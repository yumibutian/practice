package jh.com.doto;

import java.util.Map;

public record BankDetailView(Long id, String name, Integer questionCount,
                             Map<String, Long> countByType,
                             Integer practicedCount, String lastMode,
                             Integer lastScore, ResumeView resume,
                             ExamConfig examConfig) {}
