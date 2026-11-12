package jh.com.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jh.com.common.BizException;
import jh.com.common.ErrorCode;
import jh.com.doto.ExamConfig;
import jh.com.doto.ReportResp;
import jh.com.entity.PracticeDetail;
import jh.com.entity.PracticeRecord;
import jh.com.entity.Question;
import jh.com.mapper.PracticeDetailMapper;
import jh.com.mapper.PracticeRecordMapper;
import jh.com.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int STATUS_SUBMITTED = 2;

    private final PracticeRecordMapper recordMapper;

    private final PracticeDetailMapper detailMapper;

    private final QuestionMapper questionMapper;

    private final ScoringService scoringService;

    /**
     * 练习/考试报告
     */
    public ReportResp report(Long userId, Long recordId) {
        PracticeRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(ErrorCode.PRACTICE_NOT_FOUND, "练习记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该记录");
        }
        if (!Integer.valueOf(STATUS_SUBMITTED).equals(record.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "本次练习尚未完成");
        }

        List<PracticeDetail> details = detailMapper.selectList(
                Wrappers.<PracticeDetail>lambdaQuery()
                        .eq(PracticeDetail::getRecordId, recordId)
                        .orderByAsc(PracticeDetail::getSeq));

        Map<Long, Question> questionMap = new HashMap<>();
        if (!details.isEmpty()) {
            for (Question q : questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                    .eq(Question::getBankId, record.getBankId()))) {
                questionMap.put(q.getId(), q);
            }
        }

        ExamConfig config = record.getConfigJson() == null
                ? new ExamConfig() : readConfig(record.getConfigJson());

        Map<String, int[]> typeAgg = new LinkedHashMap<>();
        List<ReportResp.WrongItem> wrongItems = new ArrayList<>();

        for (PracticeDetail d : details) {
            Question q = questionMap.get(d.getQuestionId());
            if (q == null) continue;

            int[] agg = typeAgg.computeIfAbsent(q.getType(), k -> new int[3]); // [correct, total, score]
            agg[1]++;
            if (Integer.valueOf(1).equals(d.getIsCorrect())) agg[0]++;
            agg[2] += d.getScoreGot() == null ? 0 : d.getScoreGot();

            if (!Integer.valueOf(1).equals(d.getIsCorrect())) {
                wrongItems.add(new ReportResp.WrongItem(q.getId(), q.getSeq(), q.getStem(),
                        q.getType(), readAnswers(d.getUserAnswerJson()),
                        readAnswers(q.getAnswerJson()), q.getAnalysis()));
            }
        }

        List<ReportResp.TypeScore> byType = typeAgg.entrySet().stream()
                .map(e -> {
                    int[] agg = e.getValue();
                    int per = config.scoreOf(e.getKey());
                    return new ReportResp.TypeScore(e.getKey(), agg[0], agg[1],
                            agg[2], agg[1] * per);
                })
                .toList();

        ReportResp base = buildSummary(record.getScore(), record.getFullScore(),
                record.getCorrect(), record.getTotal(),
                record.getDurationMs() == null ? 0L : record.getDurationMs());

        return new ReportResp(record.getId(), record.getMode(), base.score(),
                base.fullScore(), base.scoreRate(), base.correct(), base.total(),
                base.correctRate(), base.timeUsedSec(), byType, wrongItems);
    }

    ReportResp buildSummary(Integer score, Integer fullScore, Integer correct,
                            Integer total, Long durationMs) {
        int totalQuestions = total == null ? 0 : total;
        int correctCount = correct == null ? 0 : correct;

        int correctRate = totalQuestions == 0 ? 0
                : (int) Math.round(correctCount * 100.0 / totalQuestions);
        Integer scoreRate = (score == null || fullScore == null || fullScore == 0)
                ? null : (int) Math.round(score * 100.0 / fullScore);

        return new ReportResp(null, null, score, fullScore, scoreRate,
                correctCount, totalQuestions, correctRate,
                durationMs == null ? 0L : durationMs / 1000, List.of(), List.of());
    }

    private List<String> readAnswers(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private ExamConfig readConfig(String json) {
        try {
            return MAPPER.readValue(json, ExamConfig.class);
        } catch (Exception e) {
            return new ExamConfig();
        }
    }
}
