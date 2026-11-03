package jh.com.entity;

import jh.com.parse.model.NormalizedQuestion;

import java.util.List;

/**
 * 全量 IR
 */
public record FullIr(int version, String fileName, List<NormalizedQuestion> questions) {

    public static final int CURRENT_VERSION = 1;
}
