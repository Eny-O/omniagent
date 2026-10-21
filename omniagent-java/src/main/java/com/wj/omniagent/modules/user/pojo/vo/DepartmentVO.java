package com.wj.omniagent.modules.user.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门详情 VO
 */
@Data
@Schema(description = "部门详情 VO")
public class DepartmentVO {

    @Schema(description = "部门ID")
    private Long id;

    @Schema(description = "部门编码")
    private String deptCode;

    @Schema(description = "部门名称")
    private String deptName;

    @Schema(description = "上级部门ID")
    private Long parentId;

    @Schema(description = "部门位置/地址")
    private String location;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
