package jh.com.doto;


import jh.com.parse.model.NormalizedQuestion;
import jh.com.parse.model.Warning;

import java.util.List;

// 解析任务查询视图
public record ParseTaskView(
    Long taskId,
    Integer status,
    String stage,
    Integer progress,
    Integer totalQuestions,
    Integer warningCount,
    List<NormalizedQuestion> preview,
    List<Warning> globalWarnings,
    String errorCode,
    String errorMsg
) {}
