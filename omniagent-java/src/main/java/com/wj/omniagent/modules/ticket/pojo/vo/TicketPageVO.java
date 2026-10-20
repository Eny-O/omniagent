package com.wj.omniagent.modules.ticket.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单列表项 VO
 * <p>
 * 列表页字段比详情页精简，后续可扩展分类/优先级/状态名称等展示字段。
 */
@Data
@Schema(description = "工单列表项 VO")
public class TicketPageVO {

    @Schema(description = "工单ID")
    private Long id;

    @Schema(description = "创建人ID")
    private Long creatorId;

    @Schema(description = "工单编号")
    private String ticketNo;

    @Schema(description = "工单标题")
    private String title;

    @Schema(description = "工单分类ID")
    private Integer categoryId;

    @Schema(description = "工单优先级ID")
    private Integer priorityId;

    @Schema(description = "工单状态ID")
    private Integer statusId;

    @Schema(description = "当前处理人ID")
    private Long currentAssigneeId;

    @Schema(description = "工单来源")
    private String source;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
