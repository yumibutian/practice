package jh.com.doto;

import java.util.List;

/**
 * 练习/考试报告
 */
public record ReportResp(
    Long recordId,
    String mode,
    Integer score,
    Integer fullScore,
    Integer scoreRate,
    Integer correct,
    Integer total,
    Integer correctRate,
    Long timeUsedSec,
    List<TypeScore> byType,
    List<WrongItem> wrongItems
) {
    public record TypeScore(String type, Integer correctCount, Integer totalCount,
                            Integer score, Integer fullScore) {}

    public record WrongItem(Long questionId, Integer seq, String stem, String type,
                            List<String> userAnswer, List<String> correctAnswer,
                            String analysis) {}
}
