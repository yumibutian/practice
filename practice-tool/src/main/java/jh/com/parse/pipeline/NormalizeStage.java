package jh.com.parse.pipeline;


import jh.com.parse.model.ParseContext;
import jh.com.parse.model.TextDoc;

/**
 * 正常的阶段
 */
public interface NormalizeStage {

    String name();

    TextDoc apply(TextDoc doc, ParseContext ctx);
}
