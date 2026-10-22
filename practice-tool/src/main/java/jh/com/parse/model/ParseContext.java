package jh.com.parse.model;

import lombok.Data;

import java.util.*;

@Data
public class ParseContext {

    /**
     * 占位符
     */
    private static final String PH_OPEN = "\uE000";

    private static final String PH_CLOSE = "\uE001";
    /**
     * 公式/代码占位符、 原文
     */
    private final Map<String, String> placeholders = new HashMap<>();
    /**
     * 全局答案区解析结果：题号、答案原始串
     */
    private final Map<Integer, String> answerZone = new HashMap<>();

    //按段分区的答案区
    private final List<Map<Integer, String>> answerZonePartitions = new ArrayList<>();

    /**
     * 用户手工锚点覆盖：题号、偏移
     */
    private final Map<Integer, Integer> anchorOverrides = new HashMap<>();
    private final List<Warning> globalWarnings = new ArrayList<>();
    private TextDoc doc;
    private int placeholderSeq = 0;

    public ParseContext(TextDoc doc) {
        this.doc = doc;
    }

    public TextDoc doc() {
        return doc;
    }


    public String parkPlaceholder(String original) {
        if (original != null && (original.contains(PH_OPEN) || original.contains(PH_CLOSE))) {
            // restore无法区分是报错
            throw new IllegalArgumentException("原文含占位符，无法安全解析");
        }
        String token = PH_OPEN + (placeholderSeq++) + PH_CLOSE;
        placeholders.put(token, original);
        return token;
    }

    public String restore(String text) {
        if (text == null || placeholders.isEmpty()) return text;
        String result = text;
        for (var e : placeholders.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }

    public Map<String, String> placeholders() {
        return Collections.unmodifiableMap(placeholders);
    }

    public Map<Integer, String> answerZone() {
        return answerZone;
    }

    public List<Map<Integer, String>> answerZonePartitions() {
        return answerZonePartitions;
    }

    public Map<Integer, Integer> anchorOverrides() {
        return anchorOverrides;
    }

    public List<Warning> globalWarnings() {
        return globalWarnings;
    }
}
