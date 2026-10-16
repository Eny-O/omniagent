package com.wj.omniagent.modules.user.pojo.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体
 */
@Data
@TableName("users")
@Schema(description = "系统用户实体")
public class Users {

    @TableId(type = IdType.AUTO)
    @Schema(description = "用户ID", example = "1")
    private Long id;

    @Schema(description = "用户名", example = "zhangsan")
    private String username;

    @Schema(description = "密码哈希", example = "$2a$10$...")
    private String passwordHash;

    @Schema(description = "真实姓名", example = "张三")
    private String realName;

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "手机号", example = "13800000001")
    private String phone;

    @Schema(description = "部门ID", example = "1")
    private Long deptId;

    @Schema(description = "状态：1=启用，0=禁用", example = "1")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
