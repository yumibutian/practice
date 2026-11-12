package jh.com.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jh.com.common.BizException;
import jh.com.common.ErrorCode;
import jh.com.entity.*;
import jh.com.mapper.FileMapper;
import jh.com.mapper.ParseTaskMapper;
import jh.com.mapper.QuestionBankMapper;
import jh.com.mapper.QuestionMapper;
import jh.com.parse.model.NormalizedQuestion;
import jh.com.parse.model.QuestionType;
import jh.com.parse.task.ParseTaskStatus;
import jh.com.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 解析结果入库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParseCommitService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int PREVIEW_MAX_SIZE = 200;
    private final ParseTaskService taskService;
    private final FileStorage fileStorage;
    private final QuestionBankMapper bankMapper;
    private final QuestionMapper questionMapper;
    private final FileMapper fileMapper;
    private final ParseTaskMapper taskMapper;

    static void applyOverrides(List<NormalizedQuestion> questions, List<QuestionOverride> overrides) {
        Map<Integer, QuestionOverride> byIdx = new HashMap<>();
        for (QuestionOverride o : overrides) byIdx.put(o.idx(), o);

        for (NormalizedQuestion q : questions) {
            QuestionOverride o = byIdx.get(q.getIndex());
            if (o == null) continue;
            if (o.type() != null && !o.type().isBlank()) {
                q.setType(QuestionType.fromCode(o.type()));
            }
            if (o.stem() != null) q.setStem(o.stem());
            if (o.answers() != null && !o.answers().isEmpty()) q.setAnswers(o.answers());
            if (o.analysis() != null) q.setAnalysis(o.analysis());
        }
    }

    /**
     * 题干
     */
    public static String contentHash(Question q) {
        String raw = (q.getStem() == null ? "" : q.getStem()) + "|"
                + (q.getOptionsJson() == null ? "" : q.getOptionsJson());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public static List<Map<String, String>> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) return List.of();
        try {
            return MAPPER.readValue(optionsJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    @Transactional
    public CommitResult commit(Long userId, Long taskId, String bankName, Integer visibility,
                               List<Integer> excludedIdx, List<QuestionOverride> overrides) {
        var task = taskService.requireOwned(userId, taskId);

        // 幂等
        if (task.getCommitResultJson() != null && !task.getCommitResultJson().isBlank()) {
            return readCommitResult(task.getCommitResultJson());
        }

        if (task.getStatus() == null
                || task.getStatus() != ParseTaskStatus.SUCCESS.getCode()) {
            throw new BizException(ErrorCode.TASK_NOT_COMMITTABLE, "当前任务状态不可入库");
        }

        List<NormalizedQuestion> questions = loadFullIr(task.getResultKey());

        // 应用单题修正
        if (overrides != null && !overrides.isEmpty()) {
            applyOverrides(questions, overrides);
        }

        // 排除被用户剔除的题
        Set<Integer> excluded = excludedIdx == null ? Set.of() : new HashSet<>(excludedIdx);

        QuestionBank bank = new QuestionBank();
        bank.setTenantId(0L);
        bank.setUserId(userId);
        bank.setName(bankName == null || bankName.isBlank()
                ? "未命名题库" : bankName.strip());
        bank.setSourceFileId(task.getFileId());
        bank.setVisibility(visibility == null ? 0 : visibility);
        bank.setQuestionCount(0);
        bank.setStatus(1);
        bank.setPinned(0);
        bankMapper.insert(bank);

        int seq = 0;
        int skipped = 0;
        Set<String> seenHashes = new HashSet<>();

        for (NormalizedQuestion q : questions) {
            if (excluded.contains(q.getIndex())) continue;

            Question entity = toEntity(bank.getId(), q);
            entity.setSeq(++seq);
            String hash = contentHash(entity);
            entity.setContentHash(hash);

            // 同一次导入内先去重
            if (!seenHashes.add(hash)) {
                skipped++;
                seq--;
                continue;
            }

            boolean exists = questionMapper.exists(Wrappers.<Question>lambdaQuery()
                    .eq(Question::getBankId, bank.getId())
                    .eq(Question::getContentHash, hash));
            if (exists) {
                skipped++;
                seq--;
                continue;
            }

            questionMapper.insert(entity);
        }

        QuestionBank update = new QuestionBank();
        update.setId(bank.getId());
        update.setQuestionCount(seq);
        bankMapper.updateById(update);

        // 标记原文件已解析
        File fileUpdate = new File();
        fileUpdate.setId(task.getFileId());
        fileUpdate.setStatus(2);
        fileMapper.updateById(fileUpdate);

        // 入库后立即删除 IR，避免存储浪费
        try {
            fileStorage.delete(task.getResultKey());
        } catch (Exception e) {
            log.warn("IR 清理失败，不影响入库结果 key={}", task.getResultKey(), e);
        }

        int materialGroups = (int) questions.stream()
                .filter(q -> q.getGroupId() != null).count();
        CommitResult result = new CommitResult(bank.getId(), seq, skipped, materialGroups);

        try {
            ParseTask taskUpdate = new ParseTask();
            taskUpdate.setId(taskId);
            taskUpdate.setCommitResultJson(MAPPER.writeValueAsString(result));
            taskMapper.updateById(taskUpdate);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "入库结果记录失败", e);
        }

        return result;
    }

    /**
     * 预览分页
     */
    public PreviewPage previewPage(Long userId, Long taskId, int page, int size) {
        ParseTask task = taskService.requireOwned(userId, taskId);
        if (task.getCommitResultJson() != null && !task.getCommitResultJson().isBlank()) {
            throw new BizException(ErrorCode.CONFLICT, "该任务已入库，预览结果不再可用");
        }
        if (task.getStatus() == null
                || task.getStatus() != ParseTaskStatus.SUCCESS.getCode()) {
            throw new BizException(ErrorCode.TASK_NOT_COMMITTABLE, "当前任务状态不可预览");
        }

        int safeSize = Math.min(Math.max(size, 1), PREVIEW_MAX_SIZE);
        int safePage = Math.max(page, 0);
        List<NormalizedQuestion> questions = loadFullIr(task.getResultKey());
        int total = questions.size();
        long offset = (long) safePage * safeSize;
        int from = offset >= total ? total : (int) offset;
        int to = Math.min(from + safeSize, total);
        return new PreviewPage(new ArrayList<>(questions.subList(from, to)), safePage, safeSize, total);
    }

    private CommitResult readCommitResult(String json) {
        try {
            return MAPPER.readValue(json, CommitResult.class);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "入库结果读取失败", e);
        }
    }

    private List<NormalizedQuestion> loadFullIr(String resultKey) {
        try (InputStream in = fileStorage.read(resultKey)) {
            FullIr ir = MAPPER.readValue(in, FullIr.class);
            return new ArrayList<>(ir.questions());
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARSE_FAILED, "解析结果读取失败，请重新解析", e);
        }
    }

    private Question toEntity(Long bankId, NormalizedQuestion q) {
        Question e = new Question();
        e.setTenantId(0L);
        e.setBankId(bankId);
        e.setType(q.getType().code());
        e.setStem(q.getStem());
        e.setStemFormat(q.getStemFormat());
        try {
            e.setOptionsJson(MAPPER.writeValueAsString(q.getOptions()));
            e.setAnswerJson(MAPPER.writeValueAsString(q.getAnswers()));
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARSE_FAILED, "题目序列化失败", ex);
        }
        e.setAnalysis(q.getAnalysis());
        e.setSourceSeq(q.getSourceSeq());
        return e;
    }

    public record CommitResult(Long bankId, int questionCount,
                               int skippedDuplicateCount, int materialGroupCount) {
    }

    // 预览分页的一页。items 为该片题目，total 为全量题数
    public record PreviewPage(List<NormalizedQuestion> items, int page, int size, int total) {
    }
}
