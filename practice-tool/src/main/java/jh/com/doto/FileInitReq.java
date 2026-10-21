package jh.com.doto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FileInitReq(
    @NotBlank(message = "不能为空")
    String fileName,

    @NotNull(message = "不能为空")
    @Positive(message = "必须为正数")
    Long fileSize,

    @NotBlank(message = "不能为空")
    String fileHash,

    @NotBlank(message = "不能为空")
    String ext
) {}
