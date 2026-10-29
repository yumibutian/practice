package jh.com.parse.model;

public enum QuestionType {
    SINGLE("single"), MULTIPLE("multiple"), JUDGE("judge"),
    FILL("fill"), ESSAY("essay"), MATERIAL("material");

    private final String code;

    QuestionType(String code) {
        this.code = code;
    }

    public static QuestionType fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("题型 code 不能为 null");
        }
        for (QuestionType t : values()) {
            if (t.code.equalsIgnoreCase(code)) {
                return t;
            }
        }
        // 未知 code 必然是数据损坏或映射遗漏，静默降级成单选会把多选题变成单选题呈现
        throw new IllegalArgumentException("未知题型 code: " + code);
    }

    public String code() {
        return code;
    }

    public boolean isChoice() {
        return this == SINGLE || this == MULTIPLE;
    }
}
