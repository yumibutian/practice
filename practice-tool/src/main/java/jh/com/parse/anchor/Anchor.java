package jh.com.parse.anchor;

/**
 * 候选题号锚点。
 *
 * @param number    题号数值
 * @param offset    在 TextDoc.lines 中的行下标（段内相对）
 * @param lineStart 是否位于行首（允许最多 8 个空格缩进）
 * @param raw       原始文本片段，便于调试与选项行排除
 */
public record Anchor(int number, int offset, boolean lineStart, String raw) {
}
