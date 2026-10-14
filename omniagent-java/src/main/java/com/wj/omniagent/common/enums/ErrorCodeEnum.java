package com.wj.omniagent.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举类
 */
@Getter
@AllArgsConstructor
public enum ErrorCodeEnum {
    // HTTP 标准状态码
    SUCCESS("200", "操作成功"),
    BAD_REQUEST("400", "请求参数错误"),
    UNAUTHORIZED("401", "认证失败"),
    FORBIDDEN("403", "权限不足"),
    NOT_FOUND("404", "资源不存在"),
    INTERNAL_ERROR("500", "服务器内部错误"),

    // 业务错误码
    OPERATION_FAILED("1000", "操作失败"),
    USER_NOT_FOUND("1001", "用户不存在"),
    USER_ALREADY_EXISTS("1002", "用户已存在"),
    USER_PASSWORD_ERROR("1003", "密码错误"),
    USER_NOT_LOGIN("1004", "用户未登录"),
    PERMISSION_DENIED("1005", "无权限"),
    ACCOUNT_DISABLED("1006", "账号已被禁用"),
    PLEASE_FLUSH_AFTER_RETRY("1007", "请刷新页面后重试"),
    PARAM_ERROR("1008", "传入参数错误"),

    // 题目错误码
    QUESTION_NOT_EXIST("2001", "题目不存在"),
    QUESTION_REFERENCE_PAPER("2002", "题目关联了试卷"),
    QUESTION_OPTIONS_NOT_EMPTY("2003", "题目选项不能为空"),
    QUESTION_OPTIONS_EXCEED_SIX("2004", "题目选项超过6个"),
    QUESTION_OPTION_LABEL_ERROR("2005", "题目选项标签错误"),
    JUDGE_MUST_HAVE_TWO_OPTIONS("2006", "判断只能有两个选项"),
    QUESTION_TYPE_ERROR("2007",  "题目类型错误"),
    QUESTION_OPTIONS_LABEL_ERROR("2008", "无效的选项标签"),
    QUESTION_OPTIONS_AT_LEAST_TWO("2009","题目选项至少两个"),

    // 考试错误码
    EXAM_NOT_FOUND("3001", "考试不存在"),
    NOT_ADD_ENABLE_PAPER("3002","不能添加未启用的试卷"),
    EXAM_STATUS_CHANGE_FAIL("3003","考试状态改变失败"),
    EXAM_STATUS_NOT_PENDING_NOT_SUBMIT("3004", "考试状态不是待考试不能提交"),
    EXAM_STUDENT_NOT_FOUND("3005", "考试学生关联数据不存在"),
    EXAM_NOT_ALL_GRADED("3006", "考试并没有全部阅卷完成"),
    EXAM_STATUS_NOT_GRADED("3007", "考试状态不是已阅卷不能查看试卷结果"),
    EXAM_REFERENCE_PAPER_NOT_FOUND("3008", "考试关联的试卷不存在"),

    // 文件与导入错误码
    FILE_FORMAT_ERROR("4001", "仅支持 .xlsx 格式文件"),
    FILE_EMPTY("4002", "上传文件为空"),
    FILE_PARSE_ERROR("4003", "文件解析失败，请检查文件内容"),
    FILE_CONTENT_REQUIRED_FIELD_EMPTY("4004", "文件内容有必填项为空"),

    // 试卷错误码
    PAPER_NOT_EMPTY("5001", "试卷不能为空"),
    PAPER_NOT_EXIST("5002","试卷不存在"),
    PAPER_ALREADY_ENABLE_NOT_UPDATE("5003",  "试卷已启用不能更改"),
    PAPER_ALREADY_ENABLE_NOT_DELETE("5004", "试卷已启用不能删除"),
    PAPER_STATUS_TRANSFER_NOT_ALLOWED("5005", "试卷状态转换不允许"),
    PAPER_REFERENCE_ANY_EXAM("5006", "试卷已被考试引用"),
    PAPER_STATUS_CHANGE_FAIL("5007","试卷状态改变失败"),
    STUDENT_SUBMIT_PAPER_FAILED("5008", "学生提交试卷失败"),
    NOT_SUBMIT_EMPTY_PAPER("5009", "不能提交空试卷"),

    // 班级错误码
    CLASS_NOT_FOUND("6001", "班级不存在"),

    // 阅卷错误码
    PAPER_CONTENT_EMPTY_NOT_GRADE("7001", "试卷内容为空，不能阅卷"),
    PAPER_STATUS_ERROR_NOT_GRADE("7002", "试卷状态错误，不能阅卷"),
    EXAM_ANSWER_NOT_FOUND_NOT_AUTO_GRADE("7003", "考试答案不存在,不能自动阅卷"),



    // 并发修改错误码
    DATA_ALREADY_MODIFIED_PLEASE_FLUSH_AFTER_RETRY("8001", "数据已经被更改,请刷新后重试");

    private final String code;
    private final String message;
}
