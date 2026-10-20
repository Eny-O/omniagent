package com.wj.omniagent.modules.ticket.pojo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    @Schema(description = "工单来源", example = "WEB")
    private String source;

    @Schema(description = "工单所属部门ID（受理部门）。不传则默认取创建人所属部门；可用于跨部门故障指定正确的受理部门", example = "6")
    private Long deptId;

    @Schema(description = "联系人电话", example = "13800000001")
    private String contactPhone;

    @Schema(description = "联系人邮箱", example = "user@example.com")
    private String contactEmail;

    @Schema(description = "位置", example = "A栋3楼301")
    private String location;

    @Schema(description = "预计解决时间", example = "2026-10-20 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expectedResolveAt;
}

