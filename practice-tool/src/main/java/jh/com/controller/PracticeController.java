package jh.com.controller;

import jakarta.validation.Valid;
import jh.com.auth.UserContext;
import jh.com.common.ApiResponse;
import jh.com.doto.*;
import jh.com.service.PracticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("practice")
@RequiredArgsConstructor
public class PracticeController {

    private final PracticeService practiceService;

    @PostMapping("/start")
    public ApiResponse<StartResp> start(@RequestBody @Valid StartReq req) {
        return ApiResponse.ok(practiceService.start(UserContext.requireUserId(), req));
    }

    @PostMapping("/{recordId}/submit")
    public ApiResponse<SubmitResp> submit(@PathVariable Long recordId,
                                          @RequestBody @Valid SubmitReq req) {
        return ApiResponse.ok(practiceService.submit(UserContext.requireUserId(), recordId, req));
    }

    /**
     * 中断上报
     */
    @PostMapping("/{recordId}/interrupt")
    public ApiResponse<InterruptResp> interrupt(@PathVariable Long recordId,
                                                @RequestBody(required = false) InterruptReq req) {
        return ApiResponse.ok(practiceService.interrupt(UserContext.requireUserId(), recordId, req));
    }
}
