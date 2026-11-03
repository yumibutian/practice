package jh.com.parse.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jh.com.common.ErrorCode;
import jh.com.entity.ParseTask;
import jh.com.mapper.ParseTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 僵尸任务补偿
 */
@Slf4j
@Component
public class ZombieTaskCompensator {

    private static final int STATUS_QUEUED = 0;
    private static final int STATUS_RUNNING = 1;
    private static final int STATUS_FAILED = 3;

    private final ParseTaskMapper taskMapper;
    private final UserParseLock userParseLock;
    private final long timeoutSeconds;
    private final int maxRetry;

    public ZombieTaskCompensator(ParseTaskMapper taskMapper,
                                 UserParseLock userParseLock,
                                 @Value("${quiz.parse.zombie-timeout-seconds}") long timeoutSeconds,
                                 @Value("${quiz.parse.zombie-max-retry:3}") int maxRetry) {
        this.taskMapper = taskMapper;
        this.userParseLock = userParseLock;
        this.timeoutSeconds = timeoutSeconds;
        this.maxRetry = maxRetry;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void scheduled() {
        try {
            int n = compensate();
            if (n > 0) log.warn("补偿清理僵尸解析任务 {} 个", n);
        } catch (Exception e) {
            log.error("僵尸任务补偿失败", e);
        }
    }

    public int compensate() {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(timeoutSeconds);
        List<ParseTask> candidates = taskMapper.selectList(Wrappers.<ParseTask>lambdaQuery()
                .eq(ParseTask::getStatus, STATUS_RUNNING)
                .lt(ParseTask::getStartedAt, deadline));

        int count = 0;
        for (ParseTask task : candidates) {
            LocalDateTime startedAt = task.getStartedAt();
            if (startedAt == null || !startedAt.isBefore(deadline)) continue;

            ParseTask update = new ParseTask();
            update.setId(task.getId());
            userParseLock.release(task.getUserId());

            int retry = task.getRetryCount() == null ? 0 : task.getRetryCount();
            if (retry >= maxRetry) {
                update.setStatus(STATUS_FAILED);
                update.setErrorCode(ErrorCode.PARSE_FAILED.name());
                update.setErrorMsg("解析超时，已重试 " + retry + " 次仍失败");
                update.setFinishedAt(LocalDateTime.now());
            } else {
                update.setStatus(STATUS_QUEUED);
                update.setStage(ParseStage.QUEUED.name());
                update.setProgress(0);
                update.setRetryCount(retry + 1);
                update.setStartedAt(null);
            }
            taskMapper.updateById(update);
            count++;
        }
        return count;
    }
}
