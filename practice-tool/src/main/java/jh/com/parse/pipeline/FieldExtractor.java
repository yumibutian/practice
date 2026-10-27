package jh.com.parse.pipeline;


import jh.com.parse.extract.AnswerExtractor;
import jh.com.parse.extract.OptionExtractor;
import jh.com.parse.extract.TypeInferrer;
import jh.com.parse.model.*;
import jh.com.parse.score.ConfidenceScorer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 第七步 抽取字段。所有文本在最后统一 restore 公式占位符。
 */
public class FieldExtractor {

    private final OptionExtractor optionExtractor = new OptionExtractor();
    private final AnswerExtractor answerExtractor = new AnswerExtractor();
    private final TypeInferrer typeInferrer = new TypeInferrer();
    private final ConfidenceScorer scorer = new ConfidenceScorer();

    public List<NormalizedQuestion> extract(TextDoc doc,
                                            List<QuestionAnchorSplitter.Block> blocks,
                                            ParseContext ctx) {
        List<NormalizedQuestion> result = new ArrayList<>(blocks.size());
        int index = 0;

        for (QuestionAnchorSplitter.Block block : blocks) {
            index++;
            List<TextLine> blockLines = doc.lines().subList(block.fromLine(), block.toLine() + 1);
            List<String> texts = new ArrayList<>(blockLines.stream().map(TextLine::text).toList());

            // 去掉首行开头的题号标记
            if (!texts.isEmpty()) {
                texts.set(0, texts.get(0).replaceFirst(
                        "^\\s{0,8}(?:第\\s*)?[\\(\\[]?\\d{1,3}[\\)\\]]?\\s*[.、．,，:：]?\\s*", ""));
            }

            OptionExtractor.Result opt = optionExtractor.extract(texts);
            AnswerExtractor.Result ans = answerExtractor.extract(texts, ctx.answerZone());

            NormalizedQuestion question = new NormalizedQuestion();
            question.setIndex(index);
            question.setSourceSeq(block.sourceSeq());
            // 题干必须先切掉答案/解析区：判断题没有字母选项，OptionExtractor 的
            // 「无选项」分支把整个题块当题干，答案行与解析行会一并带进来（真实题库 300/300 中招，
            // 见 AnswerExtractor.cutStemZone）。有选项的题走的是「选项之前」那段，天然干净，
            // 对它们调用本方法是空操作。
            question.setStem(AnswerExtractor.cutStemZone(ctx.restore(opt.stem())));
            question.setOptions(opt.options().stream()
                    .map(o -> new Option(o.key(), ctx.restore(o.text())))
                    .toList());
            question.setAnalysis(ctx.restore(ans.analysis()));

            // 答案：行内优先，缺失时按段取答案区分区回填
            if (!ans.answers().isEmpty()) {
                question.setAnswers(ans.answers());
            } else {
                Map<Integer, String> zone = zoneForBlock(block, ctx);
                AnswerExtractor.Result z = answerExtractor.fromZone(
                        block.sourceSeq(), zone, ans.analysis());
                question.setAnswers(z.answers());
            }

            question.setType(typeInferrer.inferWithHint(question.getOptions(), question.getAnswers(),
                    question.getStem(), block.typeHint()));

            TextLine first = doc.lines().get(block.fromLine());
            TextLine last = doc.lines().get(block.toLine());
            question.setSourceOffset(first.sourceOffset());
            question.setSourceLength((last.sourceOffset() + last.sourceLength()) - first.sourceOffset());

            // warnings
            if (question.getAnswers().isEmpty()) {
                question.addWarning(Warning.error("answer_missing", "未识别到答案",
                        question.getSourceOffset(), 0));
            }
            if (opt.brokenKeys()) {
                question.addWarning(Warning.warn("options_broken", "选项字母不连续",
                        question.getSourceOffset(), 0));
            }
            if (block.typeHint() != null
                    && block.typeHint() != typeInferrer.infer(question.getOptions(), question.getAnswers(), question.getStem())) {
                question.addWarning(new Warning("type_conflict", Warning.INFO,
                        "段标题与推断结果冲突，已采用段标题", question.getSourceOffset(), 0));
            }
            int stemLen = question.getStem() == null ? 0 : question.getStem().length();
            if (stemLen < 5) {
                question.addWarning(Warning.warn("stem_too_short", "题干过短", question.getSourceOffset(), 0));
            }
            if (stemLen > 500) {
                question.addWarning(Warning.warn("stem_too_long", "题干过长，可能多题粘连",
                        question.getSourceOffset(), 0));
            }

            question.setConfidence(scorer.score(question));
            result.add(question);
        }
        return result;
    }

    /**
     * 按 Block 所在段取答案区分区
     */
    private Map<Integer, String> zoneForBlock(QuestionAnchorSplitter.Block block, ParseContext ctx) {
        List<Map<Integer, String>> partitions = ctx.answerZonePartitions();
        if (block.sectionOrdinal() >= 0 && block.sectionOrdinal() < partitions.size()) {
            return partitions.get(block.sectionOrdinal());
        }
        return ctx.answerZone();
    }
}
