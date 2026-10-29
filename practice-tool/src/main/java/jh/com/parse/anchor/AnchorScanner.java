package jh.com.parse.anchor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 扫描候选题号锚点。只做「找候选」，判真伪交给 {@link AnchorSelector}。
 */
public class AnchorScanner {

    /**
     * 行首题号：1. / 1、/ (1) / [1] / 第1题 —— 允许最多 8 个空格缩进。
     * {@code (?!\\d)} 是必须的：没有它 "2024. 说明" 会被截成 "202" 误判为题号。
     */
    private static final Pattern LINE_START_ANCHOR = Pattern.compile(
        "^\\s{0,8}(?:第\\s*)?[\\(\\[]?(\\d{1,3})[\\)\\]]?(?!\\d)\\s*[.、．,，:：]?\\s*(?=\\S)");

    /**
     * 行内任意位置的「数字 + 标记」。
     * {@code (?<!\\d)} 与 {@code (?!\\d)} 是成对的数字边界：
     * 没有它们，"2024. 说明" 会先被行首模式截成 "202"、再被行内模式从第 3 位截成 "24." 双重误判。
     */
    private static final Pattern INLINE_ANCHOR = Pattern.compile(
        "(?<!\\d)(?:第\\s*)?[\\(\\[]?(\\d{1,3})[\\)\\]]?(?!\\d)\\s*[.、．,，:：]");

    public static final int MAX_QUESTION_NUMBER = 500;

    /**
     * @param lines 待扫描的行文本
     * @param baseOffset 行下标偏移（段在文档中的起始行）
     */
    public List<Anchor> scan(List<String> lines, int baseOffset) {
        List<Anchor> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) continue;

            Matcher start = LINE_START_ANCHOR.matcher(line);
            if (start.find()) {
                int n = Integer.parseInt(start.group(1));
                if (n >= 1 && n <= MAX_QUESTION_NUMBER) {
                    result.add(new Anchor(n, baseOffset + i, true,
                        line.substring(0, Math.min(line.length(), 12))));
                }
                // 行首已命中，不再把这一行计入行内候选，避免重复
                continue;
            }

            Matcher inline = INLINE_ANCHOR.matcher(line);
            if (inline.find()) {
                int n = Integer.parseInt(inline.group(1));
                if (n >= 1 && n <= MAX_QUESTION_NUMBER) {
                    result.add(new Anchor(n, baseOffset + i, false,
                        line.substring(0, Math.min(line.length(), 12))));
                }
            }
        }
        return result;
    }
}
