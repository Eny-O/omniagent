package com.wj.omniagent.modules.user.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建部门请求 DTO
 */
@Data
@Schema(description = "创建部门请求参数")
public class DepartmentCreateDTO {

    @NotBlank(message = "部门编码不能为空")
    @Size(max = 64, message = "部门编码长度不能超过64")
    @Schema(description = "部门编码", example = "DEPT_IT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deptCode;

    @NotBlank(message = "部门名称不能为空")
    @Size(max = 128, message = "部门名称长度不能超过128")
    @Schema(description = "部门名称", example = "信息技术部", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deptName;

    @Schema(description = "上级部门ID", example = "1")
    private Long parentId;

    @Schema(description = "排序号，默认0", example = "0")
    private Integer sortOrder;
}
