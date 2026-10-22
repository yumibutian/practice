package jh.com.parse.parser;

import jh.com.common.BizException;
import jh.com.common.ErrorCode;
import jh.com.parse.model.ParseContext;
import jh.com.parse.model.TextDoc;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Excel 读取器
 */
public class ExcelFormatReader implements FormatReader {

    /**
     * 模糊匹配表头
     */
    private static final Map<String, List<String>> COLUMN_KEYWORDS = Map.of(
            "stem", List.of("题干", "题目", "问题", "试题", "内容", "question", "stem", "title"),
            "answer", List.of("答案", "正确答案", "answer", "key"),
            "analysis", List.of("解析", "分析", "详解", "答案解析", "analysis", "explain"),
            "type", List.of("题型", "类型", "type", "category"),
            "material", List.of("材料", "资料", "阅读材料", "共用题干", "material", "passage")
    );

    private static final char[] OPTION_KEYS = "ABCDEFGH".toCharArray();
    private static final int MAX_HEADER_SCAN_ROWS = 5;
    private static final int MIN_HEADER_MATCHES = 2;

    @Override
    public Set<String> supportedExts() {
        return Set.of("xlsx", "xls");
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public TextDoc build(InputStream in, ParseContext ctx) {
        try (Workbook wb = WorkbookFactory.create(in)) {
            List<String> out = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                out.addAll(readSheet(wb.getSheetAt(i)));
            }
            return TextDocBuilder.fromLines(out);
        } catch (EncryptedDocumentException e) {
            throw new BizException(ErrorCode.FILE_ENCRYPTED, "Excel 已加密", e);
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARSE_FAILED, "Excel 读取失败", e);
        }
    }

    private List<String> readSheet(Sheet sheet) {
        List<String> out = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();

        int headerRow = findHeaderRow(sheet, fmt);
        if (headerRow < 0) return out;

        Map<String, Integer> colIndex = mapColumns(sheet.getRow(headerRow), fmt);
        if (!colIndex.containsKey("stem")) return out;

        // 合并单元格
        Map<String, String> mergedFill = collectMergedValues(sheet, fmt);

        int lastRow = sheet.getLastRowNum();
        for (int r = headerRow + 1; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String material = cell(row, colIndex.get("material"), fmt, mergedFill, r);
            String stem = cell(row, colIndex.get("stem"), fmt, mergedFill, r);
            String answer = cell(row, colIndex.get("answer"), fmt, mergedFill, r);
            String analysis = cell(row, colIndex.get("analysis"), fmt, mergedFill, r);

            List<String> optionTexts = new ArrayList<>();
            for (char key : OPTION_KEYS) {
                Integer idx = colIndex.get("opt" + key);
                if (idx == null) continue;
                String v = cell(row, idx, fmt, mergedFill, r);
                if (v != null && !v.isBlank()) optionTexts.add(key + ". " + v.strip());
            }

            boolean rowEmpty = (stem == null || stem.isBlank())
                    && optionTexts.isEmpty() && (answer == null || answer.isBlank())
                    && (material == null || material.isBlank());
            if (rowEmpty) continue;

            // 逐行展开
            if (material != null && !material.isBlank()) out.add(material.strip());
            if (stem != null && !stem.isBlank()) {
                out.add(stem.strip());
            }
            // 题干为空但选项有值, 归并到上一题的选项区
            out.addAll(optionTexts);
            if (answer != null && !answer.isBlank()) out.add("答案：" + answer.strip());
            if (analysis != null && !analysis.isBlank()) out.add("解析：" + analysis.strip());
        }
        return out;
    }

    private int findHeaderRow(Sheet sheet, DataFormatter fmt) {
        int limit = Math.min(MAX_HEADER_SCAN_ROWS, sheet.getLastRowNum() + 1);
        for (int r = 0; r < limit; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<String, Integer> map = mapColumns(row, fmt);
            if (map.size() >= MIN_HEADER_MATCHES && map.containsKey("stem")) {
                return r;
            }
        }
        return -1;
    }

    private Map<String, Integer> mapColumns(Row header, DataFormatter fmt) {
        Map<String, Integer> result = new HashMap<>();
        if (header == null) return result;
        for (int c = 0; c < header.getLastCellNum(); c++) {
            Cell cell = header.getCell(c);
            if (cell == null) continue;
            String raw = fmt.formatCellValue(cell).strip().toLowerCase();
            if (raw.isEmpty()) continue;

            for (var e : COLUMN_KEYWORDS.entrySet()) {
                if (matchesAny(raw, e.getValue())) {
                    result.putIfAbsent(e.getKey(), c);
                }
            }
            for (char key : OPTION_KEYS) {
                String k = String.valueOf(key).toLowerCase();
                if (raw.equals(k) || raw.equals("选项" + k) || raw.equals("选项" + key)
                        || raw.equals("option" + k)) {
                    result.putIfAbsent("opt" + key, c);
                }
            }

            for (int n = 0; n < OPTION_KEYS.length; n++) {
                if (raw.equals("选项" + (n + 1))
                        || raw.equals("选项" + "一二三四五六七八".charAt(n))) {
                    result.putIfAbsent("opt" + OPTION_KEYS[n], c);
                }
            }
        }
        return result;
    }

    private boolean matchesAny(String raw, List<String> keywords) {
        return keywords.stream().anyMatch(raw::contains);
    }

    // 合并区域内所有坐标都映射到左上角的值
    private Map<String, String> collectMergedValues(Sheet sheet, DataFormatter fmt) {
        Map<String, String> fill = new HashMap<>();
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            Row first = sheet.getRow(region.getFirstRow());
            if (first == null) continue;
            Cell source = first.getCell(region.getFirstColumn());
            if (source == null) continue;
            String value = fmt.formatCellValue(source).strip();
            if (value.isEmpty()) continue;
            for (int r = region.getFirstRow(); r <= region.getLastRow(); r++) {
                for (int c = region.getFirstColumn(); c <= region.getLastColumn(); c++) {
                    fill.put(r + ":" + c, value);
                }
            }
        }
        return fill;
    }

    private String cell(Row row, Integer col, DataFormatter fmt,
                        Map<String, String> mergedFill, int rowIdx) {
        if (col == null) return null;
        String merged = mergedFill.get(rowIdx + ":" + col);
        if (merged != null) return merged;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        return fmt.formatCellValue(cell).strip();
    }
}
