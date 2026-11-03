package jh.com.parse.task;

import lombok.Getter;

/**
 * 解析任务状态机
 */
@Getter
public enum ParseTaskStatus {
    QUEUED(0), RUNNING(1), SUCCESS(2), FAILED(3), CANCELED(9);

    private final int code;

    ParseTaskStatus(int code) {
        this.code = code;
    }

    public static ParseTaskStatus fromCode(int code) {
        for (ParseTaskStatus s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("未知的任务状态码：" + code);
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELED;
    }
}
