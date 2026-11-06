package jh.com.doto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

public record StartResp(
    Long recordId,
    String mode,
    Integer total,
    Integer fullScore,
    Integer durationLimitSec,
    LocalDateTime deadlineAt,
    LocalDateTime serverTime,
    List<QuestionView> questions,
    JsonNode progress
) {}
