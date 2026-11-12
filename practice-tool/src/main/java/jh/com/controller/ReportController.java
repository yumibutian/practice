package jh.com.controller;

import jh.com.auth.UserContext;
import jh.com.common.ApiResponse;
import jh.com.doto.ReportResp;
import jh.com.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("practice")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/{recordId}/report")
    public ApiResponse<ReportResp> report(@PathVariable Long recordId) {
        return ApiResponse.ok(reportService.report(UserContext.requireUserId(), recordId));
    }
}
