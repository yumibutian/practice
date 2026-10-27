package jh.com.parse.pipeline;


import jh.com.parse.model.QuestionType;
import jh.com.parse.model.Section;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 识别到大题时进行分段
 */
public class SectionSplitter {

    private static final Pattern SECTION_CN = Pattern.compile(
            "^\\s*(?:第\\s*)?[一二三四五六七八九十]+\\s*[、.．,，]\\s*.{0,50}$");
    private static final Pattern SECTION_PART = Pattern.compile(
            "^\\s*第\\s*[一二三四五六七八九十]+\\s*部分.{0,50}$");
    private static final Pattern SECTION_EN = Pattern.compile(
            "^\\s*(?:Section|PART|Part)\\s+[A-Z0-9]+\\b.{0,60}$");

    private static final Pattern SCORE_PER_Q = Pattern.compile(
            "每\\s*[题小]\\s*[（(]?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*分");

    public boolean isSectionHeader(String line) {
        String t = line.strip();
        if (t.isEmpty()) return false;
        return SECTION_CN.matcher(t).matches()
                || SECTION_PART.matcher(t).matches()
                || SECTION_EN.matcher(t).matches();
    }

    public List<Section> split(List<String> lines) {
        List<Integer> headerIdx = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (isSectionHeader(lines.get(i))) {
                headerIdx.add(i);
            }
        }

        if (headerIdx.isEmpty()) {
            return List.of(new Section(0, Math.max(0, lines.size() - 1), "", null, null));
        }

        List<Section> sections = new ArrayList<>();
        // 第一个段标题之前的内容若含题目，也作为一个无标题段
        if (headerIdx.get(0) > 0) {
            sections.add(new Section(0, headerIdx.get(0) - 1, "", null, null));
        }
        for (int k = 0; k < headerIdx.size(); k++) {
            int start = headerIdx.get(k);
            int end = (k + 1 < headerIdx.size()) ? headerIdx.get(k + 1) - 1 : lines.size() - 1;
            String title = lines.get(start).strip();
            sections.add(new Section(start, end, title,
                    inferTypeHint(title), inferScorePerQuestion(title)));
        }
        return sections;
    }

    public QuestionType inferTypeHint(String title) {
        if (title == null || title.isEmpty()) return null;
        if (title.contains("多选") || title.contains("多项")) return QuestionType.MULTIPLE;
        if (title.contains("单选") || title.contains("单项")) return QuestionType.SINGLE;
        if (title.contains("判断")) return QuestionType.JUDGE;
        if (title.contains("填空")) return QuestionType.FILL;
        if (title.contains("简答") || title.contains("论述") || title.contains("问答")) {
            return QuestionType.ESSAY;
        }
        return null;
    }

    public Double inferScorePerQuestion(String title) {
        if (title == null || title.isEmpty()) return null;
        Matcher m = SCORE_PER_Q.matcher(title);
        return m.find() ? Double.parseDouble(m.group(1)) : null;
    }
}
