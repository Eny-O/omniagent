package com.wj.omniagent.modules.ticket.pojo.vo.dict;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 工单状态下拉选项
 */
@Data
@Schema(description = "工单状态下拉选项")
public class StatusOptionVO {

    @Schema(description = "状态ID，作为 el-select 的 value")
    private Integer value;

    @Schema(description = "状态名称，作为 el-select 的 label")
    private String label;

    @Schema(description = "是否为终态（如已关闭）")
    private Boolean isTerminal;

    @Schema(description = "排序号")
    private Integer sortOrder;
}
