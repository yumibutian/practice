package jh.com.doto;

/**
 * 可续练习的摘要
 */
public record ResumeView(Long recordId, Integer current, Integer answeredCount,
                         Integer total, Long savedAt) {}
