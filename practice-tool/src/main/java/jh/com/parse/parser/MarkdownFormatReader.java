package jh.com.parse.parser;

import jh.com.common.BizException;
import jh.com.common.ErrorCode;
import jh.com.parse.model.ParseContext;
import jh.com.parse.model.TextDoc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 读取器
 */
public class MarkdownFormatReader implements FormatReader {

    private static final Pattern H2 = Pattern.compile("^\\s*#{2}\\s+(.+?)\\s*$");
    private static final Pattern H3 = Pattern.compile("^\\s*#{3,6}\\s+(\\d{1,3})\\.\\s*(.+?)\\s*$");
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*[-*+]\\s+(.+?)\\s*$");
    private static final Pattern BLOCKQUOTE = Pattern.compile("^\\s*>\\s?(.+?)\\s*$");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]*)`");
    private static final Pattern EMPHASIS = Pattern.compile("(\\*\\*|__|\\*|_)(.+?)\\1");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)");

    private static final String SECTION_NUM = "一二三四五六七八九十";

    @Override
    public Set<String> supportedExts() { return Set.of("md", "markdown"); }

    @Override
    public int priority() { return 20; }

    @Override
    public TextDoc build(InputStream in, ParseContext ctx) {
        String raw;
        try {
            raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARSE_FAILED, "Markdown 读取失败", e);
        }

        List<String> out = new ArrayList<>();
        int sectionSeq = 0;

        for (String line : raw.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1)) {
            String t = line.strip();

            Matcher h2 = H2.matcher(line);
            if (h2.matches()) {
                String title = stripInline(h2.group(1));
                sectionSeq++;
                out.add(SECTION_NUM.charAt(Math.min(sectionSeq - 1, 9)) + "、" + title);
                continue;
            }
            Matcher h3 = H3.matcher(line);
            if (h3.matches()) {
                out.add(h3.group(1) + ". " + stripInline(h3.group(2)));
                continue;
            }
            Matcher li = LIST_ITEM.matcher(line);
            if (li.matches()) {
                // 缩进两个空格
                out.add("  " + stripInline(li.group(1)));
                continue;
            }
            Matcher bq = BLOCKQUOTE.matcher(line);
            if (bq.matches()) {
                out.add(stripInline(bq.group(1)));
                continue;
            }
            if (t.isEmpty()) {
                out.add("");
                continue;
            }
            out.add(stripInline(line));
        }

        return TextDocBuilder.fromLines(out);
    }

    private String stripInline(String text) {
        String s = IMAGE.matcher(text).replaceAll("");
        s = LINK.matcher(s).replaceAll("$1");
        s = INLINE_CODE.matcher(s).replaceAll("$1");
        for (int i = 0; i < 3; i++) {
            String next = EMPHASIS.matcher(s).replaceAll("$2");
            if (next.equals(s)) break;
            s = next;
        }
        return s.strip();
    }
}
