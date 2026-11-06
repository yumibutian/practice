package jh.com.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("practice_detail")
public class PracticeDetail {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recordId;
    private Long questionId;
    private Integer seq;
    private String userAnswerJson;
    private Integer isCorrect;
    private Integer scoreGot;
    private Integer durationMs;
}
