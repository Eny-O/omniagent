package com.wj.omniagent.modules.ticket.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建工单请求 DTO
 */
@Data
@Schema(description = "创建工单请求参数")
public class TicketCreateDTO {

    @NotBlank(message = "工单标题不能为空")
    @Size(max = 255, message = "工单标题长度不能超过255")
    @Schema(description = "工单标题", example = "电脑蓝屏无法启动", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "工单描述", example = "开机后蓝屏，错误代码0x00001")
    private String description;

    @Schema(description = "工单分类ID", example = "1")
    private Integer categoryId;

    @Schema(description = "工单优先级ID", example = "1")
    private Integer priorityId;

    @Schema(description = "当前处理人ID", example = "2")
    private Long currentAssigneeId;

    @Schema(description = "工单来源", example = "WEB")
    private String source;

    @Schema(description = "联系人电话", example = "13800000001")
    private String contactPhone;

    @Schema(description = "联系人邮箱", example = "user@example.com")
    private String contactEmail;

    @Schema(description = "位置", example = "A栋3楼301")
    private String location;

    @Schema(description = "预计解决时间")
    private LocalDateTime expectedResolveAt;
}

