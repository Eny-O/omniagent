package com.wj.omniagent.modules.user.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新部门请求 DTO
 * <p>
 * 部门编码不可修改。
 */
@Data
@Schema(description = "更新部门请求参数")
public class DepartmentUpdateDTO {

    @Size(max = 128, message = "部门名称长度不能超过128")
    @Schema(description = "部门名称", example = "信息技术部")
    private String deptName;

    @Schema(description = "上级部门ID", example = "1")
    private Long parentId;

    @Schema(description = "部门位置/地址", example = "A栋3楼301")
    private String location;

    @Schema(description = "排序号", example = "0")
    private Integer sortOrder;
}
