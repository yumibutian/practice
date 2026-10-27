package jh.com.parse.pipeline;


import jh.com.parse.anchor.Anchor;
import jh.com.parse.anchor.AnchorScanner;
import jh.com.parse.anchor.AnchorSelector;
import jh.com.parse.anchor.AnswerZoneDetector;
import jh.com.parse.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 自适应题号
 */
public class QuestionAnchorSplitter implements NormalizeStage {

    private static final int MAX_GAP = 3;
    private static final int MIN_CHAIN_TARGET = 3;
    private static final double MIN_COVERAGE = 0.6;
    private final AnchorScanner scanner = new AnchorScanner();
    private final AnchorSelector selector = new AnchorSelector();
    private final AnswerZoneDetector answerZoneDetector = new AnswerZoneDetector();
    private final SectionSplitter sectionSplitter = new SectionSplitter();

    @Override
    public String name() {
        return "STEP6_ANCHOR";
    }


    public List<Block> split(TextDoc doc, ParseContext ctx) {
        List<TextLine> lines = doc.lines();
        List<String> texts = lines.stream().map(TextLine::text).toList();

        // 1. 先切出答案区，后续不再参与切题
        AnswerZoneDetector.AnswerZone zone = answerZoneDetector.scan(texts, ctx.answerZone());
        ctx.answerZonePartitions().addAll(zone.partitions());
        int contentEnd = zone.found() ? zone.startLine() - 1 : lines.size() - 1;

        // 2. 分段
        List<String> contentTexts = texts.subList(0, Math.max(0, contentEnd + 1));
        List<Section> sections = sectionSplitter.split(contentTexts);

        // 3. 段内跑 LIS
        List<Block> blocks = new ArrayList<>();
        for (int s = 0; s < sections.size(); s++) {
            Section section = sections.get(s);
            List<String> segTexts = contentTexts.subList(section.startLine(),
                    Math.min(section.endLine() + 1, contentTexts.size()));
            List<Anchor> candidates = scanner.scan(segTexts, 0);
            int minChain = Math.min(MIN_CHAIN_TARGET, candidates.size());
            List<Anchor> chain = selector.select(candidates, MAX_GAP, 1, minChain);

            if (chain.isEmpty()) {
                ctx.globalWarnings().add(Warning.error("section_unanchored",
                        "该段未识别出题目结构：" + section.title(),
                        lines.get(section.startLine()).sourceOffset(), 0));
                continue;
            }
            double coverage = selector.coverage(chain, candidates);
            if (coverage < MIN_COVERAGE) {
                ctx.globalWarnings().add(Warning.warn("anchors_low_coverage",
                        "锚点覆盖率 " + Math.round(coverage * 100) + "%，建议人工确认",
                        lines.get(section.startLine()).sourceOffset(), 0));
            }

            for (int k = 0; k < chain.size(); k++) {
                int from = chain.get(k).offset() + section.startLine();
                int to = (k + 1 < chain.size())
                        ? chain.get(k + 1).offset() + section.startLine() - 1
                        : Math.min(section.endLine(), contentEnd);

                if (to < from) continue;
                blocks.add(new Block(chain.get(k).number(), from, to,
                        section.typeHint(), section.scorePerQuestion(), s));
            }
        }
        return blocks;
    }

    @Override
    public TextDoc apply(TextDoc doc, ParseContext ctx) {
        return doc;   // 本步不修改文本，只产出切块
    }

    public record Block(int sourceSeq, int fromLine, int toLine,
                        QuestionType typeHint, Double scorePerQuestion, int sectionOrdinal) {
    }
}
