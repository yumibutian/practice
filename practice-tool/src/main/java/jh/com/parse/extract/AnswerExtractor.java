package jh.com.parse.extract;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析答案
 */
public class AnswerExtractor {

    // 答案结构
    private static final String ANSWER_MARK =
            "[\\(\\[【（]?\\s*(?:正确|参考)?答\\s*案\\s*";

    // 标记后的闭合括号
    private static final String ANSWER_CLOSE = "[\\)\\]】）]";

    // 选项字母
    private static final String ANSWER_LETTERS = "[A-Ha-hTFYN√×对错是否]{1,8}";

    // 分隔符
    private static final Pattern ANSWER_INLINE_FREE = Pattern.compile(
            ANSWER_MARK
                    + "(?:" + ANSWER_CLOSE + "\\s*[:：]?"
                    + "|\\s*[:：]"
                    + "|\\s*(?:是|为))"
                    + "\\s*([^\\n]{1,60})");

    // 兜底措施
    private static final Pattern ANSWER_INLINE_LETTERS = Pattern.compile(
            ANSWER_MARK + ANSWER_CLOSE + "?\\s*[:：]?\\s*(" + ANSWER_LETTERS + ")");

    // 答案文本的截断
    private static final Pattern ANSWER_CUT = Pattern.compile(
            "[。！？].*$|(?:解析|分析|详解|解释)\\s*[:：].*$", Pattern.DOTALL);

    // 开头的选项字母
    private static final Pattern LEADING_ANSWER_TOKEN = Pattern.compile(
            "^[A-Ha-hTFYN√×对错是否]+");

    // 结束
    private static final Pattern SECTION_TITLE_TAIL = Pattern.compile(
            "^(?:[（(][一二三四五六七八九十]"
                    + "|[一二三四五六七八九十](?=$|[\\s，、。；：,.;:!！?？]))");

    // 解析标记
    private static final Pattern ANALYSIS_MARK = Pattern.compile(
            "[\\(\\[【（]\\s*(?:答案)?(?:解析|分析|详解|解释)\\s*[\\)\\]】）]\\s*[:：]?"
                    + "|(?:答案)?(?:解析|分析|详解|解释)\\s*[:：]");

    // 答案区域标记
    private static final String ZONE_MARK =
            "(?:[\\(\\[【（]\\s*(?:正确|参考)?(?:答\\s*案|解析|分析|详解|解释)\\s*[\\)\\]】）]\\s*[:：]?"
                    + "|(?:正确|参考)?答\\s*案\\s*[:：]"
                    + "|(?:解析|分析|详解|解释)\\s*[:：])";

    // 答案第一行
    private static final Pattern ZONE_AT_LINE_HEAD =
            Pattern.compile("^[ \\t　]*" + ZONE_MARK);

    private static final Pattern ZONE_AFTER_SENTENCE_END = Pattern.compile("(?<=[。！？；;])[ \\t　]*" + ZONE_MARK);

    private static final List<String> TRUE_WORDS = List.of("对", "正确", "是", "√", "T", "TRUE", "Y");

    private static final List<String> FALSE_WORDS = List.of("错", "错误", "否", "×", "F", "FALSE", "N");

    /**
     * 把答案里去掉，返回纯题干
     */
    public static String cutStemZone(String stem) {
        if (stem == null || stem.isEmpty()) return stem;
        String[] lines = stem.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int cut = -1;
            if (i > 0) {
                Matcher head = ZONE_AT_LINE_HEAD.matcher(line);
                if (head.find()) cut = head.start();
            }
            if (cut < 0) {
                Matcher after = ZONE_AFTER_SENTENCE_END.matcher(line);
                if (after.find()) cut = after.start();
            }
            if (cut >= 0) {
                String[] kept = Arrays.copyOf(lines, i + 1);
                kept[i] = line.substring(0, cut);
                return String.join("\n", kept).strip();
            }
        }
        return stem.strip();
    }

    /**
     * 获取答案内容
     */
    public static String cleanAnswer(String raw) {
        if (raw == null) return "";
        String s = ANSWER_CUT.matcher(raw).replaceFirst("");
        s = s.replaceAll("[\\s\\)\\]】）]+$", "");
        s = s.replaceAll("^[\\s，、;；]+|[\\s，、;；]+$", "");
        return collapseTrailingProse(s);
    }


    private static String collapseTrailingProse(String s) {
        Matcher t = LEADING_ANSWER_TOKEN.matcher(s);
        if (!t.find()) return s;
        int i = t.end();
        while (i < s.length() && " 　,，、;；".indexOf(s.charAt(i)) >= 0) i++;
        if (i >= s.length()) return s;
        if (!SECTION_TITLE_TAIL.matcher(s.substring(i)).find()) return s;
        return t.group();
    }

    /**
     * 获取题块的所有行
     */
    public Result extract(List<String> block, Map<Integer, String> zone) {
        String inlineAnswer = null;
        String analysis = null;

        for (int i = 0; i < block.size(); i++) {
            String line = block.get(i);
            if (inlineAnswer == null) {
                inlineAnswer = findInlineAnswer(line);
            }
            if (analysis == null) {
                Matcher a = ANALYSIS_MARK.matcher(line);
                if (a.find()) {
                    analysis = tailAfter(block, i, a.end());
                }
            }
        }

        if (inlineAnswer != null) {
            return new Result(normalizeMulti(inlineAnswer), analysis, Source.INLINE);
        }
        if (zone != null && zone.size() == 1) {
            String sole = zone.values().iterator().next();
            return new Result(normalizeMulti(sole), analysis, Source.ANSWER_ZONE);
        }
        return new Result(List.of(), analysis, Source.NONE);
    }

    /**
     * 用答案区回填。调用方负责传入该题在原文件中的题号。
     */
    public Result fromZone(Integer sourceSeq, Map<Integer, String> zone, String analysis) {
        if (sourceSeq == null || !zone.containsKey(sourceSeq)) {
            return new Result(List.of(), analysis, Source.NONE);
        }
        return new Result(normalizeMulti(zone.get(sourceSeq)), analysis, Source.ANSWER_ZONE);
    }

    /**
     * 解析正文
     */
    private String tailAfter(List<String> block, int lineIdx, int fromChar) {
        String line = block.get(lineIdx);
        String head = fromChar >= line.length() ? "" : line.substring(fromChar);
        String rest = lineIdx + 1 < block.size()
                ? join(block.subList(lineIdx + 1, block.size())) : "";
        if (head.isBlank()) return rest.strip();
        return rest.isEmpty() ? head.strip() : head.strip() + "\n" + rest;
    }

    /**
     * 行内答案
     */
    private String findInlineAnswer(String line) {
        Matcher free = ANSWER_INLINE_FREE.matcher(line);
        if (free.find()) {
            String cleaned = cleanAnswer(free.group(1));
            if (!cleaned.isEmpty()) return cleaned;
        }
        Matcher letters = ANSWER_INLINE_LETTERS.matcher(line);
        return letters.find() ? letters.group(1) : null;
    }

    /**
     * 归一化答案
     */
    public List<String> normalizeMulti(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String s = raw.strip();

        // 判断题
        String upper = s.toUpperCase();
        if (TRUE_WORDS.contains(upper)) return List.of("true");
        if (FALSE_WORDS.contains(upper)) return List.of("false");

        // 选择题
        String core = s.replaceAll("^[\\s\\(（\\[【]+", "")
                .replaceAll("[\\s\\)）\\]】.．。]+$", "");
        String compact = core.replaceAll("[,，、;；\\s]", "");
        if (!compact.isEmpty() && compact.matches("[A-Ha-h]+")) {
            return compact.chars()
                    .mapToObj(c -> String.valueOf((char) Character.toUpperCase(c)))
                    .distinct()
                    .sorted()
                    .toList();
        }

        // 其它
        return Arrays.stream(s.split("[,，、;；\\s]+"))
                .map(String::strip)
                .filter(x -> !x.isEmpty())
                .toList();
    }

    private String join(List<String> lines) {
        return String.join("\n", lines).strip();
    }

    public enum Source {INLINE, ANSWER_ZONE, NONE}

    public record Result(List<String> answers, String analysis, Source source) {
    }
}
