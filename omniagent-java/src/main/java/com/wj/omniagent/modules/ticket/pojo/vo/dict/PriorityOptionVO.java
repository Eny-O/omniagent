package com.wj.omniagent.modules.ticket.pojo.vo.dict;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 工单优先级下拉选项
 */
@Data
@Schema(description = "工单优先级下拉选项")
public class PriorityOptionVO {

    @Schema(description = "优先级ID，作为 el-select 的 value")
    private Integer value;

    @Schema(description = "优先级名称，作为 el-select 的 label")
    private String label;

    @Schema(description = "响应 SLA（分钟）")
    private Integer responseSlaMinutes;

    @Schema(description = "解决 SLA（分钟）")
    private Integer resolveSlaMinutes;

    @Schema(description = "排序号")
    private Integer sortOrder;
}
