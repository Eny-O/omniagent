package com.wj.omniagent.modules.user.pojo.dto;

import com.wj.omniagent.common.pojo.dto.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户分页查询参数")
public class UserQuery extends PageQuery {

    @Schema(description = "搜索关键词（用户名/真实姓名）", example = "zhangsan")
    private String keyword;

    @Schema(description = "部门ID", example = "1")
    private Long deptId;

    @Schema(description = "状态：1=启用，0=禁用", example = "1")
    private Integer status;
}
