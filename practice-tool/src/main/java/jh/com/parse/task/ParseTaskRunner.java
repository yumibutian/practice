package jh.com.parse.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jh.com.common.BizException;
import jh.com.common.ErrorCode;
import jh.com.entity.File;
import jh.com.entity.FullIr;
import jh.com.entity.ParseTask;
import jh.com.mapper.ParseTaskMapper;
import jh.com.parse.ParsePipeline;
import jh.com.parse.model.NormalizedQuestion;
import jh.com.parse.model.ParseContext;
import jh.com.parse.model.TextDoc;
import jh.com.parse.parser.FormatReader;
import jh.com.parse.parser.FormatReaderRegistry;
import jh.com.service.FileService;
import jh.com.service.ParseTaskService;
import jh.com.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

/**
 * 解析任务异步执行器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParseTaskRunner {

    private static final int STATUS_FAILED = 3;
    private static final int PREVIEW_SIZE = 20;

    private final ParseTaskMapper taskMapper;
    private final ParseTaskService taskService;
    private final FileService fileService;
    private final FileStorage fileStorage;
    private final FormatReaderRegistry readerRegistry;
    private final UserParseLock userParseLock;
    private final ObjectMapper objectMapper = new ObjectMapper();


    private final ParsePipeline pipeline = new ParsePipeline();

    @Resource
    @Qualifier("quizParseExecutor")
    private ThreadPoolTaskExecutor parseExecutor;

    @Value("${quiz.parse.max-questions-per-file}")
    private int maxQuestions;

    public void submit(Long taskId) {
        try {
            parseExecutor.execute(() -> runGuarded(taskId));
        } catch (RejectedExecutionException e) {
            ParseTask fail = new ParseTask();
            fail.setId(taskId);
            fail.setStatus(STATUS_FAILED);
            fail.setErrorCode(ErrorCode.TASK_BUSY.name());
            fail.setErrorMsg(ErrorCode.TASK_BUSY.getDefaultMessage());
            fail.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(fail);
        }
    }

    void runGuarded(Long taskId) {
        ParseTask task = taskMapper.selectById(taskId);
        if (task == null) return;
        try {
            run(task);
        } catch (Exception e) {
            log.error("解析任务失败 taskId={}", taskId, e);
            String code = (e instanceof BizException be)
                    ? be.getCode().name() : ErrorCode.PARSE_FAILED.name();
            ParseTask fail = new ParseTask();
            fail.setId(taskId);
            fail.setStatus(STATUS_FAILED);
            fail.setErrorCode(code);
            fail.setErrorMsg(e.getMessage() == null
                    ? ErrorCode.PARSE_FAILED.getDefaultMessage() : e.getMessage());
            fail.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(fail);
        } finally {
            userParseLock.release(task.getUserId());
        }
    }

    private void run(ParseTask task) throws Exception {
        File file = fileService.requireOwned(task.getUserId(), task.getFileId());
        taskService.updateProgress(task.getId(), ParseStage.NORMALIZE, 5);

        FormatReader reader = readerRegistry.resolve(file.getFileExt());
        ParseContext ctx = new ParseContext(null);

        TextDoc doc;
        try (InputStream in = fileStorage.read(file.getStorageKey())) {
            doc = reader.build(in, ctx);
        }
        ctx.setDoc(doc);

        if (doc.lines().isEmpty()) {
            throw new BizException(ErrorCode.FILE_EMPTY, "文件内容为空");
        }

        List<NormalizedQuestion> questions = pipeline.run(doc, ctx, (stage, progress) ->
                taskService.updateProgress(task.getId(), ParseStage.valueOf(stage), progress));

        if (questions.size() > maxQuestions) {
            throw new BizException(ErrorCode.MAX_QUESTIONS_EXCEEDED,
                    "题目数量 " + questions.size() + " 超过上限 " + maxQuestions);
        }
        if (questions.isEmpty()) {
            throw new BizException(ErrorCode.SECTION_UNANCHORED, "未识别到题目结构");
        }

        taskService.updateProgress(task.getId(), ParseStage.PREVIEW, 95);

        int warnCount = questions.stream().mapToInt(q -> q.getWarnings().size()).sum()
                + ctx.globalWarnings().size();

        FullIr ir = new FullIr(FullIr.CURRENT_VERSION, file.getFileName(), questions);
        String resultKey = "ir/" + task.getUserId() + "/" + task.getId() + ".json";
        byte[] payload = objectMapper.writeValueAsBytes(ir);
        fileStorage.store(new ByteArrayInputStream(payload), resultKey, payload.length);

        ParseTask done = new ParseTask();
        done.setId(task.getId());
        done.setStatus(ParseTaskStatus.SUCCESS.getCode());
        done.setStage(ParseStage.DONE.name());
        done.setProgress(100);
        done.setTotalQuestions(questions.size());
        done.setWarningCount(warnCount);
        done.setResultKey(resultKey);
        done.setPreviewJson(objectMapper.writeValueAsString(
                questions.subList(0, Math.min(PREVIEW_SIZE, questions.size()))));
        done.setWarningsJson(objectMapper.writeValueAsString(ctx.globalWarnings()));
        done.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(done);
    }
}
