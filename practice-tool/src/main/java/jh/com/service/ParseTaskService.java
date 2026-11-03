package jh.com.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jh.com.common.BizException;
import jh.com.common.ErrorCode;
import jh.com.entity.ParseTask;
import jh.com.mapper.ParseTaskMapper;
import jh.com.parse.task.ParseStage;
import jh.com.parse.task.ParseTaskStatus;
import jh.com.parse.task.UserParseLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParseTaskService {

    private static final int STATUS_QUEUED = 0;
    private static final int STATUS_RUNNING = 1;
    private static final int STATUS_SUCCESS = 2;
    private static final int STATUS_CANCELED = 9;

    private final ParseTaskMapper taskMapper;
    private final UserParseLock userParseLock;

    @Transactional
    public Long createOrReuse(Long userId, Long fileId, Long answerFileId, String format) {
        ParseTask running = taskMapper.selectOne(Wrappers.<ParseTask>lambdaQuery()
                .eq(ParseTask::getUserId, userId)
                .eq(ParseTask::getFileId, fileId)
                .in(ParseTask::getStatus, STATUS_QUEUED, STATUS_RUNNING)
                .orderByDesc(ParseTask::getId)
                .last("limit 1"));
        if (running != null) {
            return running.getId();
        }

        if (!userParseLock.tryAcquire(userId)) {
            throw new BizException(ErrorCode.TASK_ALREADY_RUNNING, "已有解析任务进行中，请等待完成");
        }

        ParseTask task = new ParseTask();
        task.setTenantId(0L);
        task.setUserId(userId);
        task.setFileId(fileId);
        task.setAnswerFileId(answerFileId);
        task.setFormat(format);
        task.setStage(ParseStage.QUEUED.name());
        task.setStatus(STATUS_QUEUED);
        task.setProgress(0);
        task.setWarningCount(0);
        task.setRetryCount(0);
        taskMapper.insert(task);
        return task.getId();
    }

    public ParseTask requireOwned(Long userId, Long taskId) {
        ParseTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND, "解析任务不存在");
        }
        if (!task.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该任务");
        }
        return task;
    }

    @Transactional
    public void updateProgress(Long taskId, ParseStage stage, int progress) {
        ParseTask update = new ParseTask();
        update.setId(taskId);
        update.setStage(stage.name());
        update.setProgress(Math.max(0, Math.min(100, progress)));
        if (stage == ParseStage.NORMALIZE) {
            update.setStartedAt(LocalDateTime.now());
        }
        ParseTask current = taskMapper.selectById(taskId);
        if (current != null && current.getStatus() != null
                && current.getStatus() == STATUS_QUEUED) {
            update.setStatus(STATUS_RUNNING);
        }
        taskMapper.updateById(update);
    }

    @Transactional
    public void cancel(Long userId, Long taskId) {
        ParseTask task = requireOwned(userId, taskId);
        if (ParseTaskStatus.SUCCESS.getCode() == task.getStatus()) {
            throw new BizException(ErrorCode.CONFLICT, "任务已完成，无法取消");
        }
        ParseTask update = new ParseTask();
        update.setId(taskId);
        update.setStatus(STATUS_CANCELED);
        update.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(update);
        userParseLock.release(userId);
    }

    public List<ParseTask> listUnfinished() {
        return taskMapper.selectList(Wrappers.<ParseTask>lambdaQuery()
                .in(ParseTask::getStatus, STATUS_QUEUED, STATUS_RUNNING));
    }
}
