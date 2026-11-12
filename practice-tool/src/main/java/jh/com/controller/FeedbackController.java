package jh.com.controller;

import jh.com.auth.UserContext;
import jh.com.common.ApiResponse;
import jh.com.doto.FeedbackReq;
import jh.com.doto.FeedbackResp;
import jh.com.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 内容反馈
@RestController
@RequestMapping("feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ApiResponse<FeedbackResp> submit(@RequestBody FeedbackReq req) {
        return ApiResponse.ok(new FeedbackResp(
                feedbackService.submit(UserContext.requireUserId(), req)));
    }
}
