package jh.com.parse.pipeline;

import jh.com.parse.model.ParseContext;
import jh.com.parse.model.TextDoc;
import jh.com.parse.model.TextLine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 重复行判断
 */
public class LayoutStripper implements NormalizeStage {

    /**
     * 纯页码行：第 3 页 / -3- / 3 / 3/20 / 3 of 20
     */
    private static final Pattern PAGE_NUM = Pattern.compile(
            "^\\s*[-—－]?\\s*(?:第\\s*)?\\d{1,4}\\s*(?:页|/\\s*\\d{1,4}|\\s*of\\s*\\d{1,4})?\\s*[-—－]?\\s*$");

    /**
     * 重复判定豁免行
     */
    private static final Pattern ANSWER_LINE = Pattern.compile(
            "^\\s*[\\(\\[【]?\\s*(?:正确|参考)?答\\s*案\\s*[\\)\\]】]?\\s*[:：]?.{0,24}$");
    private static final Pattern OPTION_START = Pattern.compile(
            "^\\s*[\\(\\[（]?\\s*[A-Ha-h]\\s*[\\)\\]）]?\\s*[.、．,，:：]");

    /**
     * 重复行判定：出现次数达到该值且长度受限
     */
    private static final int REPEAT_THRESHOLD = 3;
    private static final int REPEAT_MAX_LEN = 40;

    @Override
    public String name() {
        return "STEP2_LAYOUT";
    }

    @Override
    public TextDoc apply(TextDoc doc, ParseContext ctx) {
        List<TextLine> lines = doc.lines();

        Map<String, Integer> freq = new HashMap<>();
        for (TextLine line : lines) {
            String t = line.trimmed();
            if (!t.isEmpty() && t.length() <= REPEAT_MAX_LEN) {
                freq.merge(t, 1, Integer::sum);
            }
        }
        List<TextLine> kept = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            TextLine line = lines.get(i);
            String t = line.trimmed();
            if (t.isEmpty()) {
                kept.add(line);
                continue;
            }

            if (PAGE_NUM.matcher(t).matches()) {
                if (isProtectedNumberLine(lines, i)) {
                    kept.add(line);
                }
                continue;
            }
            if (!ANSWER_LINE.matcher(t).matches() && !OPTION_START.matcher(t).lookingAt()
                    && freq.getOrDefault(t, 0) >= REPEAT_THRESHOLD) {
                continue;
            }
            kept.add(line);
        }
        return TextDoc.of(doc.raw(), kept);
    }

    /**
     * 选项列表里常出现纯数字选项（如 "2" 表示值为 2 的选项）
     */
    private boolean isProtectedNumberLine(List<TextLine> lines, int idx) {
        for (int i = idx - 1; i >= 0 && i >= idx - 3; i--) {
            String prev = lines.get(i).trimmed();
            if (prev.isEmpty()) continue;
            if (prev.matches("^[A-Ha-h]\\s*[.、.\\s].*")) return true;
            if (prev.matches("^[\\(\\[]\\s*[A-Ha-h]\\s*[\\)\\]]\\s*.*")) return true;
            return false;
        }
        return false;
    }
}
