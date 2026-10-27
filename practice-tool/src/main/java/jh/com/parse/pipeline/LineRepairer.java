package jh.com.parse.pipeline;

import jh.com.parse.model.ParseContext;
import jh.com.parse.model.TextDoc;
import jh.com.parse.model.TextLine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 修复断行情况
 */
public class LineRepairer implements NormalizeStage {

    private static final Pattern SENTENCE_END = Pattern.compile("[。？！；：.?!;:]\\s*$");
    /**
     * 选项行标记
     */
    private static final Pattern OPTION_MARKER = Pattern.compile(
            "^\\s*[\\(\\[（]?\\s*[A-Ha-h]\\s*[\\)\\]）]?\\s*[.、．,，:：]?"
                    + "(?:\\s+|(?<=[.、．,，:：\\)\\]）]))\\S");
    private static final Pattern NUMBER_ANCHOR = Pattern.compile(
            "^\\s{0,8}(?:第\\s*)?[\\(\\[]?(\\d{1,3})[\\)\\]]?\\s*[.、．,，:：]?\\s*\\S");
    private static final Pattern SECTION_ANCHOR = Pattern.compile(
            "^\\s*(?:第\\s*)?[一二三四五六七八九十]+\\s*[、.．,，]\\s*.{0,50}$");

    private static final int CJK_SHORT_LINE = 12;
    private static final int EN_SHORT_LINE = 25;

    @Override
    public String name() {
        return "STEP3_LINE_REPAIR";
    }

    @Override
    public TextDoc apply(TextDoc doc, ParseContext ctx) {
        List<TextLine> src = doc.lines();
        List<TextLine> out = new ArrayList<>(src.size());

        int i = 0;
        while (i < src.size()) {
            int j = i;
            while (j + 1 < src.size() && shouldMerge(src.get(j), src.get(j + 1))) {
                j++;
            }
            if (j == i) {
                out.add(src.get(i));
                i++;
            } else {
                out.add(merge(src, i, j));
                i = j + 1;
            }
        }
        return TextDoc.of(doc.raw(), out);
    }

    private boolean shouldMerge(TextLine prev, TextLine next) {
        String p = prev.trimmed();
        String n = next.trimmed();
        if (p.isEmpty() || n.isEmpty()) return false;
        if (SENTENCE_END.matcher(p).find()) return false;
        if (OPTION_MARKER.matcher(n).find()) return false;
        if (NUMBER_ANCHOR.matcher(n).find()) return false;
        if (SECTION_ANCHOR.matcher(n).matches()) return false;

        String pEff = p.replaceFirst(
                "^\\s{0,8}(?:第\\s*)?[\\(\\[]?\\d{1,3}[\\)\\]]?\\s*[.、．,，:：]?\\s*", "");
        int len = (pEff.isEmpty() ? p : pEff).length();
        boolean cjk = isCjk(p.charAt(p.length() - 1));
        int shortThreshold = cjk ? CJK_SHORT_LINE : EN_SHORT_LINE;
        return len <= shortThreshold;
    }

    private TextLine merge(List<TextLine> src, int from, int to) {
        TextLine first = src.get(from);
        TextLine last = src.get(to);
        StringBuilder sb = new StringBuilder(first.text());

        for (int k = from + 1; k <= to; k++) {
            String prevText = sb.toString();
            String nextText = src.get(k).text().strip();
            if (prevText.endsWith("-") && prevIsAsciiLetter(prevText)) {
                sb.setLength(sb.length() - 1);
            } else if (!prevText.isEmpty() && !isCjk(prevText.charAt(prevText.length() - 1))
                    && !nextText.isEmpty() && !isCjk(nextText.charAt(0))) {
                sb.append(' ');
            }
            sb.append(nextText);
        }

        int span = (last.sourceOffset() + last.sourceLength()) - first.sourceOffset();
        return new TextLine(sb.toString(), first.indent(), first.sourceOffset(), span);
    }

    private boolean isCjk(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    private boolean prevIsAsciiLetter(String s) {
        if (s.length() < 2) return false;
        char c = s.charAt(s.length() - 2);
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
}
