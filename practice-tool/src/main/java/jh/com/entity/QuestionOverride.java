package jh.com.entity;

import java.util.List;

/**
 * 单题修正
 */
public record QuestionOverride(
    Integer idx,
    String type,
    String stem,
    List<String> answers,
    String analysis
) {}
