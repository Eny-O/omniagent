package com.wj.omniagent.modules.user.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建用户请求 DTO
 */
@Data
@Schema(description = "创建用户请求参数")
public class UserCreateDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过64")
    @Schema(description = "用户名", example = "zhangsan", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度必须在6-64之间")
    @Schema(description = "密码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

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

    @Schema(description = "状态：1=启用，0=禁用，默认1", example = "1")
    private Integer status;
}
