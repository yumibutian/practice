package jh.com.parse;



import jh.com.parse.model.NormalizedQuestion;
import jh.com.parse.model.ParseContext;
import jh.com.parse.model.TextDoc;
import jh.com.parse.pipeline.*;

import java.util.List;

/**
 * 归一化管线
 */
public class ParsePipeline {

    private final LayoutStripper layoutStripper = new LayoutStripper();
    private final LineRepairer lineRepairer = new LineRepairer();
    private final MathCodeProtector mathCodeProtector = new MathCodeProtector();
    private final QuestionAnchorSplitter anchorSplitter = new QuestionAnchorSplitter();
    private final FieldExtractor fieldExtractor = new FieldExtractor();

    public List<NormalizedQuestion> run(TextDoc rawDoc, ProgressReporter reporter) {
        return run(rawDoc, new ParseContext(rawDoc), reporter);
    }

    /**
     * 带外部调用方需要读到管线内产生的
     */
    public List<NormalizedQuestion> run(TextDoc rawDoc, ParseContext ctx, ProgressReporter reporter) {
        reporter.report("NORMALIZE", 10);
        TextDoc doc = layoutStripper.apply(rawDoc, ctx);

        reporter.report("NORMALIZE", 20);
        doc = lineRepairer.apply(doc, ctx);

        reporter.report("NORMALIZE", 30);
        doc = mathCodeProtector.apply(doc, ctx);
        ctx.setDoc(doc);

        reporter.report("SPLIT", 45);
        List<QuestionAnchorSplitter.Block> blocks = anchorSplitter.split(doc, ctx);

        reporter.report("SPLIT", 70);
        List<NormalizedQuestion> questions = fieldExtractor.extract(doc, blocks, ctx);

        reporter.report("VALIDATE", 90);
        return questions;
    }

    @FunctionalInterface
    public interface ProgressReporter {
        void report(String stage, int progress);
    }
}
