package com.wj.omniagent.modules.ticket.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 工单派单请求 DTO
 */
@Data
@Schema(description = "工单派单请求参数")
public class TicketAssignDTO {

    @NotNull(message = "被指派处理人不能为空")
    @Schema(description = "被指派处理人ID（HANDLER）", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long assigneeId;

    @Schema(description = "优先级终审（可选）：支持侧在派单时确认/调整报障人申报的优先级；不传则沿用原优先级", example = "1")
    private Long priorityId;

    @Schema(description = "派单备注", example = "请运维组优先处理")
    private String remark;
}
