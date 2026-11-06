package jh.com.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("question_bank")
public class QuestionBank {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String name;
    private Long sourceFileId;
    private Integer visibility;
    private Integer questionCount;
    private String examConfigJson;
    private Integer pinned;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
