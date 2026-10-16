package com.wj.omniagent.modules.ticket.pojo.vo.dict;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 工单分类下拉选项
 * <p>
 * 前端 el-select 直接使用：value = 分类ID，label = 分类名称。
 */
@Data
@Schema(description = "工单分类下拉选项")
public class CategoryOptionVO {

    @Schema(description = "分类ID，作为 el-select 的 value")
    private Integer value;

    @Schema(description = "分类名称，作为 el-select 的 label")
    private String label;

    @Schema(description = "上级分类ID，null 为一级分类，可用于树形展示")
    private Integer parentId;

    @Schema(description = "排序号")
    private Integer sortOrder;
}
