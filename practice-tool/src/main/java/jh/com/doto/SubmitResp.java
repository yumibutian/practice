package jh.com.doto;

import java.util.List;

public record SubmitResp(
    Integer correct,
    Integer total,
    Integer score,
    Integer fullScore,
    List<Detail> details
) {
    public record Detail(Long questionId, boolean isCorrect, List<String> correctAnswer,
                         String analysis, Integer scoreGot) {}
}
