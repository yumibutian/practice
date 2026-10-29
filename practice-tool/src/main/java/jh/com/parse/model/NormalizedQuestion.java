package jh.com.parse.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NormalizedQuestion {

    private int index;
    private Integer sourceSeq;
    private QuestionType type = QuestionType.SINGLE;
    private String stem = "";
    private String stemFormat = "text";
    private List<Option> options = new ArrayList<>();
    private List<String> answers = new ArrayList<>();
    private String analysis;
    private String groupId;
    private int sourceOffset;
    private int sourceLength;

    private double confidence = 0.0;
    private final List<Warning> warnings = new ArrayList<>();

    public void addWarning(Warning w) { if (w != null) this.warnings.add(w); }

    public boolean hasError() {
        return warnings.stream().anyMatch(w -> Warning.ERROR.equals(w.level()));
    }


}
