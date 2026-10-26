package jh.com.parse.pipeline;

import jh.com.parse.model.ParseContext;
import jh.com.parse.model.TextDoc;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;

/**
 * 统一字符与编码
 */
public class EncodingNormalizer implements NormalizeStage {

    // 标记
    private static final String STRIP_CHARS = "[​‌‍﻿­‎‏]";

    private static final char[] HALF_DIGITS = "0123456789".toCharArray();

    private static final Charset GBK = Charset.forName("GBK");

    public String decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2) {
            if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
                return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
            }
            if ((bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
                return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
            }
        }
        String utf8 = strictDecode(bytes, StandardCharsets.UTF_8);
        if (utf8 != null) {
            return utf8;
        }
        String gbk = strictDecode(bytes, GBK);
        return gbk != null ? gbk : new String(bytes, StandardCharsets.UTF_8);
    }

    // 异常解码是失败处理
    private String strictDecode(byte[] bytes, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer buffer = decoder.decode(ByteBuffer.wrap(bytes));
            String result = buffer.toString();
            long replacement = result.chars().filter(c -> c == '�').count();
            if (result.length() > 0 && replacement * 50 > result.length()) {
                return null;
            }
            return result;
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    public String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replaceAll(STRIP_CHARS, "");
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c == '　') {
                sb.append(' ');
            } else if (c == '\f') {
                sb.append('\n');
            } else if (c >= '０' && c <= '９') {
                sb.append(HALF_DIGITS[c - '０']);
            } else if (c == '．') {
            } else if (c == '？') {
                sb.append('?');
            } else if (c == '：') {
                sb.append(':');
            } else if (c == '；') {
                sb.append(';');
            } else if (c == '（') {
                sb.append('(');
            } else if (c == '）') {
                sb.append(')');
            } else if (c == '【') {
                sb.append('[');
            } else if (c == '】') {
                sb.append(']');
            } else {
                sb.append(c);
            }
        }

        return sb.toString().replace("\r\n", "\n").replace("\r", "\n");
    }

    @Override
    public String name() {
        return "STEP1_ENCODING";
    }

    @Override
    public TextDoc apply(TextDoc doc, ParseContext ctx) {
        return doc;
    }
}
