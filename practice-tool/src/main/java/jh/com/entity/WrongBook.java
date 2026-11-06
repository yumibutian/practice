package jh.com.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wrong_book")
public class WrongBook {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long questionId;
    private Long bankId;
    private Integer wrongCount;
    /** 连续答对次数。达到 quiz.wrong.mastered-streak（默认 3）视为掌握 */
    private Integer rightStreak;
    private Integer mastered;
    private LocalDateTime lastWrongAt;
    private LocalDateTime nextReviewAt;
}
