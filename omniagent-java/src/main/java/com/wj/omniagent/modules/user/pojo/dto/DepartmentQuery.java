package com.wj.omniagent.modules.user.pojo.dto;

import com.wj.omniagent.common.pojo.dto.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 部门分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "部门分页查询参数")
public class DepartmentQuery extends PageQuery {

    @Schema(description = "搜索关键词（部门编码/部门名称）", example = "IT")
    private String keyword;

    @Schema(description = "上级部门ID", example = "1")
    private Long parentId;
}
