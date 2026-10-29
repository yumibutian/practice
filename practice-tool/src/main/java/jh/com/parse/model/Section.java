package jh.com.parse.model;

/**
 * 大题分段结果
 *
 * @param startLine        起始行下标（含）
 * @param endLine          结束行下标（含）
 * @param title            段标题原文，无标题时为空串
 * @param typeHint         段标题推断出的题型，未识别为 null
 * @param scorePerQuestion 段标题里的每题分值，未识别为 null
 */
public record Section(int startLine, int endLine, String title,
                      QuestionType typeHint, Double scorePerQuestion) {
}
