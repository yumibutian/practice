package jh.com.doto;

import java.time.LocalDateTime;
import java.util.List;

public record WrongItemView(Long questionId, String stem, String type, Long bankId,
                            String bankName, Integer wrongCount, Integer rightStreak,
                            boolean mastered, LocalDateTime lastWrongAt,
                            List<String> correctAnswer) {}
