package com.wj.omniagent.exception;


import com.wj.omniagent.common.enums.ErrorCodeEnum;
import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BusinessException extends RuntimeException{
    private final String errorCode;

    public BusinessException(ErrorCodeEnum errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode.getCode();
    }

    // 也支持动态消息，比如"题目XXX不存在"
    public BusinessException(ErrorCodeEnum errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode.getCode();
    }
}
