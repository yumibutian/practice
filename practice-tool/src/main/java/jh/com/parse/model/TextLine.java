package jh.com.parse.model;

/**
 * @param text         当前文本内容（可能已被上游步骤修改）
 * @param indent       缩进层级（空格数 / 2），选项判定用
 * @param sourceOffset 在原始文本中的起始偏移
 * @param sourceLength 在原始文本中跨越的长度
 */
public record TextLine(String text, int indent, int sourceOffset, int sourceLength) {

    public boolean isBlank() {
        return text == null || text.isBlank();
    }

    public String trimmed() {
        return text == null ? "" : text.strip();
    }
}
