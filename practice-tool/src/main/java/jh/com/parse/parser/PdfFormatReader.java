package jh.com.parse.parser;

import jh.com.common.BizException;
import jh.com.common.ErrorCode;
import jh.com.parse.model.ParseContext;
import jh.com.parse.model.TextDoc;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * PDF 读取器
 */
public class PdfFormatReader implements FormatReader {

    @Override
    public Set<String> supportedExts() { return Set.of("pdf"); }

    @Override
    public int priority() { return 15; }

    @Override
    public TextDoc build(InputStream in, ParseContext ctx) {
        try (PDDocument pdf = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            List<String> lines = new ArrayList<>();
            int pageCount = pdf.getNumberOfPages();
            for (int p = 1; p <= pageCount; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(pdf);
                for (String line : text.split("\r\n|\r|\n")) {
                    lines.add(line.stripTrailing());
                }
            }
            return TextDocBuilder.fromLines(lines);
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARSE_FAILED, "PDF 读取失败（加密或损坏？）", e);
        }
    }
}
