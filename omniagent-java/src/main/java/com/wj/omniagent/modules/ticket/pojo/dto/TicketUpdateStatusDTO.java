package com.wj.omniagent.modules.ticket.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新工单状态请求 DTO
 */
@Data
@Schema(description = "更新工单状态请求参数")
public class TicketUpdateStatusDTO {

    @NotBlank(message = "目标状态不能为空")
    @Schema(description = "目标状态编码", example = "PENDING", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetStatusCode;

    @Schema(description = "操作备注", example = "已分派给运维组处理")
    private String remark;
}
