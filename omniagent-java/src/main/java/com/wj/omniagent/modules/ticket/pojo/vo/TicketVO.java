package com.wj.omniagent.modules.ticket.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单详情 VO
 */
@Data
@Schema(description = "工单详情 VO")
public class TicketVO {

    @Schema(description = "工单ID")
    private Long id;

    @Schema(description = "工单编号")
    private String ticketNo;

    @Schema(description = "工单标题")
    private String title;

    @Schema(description = "工单描述")
    private String description;

    @Schema(description = "工单分类ID")
    private Integer categoryId;

    @Schema(description = "工单优先级ID")
    private Integer priorityId;

    @Schema(description = "工单状态ID")
    private Integer statusId;

    @Schema(description = "创建人ID")
    private Long creatorId;

    @Schema(description = "当前处理人ID")
    private Long currentAssigneeId;

    @Schema(description = "工单来源")
    private String source;

    @Schema(description = "联系人电话")
    private String contactPhone;

    @Schema(description = "联系人邮箱")
    private String contactEmail;

    @Schema(description = "位置")
    private String location;

    @Schema(description = "预计解决时间")
    private LocalDateTime expectedResolveAt;

    @Schema(description = "实际解决时间")
    private LocalDateTime resolvedAt;

    @Schema(description = "关闭时间")
    private LocalDateTime closedAt;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
