package com.wj.omniagent.common.pojo.result;

import com.wj.omniagent.common.enums.ErrorCodeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应结果封装类
 * @param <T> 响应数据类型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponseResult<T> {
    @Schema(description = "状态码", example = "200")
    private String code;

    @Schema(description = "响应信息", example = "操作成功")
    private String message;

    @Schema(description = "响应数据")
    private T data;


    public ResponseResult(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 成功响应（携带数据）
     */
    public static <T> ResponseResult<T> success(T data) {
        return new ResponseResult<>(ErrorCodeEnum.SUCCESS.getCode(),ErrorCodeEnum.SUCCESS.getMessage(), data);
    }
    /**
     * 成功响应（自定义消息 + 数据）
     */
    public static <T> ResponseResult<T> success(String message,T data) {
        return new ResponseResult<>(ErrorCodeEnum.SUCCESS.getCode(), message, data);
    }

    /**
     * 成功响应（无数据）
     */
    public static ResponseResult<Void> success() {
        return new ResponseResult<>(ErrorCodeEnum.SUCCESS.getCode(), ErrorCodeEnum.SUCCESS.getMessage());
    }

    /**
     * 成功响应（自定义消息，无数据）
     */
    public static ResponseResult<Void> success(String message) {
        return new ResponseResult<>(ErrorCodeEnum.SUCCESS.getCode(), message);
    }


    /**
     * 失败响应（自定义消息，使用通用业务失败码）
     */
    public static <T> ResponseResult<T> error(String message) {
        return new ResponseResult<>(ErrorCodeEnum.OPERATION_FAILED.getCode(), message);
    }
    /**
     * 失败响应（自定义错误码，自定义消息）
     */
    public static <T> ResponseResult<T> error(String code,String message) {
        return new ResponseResult<>(code, message);
    }

    /**
     * 失败响应（指定错误码枚举）
     */
    public static <T> ResponseResult<T> error(ErrorCodeEnum errorCodeEnum) {
        return new ResponseResult<>(errorCodeEnum.getCode(), errorCodeEnum.getMessage());
    }

    /**
     * 失败响应（使用默认通用业务失败码）
     */
    public static ResponseResult<Void> error() {
        return new ResponseResult<>(ErrorCodeEnum.OPERATION_FAILED.getCode(), ErrorCodeEnum.OPERATION_FAILED.getMessage());
    }

    /**
     * 失败响应（自定义返回对象）
     */
    public static <T> ResponseResult<T> error(T data) {
        return new ResponseResult<>(ErrorCodeEnum.OPERATION_FAILED.getCode(),ErrorCodeEnum.OPERATION_FAILED.getMessage(), data);
    }

}
