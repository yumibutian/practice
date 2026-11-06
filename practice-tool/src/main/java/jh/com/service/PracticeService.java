package jh.com.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jh.com.common.BizException;
import jh.com.common.ErrorCode;
import jh.com.doto.*;
import jh.com.entity.PracticeDetail;
import jh.com.entity.PracticeRecord;
import jh.com.entity.Question;
import jh.com.mapper.PracticeDetailMapper;
import jh.com.mapper.PracticeRecordMapper;
import jh.com.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 练习与考试编排
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PracticeService {

    public static final String MODE_PRACTICE = "practice";
    public static final String MODE_EXAM = "exam";

    /**
     * 全量范围
     */
    public static final String SCOPE_ALL = ExamConfig.Scope.ALL.name();

    //错题范围
    public static final String SCOPE_WRONG = ExamConfig.Scope.WRONG.name();
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_SUBMITTED = 2;
    /**
     * 已中断
     */
    public static final int STATUS_INTERRUPTED = 3;
    /**
     * 已作废
     */
    public static final int STATUS_ABANDONED = 9;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final QuestionMapper questionMapper;

    private final PracticeRecordMapper recordMapper;

    private final PracticeDetailMapper detailMapper;

    private final ScoringService scoringService;

    private final BankService bankService;

    private final WrongBookService wrongBookService;

    @Transactional
    public StartResp start(Long userId, StartReq req) {
        var bank = bankService.requireOwned(userId, req.bankId());

        List<Question> all = questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                .eq(Question::getBankId, req.bankId())
                .orderByAsc(Question::getSeq));
        if (all.isEmpty()) {
            throw new BizException(ErrorCode.BANK_EMPTY, "该题库还没有题目");
        }

        boolean exam = MODE_EXAM.equalsIgnoreCase(req.mode());

        ExamConfig config = exam
                ? (req.config() != null ? req.config() : bankService.readExamConfig(bank))
                : practiceConfig(req.config());
        if (exam) {
            config.validate();
        }

        // 断点续练
        if (!exam && req.recordId() != null) {
            PracticeRecord existing = recordMapper.selectById(req.recordId());
            if (resumable(existing, userId, req.bankId())) {
                PracticeRecord revive = new PracticeRecord();
                revive.setId(existing.getId());
                revive.setTotal(all.size());
                recordMapper.updateById(revive);
                log.info("续练复用记录 recordId={} bankId={}", existing.getId(), req.bankId());
                return resp(existing.getId(), MODE_PRACTICE, all, null, null, null, null,
                        readProgressNode(existing.getProgressJson()));
            }
            log.info("续练请求被拒，落回新建 recordId={} bankId={}", req.recordId(), req.bankId());
        }

        List<Question> picked = pick(all, config, userId, req.bankId());

        String mode = exam ? MODE_EXAM : MODE_PRACTICE;
        String scope = config.getScope().name();
        abandonRunning(userId, req.bankId(), mode, scope);

        PracticeRecord record = new PracticeRecord();
        record.setTenantId(0L);
        record.setUserId(userId);
        record.setBankId(req.bankId());
        record.setMode(mode);
        record.setScope(scope);
        record.setTotal(picked.size());
        record.setCorrect(0);
        record.setDurationMs(0L);
        record.setStatus(STATUS_RUNNING);
        record.setStartedAt(LocalDateTime.now());
        if (exam) {
            record.setConfigJson(writeJson(config));
            if (!config.isUnlimited()) {
                int limit = config.getDurationMinutes() * 60;
                record.setDurationLimitSec(limit);
                record.setDeadlineAt(record.getStartedAt().plusSeconds(limit));
            }
            record.setFullScore(config.fullScore(scoringService.countByType(picked)));
        }
        recordMapper.insert(record);

        return resp(record.getId(), record.getMode(), picked, exam ? config : null,
                record.getFullScore(), record.getDurationLimitSec(), record.getDeadlineAt(), null);
    }

    private StartResp resp(Long recordId, String mode, List<Question> questions, ExamConfig config,
                           Integer fullScore, Integer durationLimitSec, LocalDateTime deadlineAt,
                           JsonNode progress) {
        List<QuestionView> views = questions.stream()
                .map(q -> toView(q, mode, config == null ? null : config.scoreOf(q.getType())))
                .toList();
        return new StartResp(recordId, mode, questions.size(), fullScore, durationLimitSec,
                deadlineAt, LocalDateTime.now(), views, progress);
    }

    /**
     * 判断这条记录能否被复用续练
     */
    private boolean resumable(PracticeRecord r, Long userId, Long bankId) {
        if (r == null) return false;
        if (!userId.equals(r.getUserId())) return false;
        if (!bankId.equals(r.getBankId())) return false;
        if (!MODE_PRACTICE.equals(r.getMode())) return false;
        if (!SCOPE_ALL.equals(r.getScope())) return false;
        Integer st = r.getStatus();
        return Integer.valueOf(STATUS_RUNNING).equals(st)
                || Integer.valueOf(STATUS_INTERRUPTED).equals(st);
    }

    /**
     * 用户离开练习页时把进度存下来
     */
    @Transactional
    public InterruptResp interrupt(Long userId, Long recordId, InterruptReq req) {
        PracticeRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(ErrorCode.PRACTICE_NOT_FOUND, "练习记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该记录");
        }
        if (!MODE_PRACTICE.equals(record.getMode())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅练习模式支持断点续练");
        }
        if (Integer.valueOf(STATUS_SUBMITTED).equals(record.getStatus())) {
            return new InterruptResp(true, 0);
        }

        JsonNode progress = req == null ? null : req.progress();
        int answered = countAnswered(progress);

        PracticeRecord update = new PracticeRecord();
        update.setId(recordId);
        if (answered > 0) {
            update.setStatus(STATUS_INTERRUPTED);
            update.setProgressJson(progress == null ? null : progress.toString());
        } else {
            update.setStatus(STATUS_ABANDONED);
        }
        recordMapper.updateById(update);

        if (answered > 0) {
            persistAnswered(userId, record, progress);
        }
        return new InterruptResp(answered > 0, answered);
    }

    /**
     * 把一次进度里的作答增量落库：写明细（每 record+question 一行）+ 写错题本。
     */
    private int persistAnswered(Long userId, PracticeRecord record, JsonNode progress) {
        JsonNode answers = progress == null ? null : progress.get("answers");
        if (answers == null || !answers.isObject() || answers.isEmpty()) return 0;

        Set<Long> already = detailMapper.selectList(Wrappers.<PracticeDetail>lambdaQuery()
                        .eq(PracticeDetail::getRecordId, record.getId()))
                .stream().map(PracticeDetail::getQuestionId).collect(Collectors.toSet());

        Map<Long, Question> questionMap = new HashMap<>();
        for (Question q : questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                .eq(Question::getBankId, record.getBankId()))) {
            questionMap.put(q.getId(), q);
        }

        JsonNode judgedNode = progress.get("judged");
        JsonNode durationsNode = progress.get("durations");

        int written = 0;
        Iterator<Map.Entry<String, JsonNode>> it = answers.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            Long qid = parseId(e.getKey());
            if (qid == null || already.contains(qid)) continue;

            Question q = questionMap.get(qid);
            if (q == null) continue;

            List<String> userAnswer = readJsonStringList(e.getValue());
            if (userAnswer.isEmpty()) continue;

            Boolean ok = readJudged(judgedNode, e.getKey());
            if (ok == null) ok = scoringService.isCorrect(q, userAnswer);

            PracticeDetail detail = new PracticeDetail();
            detail.setRecordId(record.getId());
            detail.setQuestionId(q.getId());
            detail.setSeq(q.getSeq());
            detail.setUserAnswerJson(writeJson(userAnswer));
            detail.setIsCorrect(ok ? 1 : 0);
            detail.setScoreGot(0);
            detail.setDurationMs(readDuration(durationsNode, e.getKey()));
            detailMapper.insert(detail);
            already.add(qid);
            written++;

            wrongBookService.record(userId, record.getBankId(), q.getId(), !ok);
        }
        return written;
    }

    private Long parseId(String key) {
        try {
            return Long.valueOf(key);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> readJsonStringList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonNode n : node) {
            if (n != null && n.isTextual() && !n.asText().isBlank()) out.add(n.asText());
        }
        return out;
    }

    private Boolean readJudged(JsonNode judgedNode, String key) {
        if (judgedNode == null || !judgedNode.isObject()) return null;
        JsonNode v = judgedNode.get(key);
        return v != null && v.isBoolean() ? v.asBoolean() : null;
    }

    private int readDuration(JsonNode durationsNode, String key) {
        if (durationsNode == null || !durationsNode.isObject()) return 0;
        JsonNode v = durationsNode.get(key);
        return v != null && v.isNumber() ? v.asInt() : 0;
    }

    private int countAnswered(JsonNode progress) {
        if (progress == null) return 0;
        JsonNode answers = progress.get("answers");
        return answers != null && answers.isObject() ? answers.size() : 0;
    }

    private JsonNode readProgressNode(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            log.warn("断点进度解析失败，按无进度开局", e);
            return null;
        }
    }


    private void abandonRunning(Long userId, Long bankId, String mode, String scope) {
        PracticeRecord abandoned = new PracticeRecord();
        abandoned.setStatus(STATUS_ABANDONED);
        recordMapper.update(abandoned, Wrappers.<PracticeRecord>lambdaUpdate()
                .eq(PracticeRecord::getUserId, userId)
                .eq(PracticeRecord::getBankId, bankId)
                .eq(PracticeRecord::getMode, mode)
                .eq(PracticeRecord::getScope, scope)
                .in(PracticeRecord::getStatus, STATUS_RUNNING, STATUS_INTERRUPTED));
    }


    public QuestionView toView(Question q, String mode, Integer score) {
        boolean exam = MODE_EXAM.equalsIgnoreCase(mode);
        Object options = null;
        try {
            if (q.getOptionsJson() != null && !q.getOptionsJson().isBlank()) {
                options = MAPPER.readValue(q.getOptionsJson(), new TypeReference<List<Map<String, String>>>() {
                });
            }
        } catch (Exception e) {
            log.warn("选项反序列化失败 questionId={}", q.getId(), e);
        }
        return new QuestionView(q.getId(), q.getSeq(), q.getType(), q.getStem(),
                q.getStemFormat(), options,
                exam ? null : readAnswers(q.getAnswerJson()),
                exam ? null : q.getAnalysis(),
                exam ? score : null,
                q.getGroupId());
    }

    @Transactional
    public SubmitResp submit(Long userId, Long recordId, SubmitReq req) {
        PracticeRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(ErrorCode.PRACTICE_NOT_FOUND, "练习记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该记录");
        }
        if (Integer.valueOf(STATUS_SUBMITTED).equals(record.getStatus())) {
            throw new BizException(ErrorCode.PRACTICE_ALREADY_SUBMITTED, "本次练习已提交");
        }

        boolean exam = MODE_EXAM.equals(record.getMode());
        ExamConfig config = exam ? readConfig(record.getConfigJson()) : null;

        Map<Long, Question> questionMap = new HashMap<>();
        for (Question q : questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                .eq(Question::getBankId, record.getBankId()))) {
            questionMap.put(q.getId(), q);
        }

        Set<Long> preExisting = detailMapper.selectList(Wrappers.<PracticeDetail>lambdaQuery()
                        .eq(PracticeDetail::getRecordId, recordId))
                .stream().map(PracticeDetail::getQuestionId).collect(Collectors.toSet());

        int correct = 0;
        int score = 0;
        int fullScore = 0;
        long totalDuration = 0;
        List<SubmitResp.Detail> details = new ArrayList<>();

        for (SubmitReq.AnswerItem item : req.answers()) {
            Question q = questionMap.get(item.questionId());
            if (q == null) continue;

            boolean ok = scoringService.isCorrect(q, item.answer());
            int scoreGot = 0;
            if (exam) {
                int per = config.scoreOf(q.getType());
                fullScore += per;
                if (ok) {
                    scoreGot = per;
                    score += per;
                }
            }
            if (ok) correct++;

            int duration = item.durationMs() == null ? 0 : item.durationMs();
            totalDuration += duration;

            boolean counted = !preExisting.contains(q.getId());
            if (counted) {
                PracticeDetail detail = new PracticeDetail();
                detail.setRecordId(recordId);
                detail.setQuestionId(q.getId());
                detail.setSeq(q.getSeq());
                detail.setUserAnswerJson(writeJson(item.answer()));
                detail.setIsCorrect(ok ? 1 : 0);
                detail.setScoreGot(scoreGot);
                detail.setDurationMs(duration);
                detailMapper.insert(detail);
            }

            details.add(new SubmitResp.Detail(q.getId(), ok,
                    exam ? null : readAnswers(q.getAnswerJson()),
                    exam ? null : q.getAnalysis(),
                    exam ? scoreGot : null));

            if (!exam && counted) {
                wrongBookService.record(userId, record.getBankId(), q.getId(), !ok);
            }
        }

        if (exam) {
            Set<Long> recordedWrong = new HashSet<>(preExisting);
            for (SubmitReq.AnswerItem item : req.answers()) {
                Question q = questionMap.get(item.questionId());
                if (q == null || !recordedWrong.add(q.getId())) continue;
                wrongBookService.record(userId, record.getBankId(), q.getId(),
                        !scoringService.isCorrect(q, item.answer()));
            }
        }

        PracticeRecord update = new PracticeRecord();
        update.setId(recordId);
        update.setCorrect(correct);
        update.setDurationMs(totalDuration);
        update.setStatus(STATUS_SUBMITTED);
        update.setSubmittedAt(LocalDateTime.now());
        if (exam) {
            update.setScore(score);
            update.setFullScore(fullScore);
        }
        recordMapper.updateById(update);

        return new SubmitResp(correct, req.answers().size(),
                exam ? score : null, exam ? fullScore : null, details);
    }

    /**
     * 练习模式的配置
     */
    private ExamConfig practiceConfig(ExamConfig requested) {
        ExamConfig c = new ExamConfig();
        c.setScope(requested != null && requested.getScope() == ExamConfig.Scope.WRONG
                ? ExamConfig.Scope.WRONG
                : ExamConfig.Scope.ALL);
        return c;
    }

    private List<Question> pick(List<Question> all, ExamConfig config,
                                Long userId, Long bankId) {
        if (config.getScope() == ExamConfig.Scope.WRONG) {
            Set<Long> wrongIds = wrongBookService.wrongQuestionIds(userId, bankId);
            List<Question> wrong = all.stream()
                    .filter(q -> wrongIds.contains(q.getId())).toList();
            if (wrong.isEmpty()) {
                throw new BizException(ErrorCode.WRONG_SOURCE_EMPTY, "该题库没有错题可抽");
            }
            return wrong;
        }
        if (config.getScope() == ExamConfig.Scope.RANDOM) {
            int n = config.getQuestionCount() == null ? all.size() : config.getQuestionCount();
            return ExamPicker.pick(all, n, new Random());
        }
        return all;
    }

    private List<String> readAnswers(String answerJson) {
        try {
            return MAPPER.readValue(answerJson, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    ExamConfig readConfig(String json) {
        if (json == null || json.isBlank()) return new ExamConfig();
        try {
            return MAPPER.readValue(json, ExamConfig.class);
        } catch (Exception e) {
            return new ExamConfig();
        }
    }

    private String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "序列化失败", e);
        }
    }
}
