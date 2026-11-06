package jh.com.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jh.com.common.BizException;
import jh.com.common.ErrorCode;
import jh.com.doto.*;
import jh.com.entity.PracticeRecord;
import jh.com.entity.Question;
import jh.com.entity.QuestionBank;
import jh.com.entity.WrongBook;
import jh.com.mapper.PracticeRecordMapper;
import jh.com.mapper.QuestionBankMapper;
import jh.com.mapper.QuestionMapper;
import jh.com.mapper.WrongBookMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BankService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 单页上限：避免端侧一次拉爆整个题库
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 页码上界
     */
    private static final int MAX_PAGE = 1_000_000;

    private final QuestionBankMapper bankMapper;

    private final QuestionMapper questionMapper;

    private final PracticeRecordMapper recordMapper;

    private final WrongBookMapper wrongMapper;

    public static BankQuestionView toView(Question q) {
        return new BankQuestionView(q.getId(), q.getSeq(), q.getType(), q.getStem(),
                q.getStemFormat(), q.getOptionsJson(), q.getGroupId(), q.getSourceSeq());
    }

    static String limitClause(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.min(Math.max(page, 0), MAX_PAGE);
        long offset = (long) safePage * safeSize;
        return "limit " + safeSize + " offset " + offset;
    }

    public List<BankView> list(Long userId) {
        List<QuestionBank> banks = bankMapper.selectList(Wrappers.<QuestionBank>lambdaQuery()
                .eq(QuestionBank::getUserId, userId)
                .eq(QuestionBank::getStatus, 1)
                .orderByDesc(QuestionBank::getPinned)
                .orderByDesc(QuestionBank::getCreatedAt));

        return banks.stream().map(b -> {
            PracticeRecord last = lastSubmitted(userId, b.getId());
            long practiced = recordMapper.selectCount(Wrappers.<PracticeRecord>lambdaQuery()
                    .eq(PracticeRecord::getUserId, userId)
                    .eq(PracticeRecord::getBankId, b.getId())
                    .and(w -> w.isNotNull(PracticeRecord::getProgressJson)
                            .or().eq(PracticeRecord::getStatus, PracticeService.STATUS_SUBMITTED)));
            return new BankView(b.getId(), b.getName(), b.getQuestionCount(),
                    b.getPinned(),
                    last == null ? null : last.getMode(),
                    last == null ? null : last.getScore() != null ? last.getScore()
                            : (last.getTotal() == 0 ? null
                            : (int) Math.round(last.getCorrect() * 100.0 / last.getTotal())),
                    (int) practiced);
        }).toList();
    }

    public BankDetailView detail(Long userId, Long bankId) {
        QuestionBank bank = requireOwned(userId, bankId);

        List<Question> questions = questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                .eq(Question::getBankId, bankId));

        Map<String, Long> countByType = new HashMap<>();
        for (Question q : questions) {
            countByType.merge(q.getType(), 1L, Long::sum);
        }

        PracticeRecord last = lastSubmitted(userId, bankId);

        return new BankDetailView(bank.getId(), bank.getName(), bank.getQuestionCount(),
                countByType, 0,
                last == null ? null : last.getMode(),
                last == null ? null : last.getScore(),
                resumeView(resumableRecord(userId, bankId)), readExamConfig(bank));
    }

    /**
     * 分页读取题库题目
     */
    public List<BankQuestionView> questions(Long userId, Long bankId, int page, int size) {
        requireOwned(userId, bankId);
        return questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                        .eq(Question::getBankId, bankId)
                        .orderByAsc(Question::getSeq)
                        .last(limitClause(page, size)))
                .stream().map(BankService::toView).toList();
    }

    @Transactional
    public void rename(Long userId, Long bankId, String name) {
        requireOwned(userId, bankId);
        if (name == null || name.isBlank() || name.strip().length() > 64) {
            throw new BizException(ErrorCode.PARAM_INVALID, "题库名需在 1 ~ 64 字之间");
        }
        QuestionBank update = new QuestionBank();
        update.setId(bankId);
        update.setName(name.strip());
        bankMapper.updateById(update);
    }

    @Transactional
    public void pin(Long userId, Long bankId, boolean pinned) {
        requireOwned(userId, bankId);
        QuestionBank update = new QuestionBank();
        update.setId(bankId);
        update.setPinned(pinned ? 1 : 0);
        bankMapper.updateById(update);
    }

    /**
     * 逻辑删除题库
     */
    @Transactional
    public void delete(Long userId, Long bankId) {
        requireOwned(userId, bankId);
        QuestionBank update = new QuestionBank();
        update.setId(bankId);
        update.setStatus(9);
        bankMapper.updateById(update);
        wrongMapper.delete(Wrappers.<WrongBook>lambdaQuery()
                .eq(WrongBook::getUserId, userId)
                .eq(WrongBook::getBankId, bankId));
    }

    @Transactional
    public void saveExamConfig(Long userId, Long bankId, ExamConfig config) {
        requireOwned(userId, bankId);
        config.validate();
        try {
            QuestionBank update = new QuestionBank();
            update.setId(bankId);
            update.setExamConfigJson(MAPPER.writeValueAsString(config));
            bankMapper.updateById(update);
        } catch (Exception e) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "考试配置保存失败", e);
        }
    }

    public ExamConfig readExamConfig(QuestionBank bank) {
        if (bank.getExamConfigJson() == null || bank.getExamConfigJson().isBlank()) {
            return new ExamConfig();
        }
        try {
            return MAPPER.readValue(bank.getExamConfigJson(), ExamConfig.class);
        } catch (Exception e) {
            return new ExamConfig();
        }
    }

    public QuestionBank requireOwned(Long userId, Long bankId) {
        QuestionBank bank = bankMapper.selectById(bankId);
        if (bank == null || Integer.valueOf(9).equals(bank.getStatus())) {
            throw new BizException(ErrorCode.BANK_NOT_FOUND, "题库不存在");
        }
        if (!bank.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该题库");
        }
        return bank;
    }

    private PracticeRecord lastSubmitted(Long userId, Long bankId) {
        return recordMapper.selectOne(Wrappers.<PracticeRecord>lambdaQuery()
                .eq(PracticeRecord::getUserId, userId)
                .eq(PracticeRecord::getBankId, bankId)
                .eq(PracticeRecord::getScope, PracticeService.SCOPE_ALL)
                .eq(PracticeRecord::getStatus, PracticeService.STATUS_SUBMITTED)
                .orderByDesc(PracticeRecord::getId)
                .last("limit 1"));
    }


    private PracticeRecord resumableRecord(Long userId, Long bankId) {
        return recordMapper.selectOne(Wrappers.<PracticeRecord>lambdaQuery()
                .eq(PracticeRecord::getUserId, userId)
                .eq(PracticeRecord::getBankId, bankId)
                .eq(PracticeRecord::getMode, PracticeService.MODE_PRACTICE)
                .eq(PracticeRecord::getScope, PracticeService.SCOPE_ALL)
                .in(PracticeRecord::getStatus, PracticeService.STATUS_RUNNING,
                        PracticeService.STATUS_INTERRUPTED)
                .isNotNull(PracticeRecord::getProgressJson)
                .orderByDesc(PracticeRecord::getId)
                .last("limit 1"));
    }


    private ResumeView resumeView(PracticeRecord record) {
        if (record == null) return null;
        JsonNode progress;
        try {
            progress = MAPPER.readTree(record.getProgressJson());
        } catch (Exception e) {
            return null;
        }
        JsonNode answers = progress.path("answers");
        int answered = answers.isObject() ? answers.size() : 0;
        if (answered == 0) return null;
        return new ResumeView(record.getId(), progress.path("current").asInt(0), answered,
                record.getTotal(), progress.path("savedAt").asLong(0));
    }
}
