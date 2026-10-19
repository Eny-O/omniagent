package com.wj.omniagent.modules.user.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录成功返回的用户信息 VO
 */
@Data
@Schema(description = "登录用户信息 VO")
public class LoginUserVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "角色：REPORTER/HANDLER/SUPERVISOR/ADMIN")
    private String role;

    @Schema(description = "状态：1=启用，0=禁用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "登录时间")
    private LocalDateTime loginAt;
}
