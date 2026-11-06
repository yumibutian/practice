package jh.com.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jh.com.doto.ExamConfig;
import jh.com.entity.Question;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 判题与计分
 */
@Service
public class ScoringService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> TRUE_WORDS = List.of(
            "true", "对", "正确", "是", "√", "t", "y");
    private static final List<String> FALSE_WORDS = List.of(
            "false", "错", "错误", "否", "×", "f", "n");

    public boolean isCorrect(Question question, List<String> userAnswer) {
        if (userAnswer == null || userAnswer.isEmpty()) return false;
        if (userAnswer.stream().allMatch(a -> a == null || a.isBlank())) return false;

        List<String> expected = readAnswers(question.getAnswerJson());
        if (expected.isEmpty()) return false;

        return switch (question.getType()) {
            case "multiple" -> lettersEqual(expected, userAnswer);
            case "single" -> lettersEqual(expected, userAnswer);
            case "judge" -> judgeEqual(expected.get(0), userAnswer.get(0));
            case "fill" -> fillEqual(expected, userAnswer);
            default -> textEqual(expected, userAnswer);
        };
    }

    private List<String> readAnswers(String answerJson) {
        if (answerJson == null || answerJson.isBlank()) return List.of();
        try {
            return MAPPER.readValue(answerJson, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 字母题归并成集合，忽略顺序与大小写
     */
    private boolean lettersEqual(List<String> expected, List<String> actual) {
        return normaliseLetters(expected).equals(normaliseLetters(actual));
    }

    private List<String> normaliseLetters(List<String> input) {
        return input.stream()
                .filter(a -> a != null && !a.isBlank())
                .flatMap(a -> a.replaceAll("[^A-Za-z]", "").chars()
                        .mapToObj(c -> String.valueOf((char) Character.toUpperCase(c))))
                .distinct()
                .sorted()
                .toList();
    }

    private boolean judgeEqual(String expected, String actual) {
        return normaliseBoolean(expected).equals(normaliseBoolean(actual));
    }

    private String normaliseBoolean(String value) {
        if (value == null) return "";
        String v = value.strip().toLowerCase();
        if (TRUE_WORDS.contains(v)) return "true";
        if (FALSE_WORDS.contains(v)) return "false";
        return v;
    }

    /**
     * 多空填空按顺序逐空比对，全对才得分
     */
    private boolean fillEqual(List<String> expected, List<String> actual) {
        if (expected.size() != actual.size()) return false;
        for (int i = 0; i < expected.size(); i++) {
            if (!normaliseFill(expected.get(i)).equals(normaliseFill(actual.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private String normaliseFill(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : value.strip().toCharArray()) {
            if (Character.isWhitespace(c)) continue;
            char half = fullToHalf(c);
            sb.append(Character.toLowerCase(half));
        }
        return sb.toString();
    }

    private char fullToHalf(char c) {
        if (c >= 'Ａ' && c <= 'Ｚ') return (char) (c - 0xFEE0);
        if (c >= 'ａ' && c <= 'ｚ') return (char) (c - 0xFEE0);
        if (c >= '０' && c <= '９') return (char) (c - 0xFEE0);
        if (c == '　') return ' ';
        return c;
    }

    private boolean textEqual(List<String> expected, List<String> actual) {
        return fillEqual(expected, actual);
    }

    public Map<String, Long> countByType(List<Question> questions) {
        Map<String, Long> result = new HashMap<>();
        for (Question q : questions) {
            result.merge(q.getType(), 1L, Long::sum);
        }
        return result;
    }

    public int scoreOf(String type, Map<String, Integer> typeScores) {
        if (typeScores != null && typeScores.get(type) != null) {
            return typeScores.get(type);
        }
        return ExamConfig.DEFAULT_TYPE_SCORES.getOrDefault(type, 2);
    }
}
