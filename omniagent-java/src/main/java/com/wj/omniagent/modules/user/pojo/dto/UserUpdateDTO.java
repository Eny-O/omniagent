package com.wj.omniagent.modules.user.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户请求 DTO
 * <p>
 * 不包含用户名和密码，用户名不可修改，密码由单独接口处理。
 */
@Data
@Schema(description = "更新用户请求参数")
public class UserUpdateDTO {

    @Size(max = 64, message = "真实姓名长度不能超过64")
    @Schema(description = "真实姓名", example = "张三")
    private String realName;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过128")
    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号", example = "13800000001")
    private String phone;

    @Schema(description = "部门ID", example = "1")
    private Long deptId;

    @Schema(description = "状态：1=启用，0=禁用", example = "1")
    private Integer status;
}
