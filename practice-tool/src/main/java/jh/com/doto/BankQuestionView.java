package jh.com.doto;

/**
 * 题库题目列表的下发视图
 */
public record BankQuestionView(
    Long id,
    Integer seq,
    String type,
    String stem,
    String stemFormat,
    String optionsJson,
    Long groupId,
    Integer sourceSeq
) {}
