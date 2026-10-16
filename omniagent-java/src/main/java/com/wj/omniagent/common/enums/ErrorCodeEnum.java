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

    // 工单错误码
    TICKET_GENER_FAILED_RETRY("2000", "工单号生成失败，请重试"),
    TICKET_NOT_FOUND("2001", "工单不存在"),
    TICKET_STATUS_NOT_FOUND("2002", "工单目标状态不存在"),
    TICKET_STATUS_INVALID_TRANSITION("2003", "工单状态流转不合法"),
    TICKET_STATUS_ERROR("2004", "工单状态异常"),
    TICKET_CLOSED_NOT_UPDATE("2005", "工单已关闭，不允许修改"),

    // 文件与导入错误码
    FILE_FORMAT_ERROR("4001", "仅支持 .xlsx 格式文件"),
    FILE_EMPTY("4002", "上传文件为空"),
    FILE_PARSE_ERROR("4003", "文件解析失败，请检查文件内容"),
    FILE_CONTENT_REQUIRED_FIELD_EMPTY("4004", "文件内容有必填项为空"),

    // 并发修改错误码
    DATA_ALREADY_MODIFIED_PLEASE_FLUSH_AFTER_RETRY("8001", "数据已经被更改,请刷新后重试");

    private final String code;
    private final String message;
}
