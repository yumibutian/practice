package jh.com.common;

import lombok.Getter;

/**
 * 错误码
 */
@Getter
public enum ErrorCode {
    // 通用
    INTERNAL_ERROR("服务异常，请稍后重试"),
    PARAM_INVALID("参数不合法"),
    UNAUTHORIZED("登录已过期，请重新登录"),
    FORBIDDEN("无权访问该资源"),
    NOT_FOUND("资源不存在"),
    CONFLICT("操作冲突，请刷新后重试"),

    // 文件
    FILE_TOO_LARGE("单个文件请小于 20MB"),
    FILE_EXT_UNSUPPORTED("不支持的文件类型"),
    FILE_ENCRYPTED("文件已加密，请先解密"),
    FILE_EMPTY("文件内容为空"),
    STORAGE_ERROR("文件存储异常"),

    // 解析
    PARSE_FAILED("解析失败"),
    SECTION_UNANCHORED("未识别到题目结构"),
    SCAN_NEED_OCR("未检测到文字层，暂不支持扫描件"),
    ANCHORS_LOW_COVERAGE("题目切分置信度偏低"),
    ENCODING_UNKNOWN("文件编码无法识别"),
    PLACEHOLDER_LOST("公式占位符丢失，解析结果不可信"),
    MAX_QUESTIONS_EXCEEDED("题目数量超过上限（2000 题）"),

    // 解析任务
    TASK_NOT_FOUND("解析任务不存在"),
    TASK_ALREADY_RUNNING("已有解析任务进行中，请等待完成"),
    TASK_BUSY("系统繁忙，请稍后重试"),
    TASK_NOT_COMMITTABLE("当前任务状态不可入库"),

    // 题库
    BANK_NOT_FOUND("题库不存在"),
    BANK_EMPTY("该题库还没有题目"),

    // 练习与考试
    PRACTICE_NOT_FOUND("练习记录不存在"),
    PRACTICE_ALREADY_SUBMITTED("本次练习已提交"),
    CONFIG_INVALID("考试配置不合法"),
    WRONG_SOURCE_EMPTY("没有可重练的错题");

    private final String defaultMessage;

    ErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }
}
