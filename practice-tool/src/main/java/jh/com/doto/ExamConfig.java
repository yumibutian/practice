package jh.com.doto;

import jh.com.common.BizException;
import jh.com.common.ErrorCode;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 考试配置
 */
@Data
public class ExamConfig {

    public static final Map<String, Integer> DEFAULT_TYPE_SCORES = Map.of(
            "single", 2, "multiple", 4, "judge", 1, "fill", 2, "essay", 5);
    public static final int MIN_TYPE_SCORE = 1;
    public static final int MAX_TYPE_SCORE = 10;
    public static final int MAX_DURATION_MINUTES = 600;
    private Scope scope = Scope.ALL;
    private Integer questionCount;
    private Integer durationMinutes = 45;
    private Scoring scoring = Scoring.BY_TYPE;
    private Integer equalScore = 2;
    private Map<String, Integer> typeScores = new HashMap<>(DEFAULT_TYPE_SCORES);

    public boolean isUnlimited() {
        return durationMinutes == null;
    }

    public int scoreOf(String type) {
        if (scoring == Scoring.EQUAL) {
            return equalScore == null ? 2 : equalScore;
        }
        Integer v = typeScores == null ? null : typeScores.get(type);
        return v != null ? v : DEFAULT_TYPE_SCORES.getOrDefault(type, 2);
    }

    /**
     * @param countByType 题型的题数统计，如 {"single":60,"multiple":20,"judge":48}
     */
    public int fullScore(Map<String, Long> countByType) {
        int total = 0;
        for (var e : countByType.entrySet()) {
            total += (int) (long) e.getValue() * scoreOf(e.getKey());
        }
        return total;
    }

    public void validate() {
        if (durationMinutes != null && (durationMinutes <= 0 || durationMinutes > MAX_DURATION_MINUTES)) {
            throw new BizException(ErrorCode.CONFIG_INVALID,
                    "考试时长需在 1 ~ " + MAX_DURATION_MINUTES + " 分钟之间，或选择不限时");
        }
        if (scoring == Scoring.EQUAL) {
            if (equalScore == null || equalScore < MIN_TYPE_SCORE || equalScore > MAX_TYPE_SCORE) {
                throw new BizException(ErrorCode.CONFIG_INVALID,
                        "每题分值需在 " + MIN_TYPE_SCORE + " ~ " + MAX_TYPE_SCORE + " 分之间");
            }
        } else {
            if (typeScores != null) {
                for (var e : typeScores.entrySet()) {
                    Integer v = e.getValue();
                    if (v == null || v < MIN_TYPE_SCORE || v > MAX_TYPE_SCORE) {
                        throw new BizException(ErrorCode.CONFIG_INVALID,
                                "题型「" + e.getKey() + "」的分值需在 "
                                        + MIN_TYPE_SCORE + " ~ " + MAX_TYPE_SCORE + " 分之间");
                    }
                }
            }
        }
        if (scope == Scope.RANDOM
                && (questionCount == null || questionCount <= 0 || questionCount > 500)) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "随机抽题数量需在 1 ~ 500 之间");
        }
    }

    public enum Scope {ALL, RANDOM, WRONG}

    public enum Scoring {EQUAL, BY_TYPE}
}
