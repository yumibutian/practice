package jh.com.parse.model;

/**
 * @param offset 在原始文本中的起始偏移，用于前端高亮
 * @param length 长度
 */
public record Warning(String code, String level, String message, int offset, int length) {

    public static final String INFO = "info";
    public static final String WARN = "warn";
    public static final String ERROR = "error";

    public static Warning warn(String code, String message, int offset, int length) {
        return new Warning(code, WARN, message, offset, length);
    }

    public static Warning error(String code, String message, int offset, int length) {
        return new Warning(code, ERROR, message, offset, length);
    }
}
