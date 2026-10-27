package jh.com.parse.pipeline;

import jh.com.parse.model.ParseContext;
import jh.com.parse.model.TextDoc;
import jh.com.parse.model.TextLine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 识别为公式或者代码的时候进行完整性识别
 */
public class MathCodeProtector implements NormalizeStage {

    /**
     * 顺序敏感：``` 必须在 ` 之前，$$ 必须在 $ 之前
     */
    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("```[\\s\\S]*?```"),
            Pattern.compile("\\$\\$[\\s\\S]{1,2000}?\\$\\$"),
            Pattern.compile("\\\\\\[[\\s\\S]{1,500}?\\\\\\]"),
            Pattern.compile("\\\\\\([\\s\\S]{1,500}?\\\\\\)"),
            Pattern.compile("\\$[^$\\n]{1,500}\\$"),
            Pattern.compile("`[^`\\n]{1,200}`"),
            Pattern.compile("\\{\\\\[\\s\\S]{1,500}?\\}")
    );

    /**
     * 还原后校验：文本里不应再残留任何占位符，否则说明抽取过程丢了内容。
     */
    public static void assertNoPlaceholderLost(ParseContext ctx, String text) {
        if (text == null) return;
        for (String token : ctx.placeholders().keySet()) {
            if (text.contains(token)) {
                throw new IllegalStateException("公式占位符未还原：" + token);
            }
        }
    }

    @Override
    public String name() {
        return "STEP4_MATH_CODE";
    }

    @Override
    public TextDoc apply(TextDoc doc, ParseContext ctx) {
        TextDoc merged = mergeFencedBlocks(doc);
        List<TextLine> out = new ArrayList<>(merged.lines().size());
        for (TextLine line : merged.lines()) {
            out.add(new TextLine(protect(line.text(), ctx), line.indent(),
                    line.sourceOffset(), line.sourceLength()));
        }
        return TextDoc.of(doc.raw(), out);
    }

    /**
     * 一行内自带开闭围栏的不合并，留给逐行正则处理。
     */
    private TextDoc mergeFencedBlocks(TextDoc doc) {
        List<TextLine> src = doc.lines();
        List<TextLine> out = new ArrayList<>(src.size());
        int i = 0;
        while (i < src.size()) {
            TextLine line = src.get(i);
            int openIdx = fenceMarkerCount(line.text());
            if (openIdx == 1) {
                int close = i;
                boolean found = false;
                for (int j = i + 1; j < src.size(); j++) {
                    close = j;
                    if (fenceMarkerCount(src.get(j).text()) >= 1) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    StringBuilder sb = new StringBuilder(line.text());
                    for (int k = i + 1; k <= close; k++) {
                        sb.append('\n').append(src.get(k).text());
                    }
                    TextLine last = src.get(close);
                    int span = (last.sourceOffset() + last.sourceLength()) - line.sourceOffset();
                    out.add(new TextLine(sb.toString(), line.indent(), line.sourceOffset(), span));
                    i = close + 1;
                    continue;
                }
            }
            out.add(line);
            i++;
        }
        return TextDoc.of(doc.raw(), out);
    }

    private int fenceMarkerCount(String text) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf("```", idx)) >= 0) {
            count++;
            idx += 3;
        }
        return count;
    }

    private String protect(String text, ParseContext ctx) {
        if (text == null || text.isEmpty()) return text;
        String result = text;
        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(result);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(ctx.parkPlaceholder(m.group())));
            }
            m.appendTail(sb);
            result = sb.toString();
        }
        return result;
    }
}
