package jh.com.parse.extract;


import jh.com.parse.model.Option;
import jh.com.parse.model.QuestionType;

import java.util.List;

/**
 * 判断题型
 */
public class TypeInferrer {

    private static final List<String> BOOLEAN_WORDS = List.of(
            "对", "错", "正确", "错误", "是", "否", "true", "false", "√", "×", "t", "f", "y", "n");

    public QuestionType infer(List<Option> options, List<String> answers, String stem) {
        boolean hasOptions = options != null && options.size() >= 2;

        if (hasOptions) {
            if (areBooleanOptions(options)) return QuestionType.JUDGE;
            if (answers != null && answers.size() == 1 && isBooleanAnswer(answers.get(0))) {
                return QuestionType.JUDGE;
            }
            if (answers != null && answers.size() > 1) return QuestionType.MULTIPLE;
            return QuestionType.SINGLE;
        }
        if (answers != null && answers.size() == 1 && isBooleanAnswer(answers.get(0))) {
            return QuestionType.JUDGE;
        }
        if (stem != null && (stem.contains("____")
                || stem.contains("（  ）") || stem.contains("(  )"))) {
            return QuestionType.FILL;
        }
        return QuestionType.ESSAY;
    }

    /**
     * 段标题的题型提示优先于推断结果。冲突时以段标题为准（由打分器标 type_conflict）。
     */
    public QuestionType inferWithHint(List<Option> options, List<String> answers,
                                      String stem, QuestionType hint) {
        QuestionType inferred = infer(options, answers, stem);
        return hint != null ? hint : inferred;
    }

    public boolean areBooleanOptions(List<Option> options) {
        if (options.size() != 2) return false;
        return options.stream().allMatch(o -> {
            String t = o.text() == null ? "" : o.text().strip().toLowerCase();
            return BOOLEAN_WORDS.contains(t);
        });
    }

    public boolean isBooleanAnswer(String answer) {
        if (answer == null) return false;
        String a = answer.strip().toLowerCase();
        return "true".equals(a) || "false".equals(a)
                || BOOLEAN_WORDS.contains(a);
    }
}
