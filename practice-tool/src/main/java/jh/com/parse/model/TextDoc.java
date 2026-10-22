package jh.com.parse.model;

import java.util.ArrayList;
import java.util.List;

public record TextDoc(String raw, List<TextLine> lines) {


    public TextDoc {
        raw = raw == null ? "" : raw;
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public static TextDoc of(String raw, List<TextLine> lines) {
        return new TextDoc(raw, lines);
    }


    public TextDoc replaceLines(int from, int to, List<TextLine> newLines) {
        if (from < 0 || to < 0 || from > to || to >= lines.size()) {
            throw new IllegalArgumentException(
                    "非法行区间 [" + from + ", " + to + "]，当前 " + lines.size() + " 行");
        }
        if (newLines == null) {
            throw new IllegalArgumentException("newLines 不能为空，删除区间请传空列表");
        }
        List<TextLine> copy = new ArrayList<>(lines.subList(0, from));
        copy.addAll(newLines);
        copy.addAll(lines.subList(to + 1, lines.size()));
        return new TextDoc(raw, copy);
    }

    // 数据原始切片
    public String originalSlice(TextLine line) {
        int start = Math.min(Math.max(0, line.sourceOffset()), raw.length());
        int end = Math.min(start + Math.max(0, line.sourceLength()), raw.length());
        return raw.substring(start, end);
    }
}
