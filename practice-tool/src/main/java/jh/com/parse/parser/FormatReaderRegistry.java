package jh.com.parse.parser;


import jh.com.common.BizException;
import jh.com.common.ErrorCode;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 格式读取器策略类
 */
public class FormatReaderRegistry {

    private final Map<String, FormatReader> byExt = new HashMap<>();

    public FormatReaderRegistry(List<FormatReader> readers) {
        readers.stream()
            .sorted(Comparator.comparingInt(FormatReader::priority).reversed())
            .forEach(r -> r.supportedExts().forEach(ext ->
                byExt.putIfAbsent(ext.toLowerCase(), r)));
    }

    public FormatReader resolve(String ext) {
        FormatReader reader = byExt.get(ext == null ? "" : ext.toLowerCase().replace(".", ""));
        if (reader == null) {
            throw new BizException(ErrorCode.FILE_EXT_UNSUPPORTED, "不支持的文件类型：" + ext);
        }
        return reader;
    }

    public boolean supports(String ext) {
        return byExt.containsKey(ext == null ? "" : ext.toLowerCase());
    }
}
