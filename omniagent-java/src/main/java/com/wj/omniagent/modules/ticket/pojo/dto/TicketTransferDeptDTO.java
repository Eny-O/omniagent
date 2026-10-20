package com.wj.omniagent.modules.ticket.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 工单转部门请求 DTO
 */
@Data
@Schema(description = "工单转部门请求参数")
public class TicketTransferDeptDTO {

    @NotNull(message = "目标部门ID不能为空")
    @Schema(description = "目标受理部门ID", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long newDeptId;
}
