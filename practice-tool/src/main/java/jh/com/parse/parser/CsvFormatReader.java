package jh.com.parse.parser;

import jh.com.common.BizException;
import jh.com.common.ErrorCode;
import jh.com.parse.model.ParseContext;
import jh.com.parse.model.TextDoc;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * CSV 读取器。多行 CSV 的题号按行号递增——样例每行都写死 "1. "
 */
public class CsvFormatReader implements FormatReader {

    private static final List<Character> DELIMITERS = List.of(',', ';', '\t', '|');

    @Override
    public Set<String> supportedExts() { return Set.of("csv", "tsv", "txt"); }

    @Override
    public int priority() { return 5; }

    @Override
    public TextDoc build(InputStream in, ParseContext ctx) {
        byte[] bytes;
        try {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARSE_FAILED, "CSV 读取失败", e);
        }

        Charset charset = detectCharset(bytes);
        char delimiter = sniffDelimiter(new String(bytes, charset));

        List<String> rows = new ArrayList<>();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), charset);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                 .setDelimiter(delimiter)
                 .setIgnoreSurroundingSpaces(true)
                 .setTrim(true)
                 .build()
                 .parse(reader)) {

            boolean first = true;
            int seq = 0;
            for (CSVRecord record : parser) {
                if (first) {
                    first = false;
                    if (looksLikeHeader(record)) continue;
                }
                seq++;
                rows.add(rowToBlock(record, seq));
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARSE_FAILED, "CSV 解析失败", e);
        }

        List<String> out = new ArrayList<>();
        for (String row : rows) {
            if (row.isBlank()) continue;
            out.add(row);
            out.add("");
        }
        return TextDocBuilder.fromLines(out);
    }

    private Charset detectCharset(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
            && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        long bad = utf8.chars().filter(c -> c == '�').count();
        if (utf8.length() > 0 && bad * 50 > utf8.length()) {
            return Charset.forName("GBK");
        }
        return StandardCharsets.UTF_8;
    }

    private char sniffDelimiter(String content) {
        String sample = content.length() > 20000 ? content.substring(0, 20000) : content;
        String[] lines = sample.split("\n", -1);

        char best = ',';
        double bestVariance = Double.MAX_VALUE;
        for (char d : DELIMITERS) {
            List<Integer> counts = new ArrayList<>();
            for (String line : lines) {
                if (line.isBlank()) continue;
                counts.add(count(line, d));
            }
            if (counts.size() < 2) continue;
            double mean = counts.stream().mapToInt(Integer::intValue).average().orElse(0);
            if (mean < 2) continue;
            double variance = counts.stream()
                .mapToDouble(c -> (c - mean) * (c - mean)).average().orElse(0);
            if (variance < bestVariance) {
                bestVariance = variance;
                best = d;
            }
        }
        return best;
    }

    private int count(String line, char delimiter) {
        int n = 0;
        boolean quoted = false;
        for (char c : line.toCharArray()) {
            if (c == '"') quoted = !quoted;
            else if (c == delimiter && !quoted) n++;
        }
        return n + 1;
    }

    private boolean looksLikeHeader(CSVRecord record) {
        String joined = String.join("|", record.toList());
        return joined.contains("题干") || joined.contains("题目")
            || joined.toLowerCase().contains("stem");
    }

    private String rowToBlock(CSVRecord record, int seq) {
        List<String> cells = record.toList();
        StringBuilder sb = new StringBuilder();
        char optionKey = 'A';
        for (int i = 0; i < cells.size(); i++) {
            String value = cells.get(i).strip();
            if (value.isEmpty()) continue;
            if (i == 0) {
                // 首格已带题号则不重复编号
                if (value.matches("^(?:第\\s*)?[\\(\\[]?\\d{1,3}[\\)\\]]?\\s*[.、．,，:：]\\s*\\S.*")) {
                    sb.append(value);
                } else {
                    sb.append(seq).append(". ").append(value);
                }
            } else if (value.startsWith("答案") || value.toLowerCase().startsWith("answer")) {
                sb.append("\n答案：").append(value.replaceFirst("^(答案|answer)[:：]?", "").strip());
            } else if (looksLikeAnswer(value) && i >= 2) {
                sb.append("\n答案：").append(value);
            } else if (i == cells.size() - 1 && looksLikeAnswer(value)) {
                sb.append("\n答案：").append(value);
            } else {
                sb.append("\n").append(optionKey++).append(". ").append(value);
            }
        }
        return sb.toString();
    }

    private boolean looksLikeAnswer(String value) {
        return value.matches("^[A-Ha-h]{1,8}$")
            || value.matches("^(对|错|正确|错误|是|否|√|×|true|false)$");
    }
}
