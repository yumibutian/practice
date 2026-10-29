package jh.com.parse.score;

import jh.com.parse.model.NormalizedQuestion;
import jh.com.parse.model.QuestionType;

public class ConfidenceScorer {

    private static final double FLOOR = 0.1;
    private static final int STEM_MIN = 5;
    private static final int STEM_MAX = 500;

    public double score(NormalizedQuestion q) {
        double c = 1.0;
        if (q.getSourceSeq() == null) c -= 0.30;
        if (q.getAnswers() == null || q.getAnswers().isEmpty()) c -= 0.25;

        boolean choice = q.getType().isChoice() || q.getType() == QuestionType.JUDGE;
        if (choice && (q.getOptions() == null || q.getOptions().size() < 2)) c -= 0.15;
        if (isTypeAmbiguous(q)) c -= 0.15;

        int len = q.getStem() == null ? 0 : q.getStem().length();
        if (len < STEM_MIN || len > STEM_MAX) c -= 0.15;

        if (hasOrphanTokens(q)) c -= 0.10;

        return Math.max(FLOOR, c);
    }

    /**
     * 处理歧义题型
     */
    private boolean isTypeAmbiguous(NormalizedQuestion q) {
        boolean hasOptions = q.getOptions() != null && q.getOptions().size() >= 2;
        boolean hasAnswers = q.getAnswers() != null && !q.getAnswers().isEmpty();
        return !hasOptions && !hasAnswers;
    }

    private boolean hasOrphanTokens(NormalizedQuestion q) {
        String stem = q.getStem();
        if (stem == null) return false;
        for (String line : stem.split("\n")) {
            String t = line.strip();
            if (t.matches("^[A-Ha-h][.、．]?$") || t.matches("^\\d{1,3}[.、．]?$")) {
                return true;
            }
        }
        return false;
    }
}
