package jh.com.parse.parser;


import jh.com.parse.model.ParseContext;
import jh.com.parse.model.TextDoc;
import jh.com.parse.model.TextLine;

import java.util.ArrayList;
import java.util.List;

/**
 * TextDoc 构造工具
 */
public final class TextDocBuilder {

    private TextDocBuilder() {
    }

    /**
     * 从纯文本构造 TextDoc
     */
    public static TextDoc fromText(String raw, ParseContext ctx) {
        String normalized = raw.replace("\r\n", "\n").replace("\r", "\n");
        String[] parts = normalized.split("\n", -1);
        List<TextLine> lines = new ArrayList<>(parts.length);
        int offset = 0;
        for (String p : parts) {
            int spaces = p.length() - p.stripLeading().length();
            lines.add(new TextLine(p, spaces / 2, offset, p.length()));
            offset += p.length() + 1;
        }
        return TextDoc.of(normalized, lines);
    }

    /**
     * 按行读取
     */
    public static TextDoc fromLines(List<String> rawLines) {
        StringBuilder sb = new StringBuilder();
        List<TextLine> lines = new ArrayList<>(rawLines.size());
        for (int i = 0; i < rawLines.size(); i++) {
            String line = rawLines.get(i);
            int spaces = line.length() - line.stripLeading().length();
            lines.add(new TextLine(line, spaces / 2, sb.length(), line.length()));
            sb.append(line);
            if (i < rawLines.size() - 1) sb.append('\n');
        }
        return TextDoc.of(sb.toString(), lines);
    }
}
