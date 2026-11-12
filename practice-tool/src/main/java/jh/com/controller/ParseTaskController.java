package jh.com.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jh.com.auth.UserContext;
import jh.com.common.ApiResponse;
import jh.com.common.BizException;
import jh.com.common.ErrorCode;
import jh.com.doto.ParseTaskView;
import jh.com.entity.File;
import jh.com.entity.ParseTask;
import jh.com.entity.QuestionOverride;
import jh.com.parse.model.NormalizedQuestion;
import jh.com.parse.model.Warning;
import jh.com.parse.task.ParseTaskRunner;
import jh.com.parse.task.ParseTaskStatus;
import jh.com.service.FileService;
import jh.com.service.ParseCommitService;
import jh.com.service.ParseTaskService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("parse")
@RequiredArgsConstructor
public class ParseTaskController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ParseTaskService taskService;

    private final ParseTaskRunner taskRunner;

    private final ParseCommitService commitService;

    private final FileService fileService;

    @PostMapping
    public ApiResponse<Object> create(@RequestBody @Valid CreateReq req) {
        Long userId = UserContext.requireUserId();
        File file = fileService.requireOwned(userId, req.getFileId());

        Long taskId = taskService.createOrReuse(userId, req.getFileId(),
                req.getAnswerFileId(), req.getHintFormat() == null
                        ? file.getFileExt() : req.getHintFormat());
        taskRunner.submit(taskId);
        return ApiResponse.ok(Map.of("taskId", taskId, "status", 0));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<ParseTaskView> get(@PathVariable Long taskId) throws Exception {
        ParseTask task = taskService.requireOwned(UserContext.requireUserId(), taskId);

        List<NormalizedQuestion> preview = List.of();
        if (task.getStatus() != null
                && task.getStatus() == ParseTaskStatus.SUCCESS.getCode()
                && task.getPreviewJson() != null) {
            preview = MAPPER.readValue(task.getPreviewJson(),
                    MAPPER.getTypeFactory().constructCollectionType(List.class, NormalizedQuestion.class));
        }
        List<Warning> global = List.of();
        if (task.getWarningsJson() != null && !task.getWarningsJson().isBlank()) {
            global = MAPPER.readValue(task.getWarningsJson(),
                    MAPPER.getTypeFactory().constructCollectionType(List.class, Warning.class));
        }

        return ApiResponse.ok(new ParseTaskView(task.getId(), task.getStatus(),
                task.getStage(), task.getProgress(), task.getTotalQuestions(),
                task.getWarningCount(), preview, global,
                task.getErrorCode(), task.getErrorMsg()));
    }

    @PatchMapping("/{taskId}/questions/{idx}")
    public ApiResponse<Void> patchQuestion(@PathVariable Long taskId, @PathVariable Integer idx,
                                           @RequestBody QuestionOverride override) {
        taskService.requireOwned(UserContext.requireUserId(), taskId);
        if (override.idx() == null || !override.idx().equals(idx)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "idx 与路径不一致");
        }
        return ApiResponse.ok();
    }

    @GetMapping("/{taskId}/preview")
    public ApiResponse<ParseCommitService.PreviewPage> preview(@PathVariable Long taskId,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(commitService.previewPage(
                UserContext.requireUserId(), taskId, page, size));
    }

    @PostMapping("/{taskId}/commit")
    public ApiResponse<ParseCommitService.CommitResult> commit(@PathVariable Long taskId,
                                                               @RequestBody @Valid CommitReq req) {
        return ApiResponse.ok(commitService.commit(UserContext.requireUserId(), taskId,
                req.getBankName(), req.getVisibility(), req.getExcludedIdx(), req.getOverrides()));
    }

    @DeleteMapping("/{taskId}")
    public ApiResponse<Void> cancel(@PathVariable Long taskId) {
        taskService.cancel(UserContext.requireUserId(), taskId);
        return ApiResponse.ok();
    }

    @Data
    public static class CreateReq {
        @NotNull(message = "不能为空")
        private Long fileId;
        private Long answerFileId;
        private String hintFormat;
    }

    @Data
    public static class CommitReq {
        @NotBlank(message = "不能为空")
        private String bankName;
        private Integer visibility = 0;
        private List<Integer> excludedIdx;
        private List<QuestionOverride> overrides;
    }
}
