package jh.com.parse.anchor;


import jh.com.parse.extract.AnswerExtractor;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 识别文末「答案区」并整块切出。
 * 同时解决「答案区被误当题目」与「答案与题目分离文件」两个问题——处理逻辑完全一样。
 */
public class AnswerZoneDetector {

    // 单行形态：序号 + 答案
    private static final Pattern ZONE_LINE = Pattern.compile(
            "^\\s*(\\d{1,3})(?:\\s+|[.、．,，:：])\\s*([^\\n]{1,16})\\s*$");

    // 严格形态：只认选项字母/判断题词
    private static final Pattern ZONE_LINE_STRICT = Pattern.compile(
            "^\\s*(\\d{1,3})\\s*[.、．,，:：]?\\s*([A-Ha-hTFYN√×对错是否]{1,8})\\s*$");

    //行内配对形态
    private static final Pattern PAIR_INLINE = Pattern.compile(
            "(\\d{1,3})\\s*[.、．,，:：]?\\s*([A-Ha-hTFYN]{1,8})(?![A-Za-z])");

    // 配对行的形态
    private static final Pattern PAIR_RUN_IN_GROUP = Pattern.compile(
            "^(?:[A-Ha-hTFYN]{1,8}\\s*)?"
                    + "(?:\\d{1,3}\\s*[.、．,，:：]?\\s*[A-Ha-hTFYN]{1,8}\\s*)+$");

    //答案区起始标志
    private static final Pattern ZONE_HEADER = Pattern.compile(
            "^\\s*(?:参考)?答\\s*案(?:\\s*与\\s*解\\s*析)?\\s*[:：]?\\s*$");

    /**
     * @param out 解析出的「题号 → 答案原始串」全局表（跨段题号后写覆盖先写，仅作兜底）
     * @return 答案区信息，found() 为 false 表示没有答案区。
     * answers() 之外还按「题号重置」切出 per-section 分区，
     * 调用方按 Block 所在段取分区，避免「单选第 1 题」吃到「多选第 1 题」的答案
     */
    public AnswerZone scan(List<String> lines, Map<Integer, String> out) {
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (ZONE_HEADER.matcher(lines.get(i)).matches()) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            start = findDenseZoneStart(lines);
        }
        if (start < 0) {
            return new AnswerZone(-1, out, List.of());
        }

        List<Map<Integer, String>> partitions = new java.util.ArrayList<>();
        Map<Integer, String> current = null;
        int prevSeq = -1;
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = ZONE_LINE.matcher(line);
            if (m.matches() && !PAIR_RUN_IN_GROUP.matcher(m.group(2)).matches()) {
                String answer = AnswerExtractor.cleanAnswer(m.group(2));
                if (!answer.isEmpty()) {
                    int seq = Integer.parseInt(m.group(1));
                    if (current == null || seq <= prevSeq) {
                        current = new java.util.LinkedHashMap<>();
                        partitions.add(current);
                    }
                    prevSeq = seq;
                    current.put(seq, answer);
                    out.put(seq, answer);
                    continue;
                }
            }
            Matcher p = PAIR_INLINE.matcher(line);
            while (p.find()) {
                int seq = Integer.parseInt(p.group(1));
                if (current == null || seq <= prevSeq) {
                    current = new java.util.LinkedHashMap<>();
                    partitions.add(current);
                }
                prevSeq = seq;
                current.put(seq, p.group(2));
                out.put(seq, p.group(2));
            }
        }
        return new AnswerZone(start, out, partitions);
    }

    /**
     * 无显式「答案」标题时，找一段连续 ≥3 行都符合「序号+答案」形态的区域。
     * 只认严格形态（字母/布尔词）：自由文本与题干同形，拿它做密度判定会把正文整段误判成答案区。
     */
    private int findDenseZoneStart(List<String> lines) {
        int run = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (ZONE_LINE_STRICT.matcher(lines.get(i)).matches()) {
                run++;
                if (run >= 3) return i - run + 1;
            } else {
                run = 0;
            }
        }
        return -1;
    }

    public record AnswerZone(int startLine, Map<Integer, String> answers,
                             List<Map<Integer, String>> partitions) {
        public boolean found() {
            return startLine >= 0;
        }
    }
}
