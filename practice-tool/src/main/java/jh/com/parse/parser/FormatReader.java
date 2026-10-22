package jh.com.parse.parser;


import jh.com.parse.model.ParseContext;
import jh.com.parse.model.TextDoc;

import java.io.InputStream;
import java.util.Set;

/**
 * 格式读取器：所有格式统一产出 TextDoc，
 */
public interface FormatReader {

    Set<String> supportedExts();


    default int priority() {
        return 0;
    }

    // 格式化总入口
    TextDoc build(InputStream in, ParseContext ctx);
}
