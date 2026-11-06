package jh.com.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("practice_record")
public class PracticeRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private Long bankId;
    /** practice / exam —— 统一小写，与 DDL 注释保持一致 */
    private String mode;
    /**
     * 抽题范围 ALL / RANDOM / WRONG，取值同 {@link com.quiz.dto.ExamConfig.Scope}。
     *
     * <p>考试的范围同时存在于 {@code config_json}（那里是**快照**，还要供报告算分），
     * 这一列只为「按范围圈记录」而存在 —— 见 V6 迁移的注释：续练与收敛两处查询
     * 都必须区分「全量练习」与「错题练习」，否则两者的断点会互相覆盖。
     */
    private String scope;
    private String configJson;
    private Integer durationLimitSec;
    private Integer total;
    private Integer correct;
    private Integer score;
    private Integer fullScore;
    private Long durationMs;
    private Integer status;
    /**
     * 断点进度快照（JSON 原文），仅练习模式写，考试模式恒为 NULL。
     * <p>用 String 而不是实体类去映射：后端只做「存下来 / 原样取回去」两件事，
     * 不需要理解 {@code current} / {@code answers} 的内部结构。一旦在这里建 DTO，
     * 端侧每次调整进度字段都要连着改后端 —— 而这个结构的真正主人是端侧。
     */
    private String progressJson;
    private LocalDateTime startedAt;
    private LocalDateTime deadlineAt;
    private LocalDateTime submittedAt;
}
