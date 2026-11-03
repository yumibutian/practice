package jh.com.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("qb_parse_task")
public class ParseTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private Long fileId;
    private Long answerFileId;
    private String format;
    private String stage;
    private Integer status;
    private Integer progress;
    private Integer totalQuestions;
    private Integer warningCount;
    private String resultKey;
    private String previewJson;
    /** 首次 commit 结果 JSON（幂等键），见 V2 迁移 */
    private String commitResultJson;
    private String warningsJson;
    private String errorCode;
    private String errorMsg;
    private Integer retryCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
