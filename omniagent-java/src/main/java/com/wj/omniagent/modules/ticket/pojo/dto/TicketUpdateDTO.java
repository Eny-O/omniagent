package com.wj.omniagent.modules.ticket.pojo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 更新工单请求 DTO
 */
@Data
@Schema(description = "更新工单请求参数")
public class TicketUpdateDTO {

    @Size(max = 255, message = "工单标题长度不能超过255")
    @Schema(description = "工单标题", example = "电脑蓝屏无法启动")
    private String title;

    @Schema(description = "工单描述", example = "开机后蓝屏，错误代码0x00001")
    private String description;

    @Schema(description = "工单分类ID", example = "1")
    private Integer categoryId;

    @Schema(description = "工单优先级ID", example = "1")
    private Integer priorityId;

    @Schema(description = "当前处理人ID", example = "2")
    private Long currentAssigneeId;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联系人电话格式不正确")
    @Schema(description = "联系人电话", example = "13800000001")
    private String contactPhone;

    @Email(message = "联系人邮箱格式不正确")
    @Schema(description = "联系人邮箱", example = "user@example.com")
    private String contactEmail;

    @Size(max = 255, message = "位置长度不能超过255")
    @Schema(description = "位置", example = "A栋3楼301")
    private String location;

    @Schema(description = "预计解决时间", example = "2026-10-20 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expectedResolveAt;
}
