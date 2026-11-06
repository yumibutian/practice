package jh.com.doto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StartReq(
        @NotNull(message = "不能为空") Long bankId,
        @NotBlank(message = "不能为空") String mode,
        ExamConfig config,
        Long recordId
) {
    public StartReq(Long bankId, String mode, ExamConfig config) {
        this(bankId, mode, config, null);
    }
}
