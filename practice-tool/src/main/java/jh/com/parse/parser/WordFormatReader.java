package jh.com.parse.parser;

import jh.com.common.BizException;
import jh.com.common.ErrorCode;
import jh.com.parse.model.ParseContext;
import jh.com.parse.model.TextDoc;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Word 读取器
 */
public class WordFormatReader implements FormatReader {

    private static final int ZIP_MAGIC_0 = 0x50;
    private static final int ZIP_MAGIC_1 = 0x4B;

    @Override
    public Set<String> supportedExts() { return Set.of("docx", "doc"); }

    @Override
    public int priority() { return 12; }

    @Override
    public TextDoc build(InputStream in, ParseContext ctx) {
        byte[] bytes;
        try {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARSE_FAILED, "Word 读取失败", e);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == ZIP_MAGIC_0
            && (bytes[1] & 0xFF) == ZIP_MAGIC_1) {
            return readDocx(bytes);
        }
        return readLegacyDoc(bytes);
    }

    private TextDoc readDocx(byte[] bytes) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            List<String> lines = new ArrayList<>();
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph para) {
                    addLine(lines, para.getText());
                } else if (element instanceof XWPFTable table) {
                    table.getRows().forEach(row -> row.getTableCells()
                        .forEach(cell -> cell.getParagraphs()
                            .forEach(p -> addLine(lines, p.getText()))));
                }
            }
            return TextDocBuilder.fromLines(lines);
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARSE_FAILED, "DOCX 解析失败", e);
        }
    }

    private TextDoc readLegacyDoc(byte[] bytes) {
        try (HWPFDocument doc = new HWPFDocument(new ByteArrayInputStream(bytes));
             WordExtractor extractor = new WordExtractor(doc)) {
            List<String> lines = new ArrayList<>();
            for (String para : extractor.getParagraphText()) {
                for (String piece : para.split("[\\r]+")) {
                    addLine(lines, piece);
                }
            }
            return TextDocBuilder.fromLines(lines);
        } catch (IOException | RuntimeException e) {
            throw new BizException(ErrorCode.PARSE_FAILED, "DOC 解析失败（损坏或非 Word 文件？）", e);
        }
    }

    private void addLine(List<String> lines, String text) {
        if (text == null || text.isBlank()) return;
        lines.add(text.strip());
    }
}
