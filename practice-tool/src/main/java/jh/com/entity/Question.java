package jh.com.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("question")
public class Question {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long bankId;
    private Integer seq;
    private String type;
    private String stem;
    private String stemFormat;
    private String optionsJson;
    private String answerJson;
    private String analysis;
    private Long groupId;
    private String contentHash;
    private Integer sourceSeq;
    private LocalDateTime createdAt;
}
