package jh.com.doto;

public record BankView(Long id, String name, Integer questionCount,
                       Integer pinned, String lastMode, Integer lastScore,
                       Integer practicedCount) {}
