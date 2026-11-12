package jh.com.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jh.com.auth.UserContext;
import jh.com.common.ApiResponse;
import jh.com.doto.BankDetailView;
import jh.com.doto.BankQuestionView;
import jh.com.doto.BankView;
import jh.com.doto.ExamConfig;
import jh.com.service.BankService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("banks")
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;

    @GetMapping
    public ApiResponse<List<BankView>> list() {
        return ApiResponse.ok(bankService.list(UserContext.requireUserId()));
    }

    @GetMapping("/{bankId}")
    public ApiResponse<BankDetailView> detail(@PathVariable Long bankId) {
        return ApiResponse.ok(bankService.detail(UserContext.requireUserId(), bankId));
    }

    @GetMapping("/{bankId}/exam-config")
    public ApiResponse<ExamConfig> examConfig(@PathVariable Long bankId) {
        var bank = bankService.requireOwned(UserContext.requireUserId(), bankId);
        return ApiResponse.ok(bankService.readExamConfig(bank));
    }

    @PutMapping("/{bankId}/exam-config")
    public ApiResponse<Void> saveExamConfig(@PathVariable Long bankId,
                                            @RequestBody ExamConfig config) {
        bankService.saveExamConfig(UserContext.requireUserId(), bankId, config);
        return ApiResponse.ok();
    }

    @PatchMapping("/{bankId}/name")
    public ApiResponse<Void> rename(@PathVariable Long bankId, @RequestBody @Valid RenameReq req) {
        bankService.rename(UserContext.requireUserId(), bankId, req.getName());
        return ApiResponse.ok();
    }

    @PatchMapping("/{bankId}/pin")
    public ApiResponse<Void> pin(@PathVariable Long bankId, @RequestBody PinReq req) {
        bankService.pin(UserContext.requireUserId(), bankId, req.isPinned());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{bankId}")
    public ApiResponse<Void> delete(@PathVariable Long bankId) {
        bankService.delete(UserContext.requireUserId(), bankId);
        return ApiResponse.ok();
    }

    /**
     * 题库题目分页
     */
    @GetMapping("/{bankId}/questions")
    public ApiResponse<List<BankQuestionView>> questions(@PathVariable Long bankId,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(bankService.questions(UserContext.requireUserId(), bankId, page, size));
    }

    @Data
    public static class RenameReq {
        @NotBlank(message = "不能为空")
        private String name;
    }

    @Data
    public static class PinReq {
        private boolean pinned;
    }
}
