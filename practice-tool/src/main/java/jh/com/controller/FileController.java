package jh.com.controller;


import jakarta.validation.Valid;
import jh.com.auth.UserContext;
import jh.com.common.ApiResponse;
import jh.com.common.BizException;
import jh.com.common.ErrorCode;
import jh.com.doto.FileInitReq;
import jh.com.doto.FileInitResp;
import jh.com.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/init")
    public ApiResponse<FileInitResp> init(@RequestBody @Valid FileInitReq req) {
        return ApiResponse.ok(fileService.init(UserContext.requireUserId(), req));
    }

    /**
     * 切片上传
     */
    @PostMapping("/{fileId}/upload")
    public ApiResponse<Void> upload(@PathVariable Long fileId,
                                    @RequestParam("file") MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            fileService.upload(UserContext.requireUserId(), fileId, in, file.getSize());
        } catch (IOException e) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "文件读取失败");
        }
        return ApiResponse.ok();
    }
}
