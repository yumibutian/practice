package jh.com.parse.extract;


import jh.com.parse.model.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 选项抽取
 */
public class OptionExtractor {

    // 选项行
    private static final Pattern OPTION_LINE = Pattern.compile(
            "^\\s*[\\(\\[（]?\\s*([A-Ha-h])\\s*[\\)\\]）]?\\s*[.、．,，:：]?"
                    + "(?:\\s+|(?<=[.、．,，:：\\)\\]）]))(\\S.*)$");

    // 同一行内多个选项
    private static final Pattern INLINE_MULTI = Pattern.compile(
            "([A-Ha-h])\\s*[.、．]\\s*([^A-H]{1,200}?)(?=\\s{2,}[A-H]\\s*[.、．]|$)");

    //同一行内用括号分割的多个选项
    private static final String PAREN_MARK = "[\\(（]\\s*[A-Ha-h]\\s*[\\)）]";
    private static final Pattern INLINE_PAREN = Pattern.compile(
            "(?<=^|[\\s。；;，、：:])[\\(（]\\s*([A-Ha-h])\\s*[\\)）]"
                    + "((?:(?!" + PAREN_MARK + ")[^\\n]){0,200}?)(?=" + PAREN_MARK + "|$)");

    //选项区终止标记
    private static final Pattern ANSWER_ZONE_CUT = Pattern.compile(
            "[\\(\\[【（]\\s*(?:正确|参考)?答\\s*案\\s*[\\)\\]】）]\\s*[:：]?"
                    + "|(?:正确|参考)?答\\s*案\\s*[:：]"
                    + "|(?:解析|分析|详解|解释)\\s*[:：]");

    private static final int MIN_OPTIONS = 2;

    public Result extract(List<String> block) {
        for (int i = 0; i < block.size(); i++) {
            List<Option> inline = parseInlineMulti(cutAtAnswerMarker(block.get(i)));
            if (inline.size() >= MIN_OPTIONS) {
                return new Result(join(block.subList(0, i)), inline, hasBrokenKeys(inline));
            }
        }

        boolean answerCrossed = false;
        for (int i = 0; i < block.size(); i++) {
            if (answerCrossed) break;
            String raw = block.get(i);
            boolean hasAnswer = ANSWER_ZONE_CUT.matcher(raw).find();
            String line = cutAtAnswerMarker(raw);
            List<Option> found = parseInlineParen(line);
            boolean candidate = !found.isEmpty() && !(found.size() == 1 && startsWithParenMark(line));
            if (!candidate) {
                if (hasAnswer) answerCrossed = true;
                continue;
            }
            if (hasAnswer) {
                answerCrossed = true;
            } else {
                for (int j = i + 1; j < block.size(); j++) {
                    String rj = block.get(j);
                    boolean jAnswer = ANSWER_ZONE_CUT.matcher(rj).find();
                    String nj = cutAtAnswerMarker(rj);
                    List<Option> next = parseInlineParen(nj);
                    if (next.isEmpty()) {
                        if (jAnswer) {
                            answerCrossed = true;
                            break;
                        }
                        String cont = nj.strip();
                        if (cont.isEmpty() || cont.matches("\\d{1,3}")) continue;  // 页码/空行
                        appendWrappedText(found, cont);
                        continue;
                    }
                    appendWrappedText(found, parenStemPrefix(nj));
                    found.addAll(next);
                    if (jAnswer) {
                        answerCrossed = true;
                        break;
                    }
                }
            }
            if (found.size() >= MIN_OPTIONS) {
                List<String> stemLines = new ArrayList<>(block.subList(0, i));
                String prefix = parenStemPrefix(line);
                if (!prefix.isEmpty()) stemLines.add(prefix);
                Option last = found.get(found.size() - 1);
                found.set(found.size() - 1,
                        new Option(last.key(), stripInlineParenTail(last.text())));
                return new Result(join(stemLines), found, hasBrokenKeys(found));
            }
        }

        int firstOption = -1;
        List<Option> options = new ArrayList<>();
        StringBuilder currentText = null;
        String currentKey = null;

        for (int i = 0; i < block.size(); i++) {
            String raw = block.get(i);
            boolean answerStarts = ANSWER_ZONE_CUT.matcher(raw).find();
            Matcher m = OPTION_LINE.matcher(cutAtAnswerMarker(raw));
            if (m.matches()) {
                if (firstOption < 0) firstOption = i;
                if (currentKey != null) {
                    options.add(new Option(currentKey, currentText.toString().strip()));
                }
                currentKey = m.group(1).toUpperCase();
                currentText = new StringBuilder(m.group(2).strip());
            } else if (currentKey != null) {
                if (answerStarts) break;
                if (!currentText.isEmpty()
                        && !currentText.toString().endsWith(",")
                        && isCjk(currentText.charAt(currentText.length() - 1))
                        && !block.get(i).isBlank()
                        && isCjk(block.get(i).strip().charAt(0))) {
                    currentText.append(block.get(i).strip());
                } else {
                    currentText.append(' ').append(block.get(i).strip());
                }
            }
            if (answerStarts) break;
        }
        if (currentKey != null) {
            options.add(new Option(currentKey, currentText.toString().strip()));
        }

        if (options.size() < MIN_OPTIONS || firstOption < 0) {
            return new Result(join(block), List.of(), false);
        }
        return new Result(join(block.subList(0, firstOption)), options, hasBrokenKeys(options));
    }

    private List<Option> parseInlineMulti(String line) {
        List<Option> found = new ArrayList<>();
        Matcher m = INLINE_MULTI.matcher(line);
        while (m.find()) {
            found.add(new Option(m.group(1).toUpperCase(), m.group(2).strip()));
        }
        return found.size() >= MIN_OPTIONS ? found : List.of();
    }

    /**
     * 一行内的括号选项原样匹配
     */
    private List<Option> parseInlineParen(String line) {
        List<Option> found = new ArrayList<>();
        Matcher m = INLINE_PAREN.matcher(line);
        while (m.find()) {
            found.add(new Option(m.group(1).toUpperCase(), m.group(2).strip()));
        }
        return found;
    }

    private boolean startsWithParenMark(String line) {
        return line.strip().matches("^[\\(（]\\s*[A-Ha-h]\\s*[\\)）].*$");
    }

    /**
     * 把换行拆开的残段并入上一选项文本
     */
    private void appendWrappedText(List<Option> acc, String fragment) {
        if (fragment.isEmpty()) return;
        Option last = acc.get(acc.size() - 1);
        String text = last.text();
        String joined;
        if (text.isEmpty()) {
            joined = fragment;
        } else if (isCjk(text.charAt(text.length() - 1)) && isCjk(fragment.charAt(0))) {
            joined = text + fragment;
        } else {
            joined = text + " " + fragment;
        }
        acc.set(acc.size() - 1, new Option(last.key(), joined));
    }

    /**
     * 首个括号选项标记前的行首残段
     */
    private String parenStemPrefix(String line) {
        Matcher m = INLINE_PAREN.matcher(line);
        return m.find() ? line.substring(0, m.start()).strip() : "";
    }

    /**
     * 选项区终止标记前的
     */
    private String cutAtAnswerMarker(String line) {
        Matcher m = ANSWER_ZONE_CUT.matcher(line);
        return m.find() ? line.substring(0, m.start()) : line;
    }

    private String stripInlineParenTail(String text) {
        Matcher cut = ANSWER_ZONE_CUT.matcher(text);
        return (cut.find() ? text.substring(0, cut.start()) : text).strip();
    }

    private boolean hasBrokenKeys(List<Option> options) {
        for (int i = 0; i < options.size(); i++) {
            char expected = (char) ('A' + i);
            if (!options.get(i).key().equals(String.valueOf(expected))) {
                return true;
            }
        }
        return false;
    }

    private String join(List<String> lines) {
        return String.join("\n", lines).strip();
    }

    private boolean isCjk(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    public record Result(String stem, List<Option> options, boolean brokenKeys) {
    }
}
