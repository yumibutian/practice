package jh.com.doto;

import java.util.List;

public record QuestionView(
    Long questionId,
    Integer seq,
    String type,
    String stem,
    String stemFormat,
    Object options,
    List<String> answers,
    String analysis,
    Integer score,
    Long groupId
) {}
