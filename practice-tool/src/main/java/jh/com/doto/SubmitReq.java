package jh.com.doto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SubmitReq(@NotNull(message = "不能为空") List<AnswerItem> answers) {

    public record AnswerItem(Long questionId, List<String> answer, Integer durationMs) {}
}
