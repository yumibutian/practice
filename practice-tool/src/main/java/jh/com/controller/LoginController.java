package jh.com.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.constraints.NotNull;
import jh.com.common.ApiResponse;
import lombok.Data;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登录
 */
@RestController
@RequestMapping("auth")
@Profile({"dev", "local", "test"})
public class LoginController {

    @Data
    public static class DevLoginReq {
        @NotNull(message = "不能为空")
        private Long userId;
    }

    @PostMapping("/dev-login")
    public ApiResponse<Map<String, Object>> devLogin(@RequestBody @Validated DevLoginReq req) {
        StpUtil.login(req.getUserId());
        return ApiResponse.ok(Map.of(
            "token", StpUtil.getTokenValue(),
            "tokenName", StpUtil.getTokenName(),
            "userId", req.getUserId()
        ));
    }
}
