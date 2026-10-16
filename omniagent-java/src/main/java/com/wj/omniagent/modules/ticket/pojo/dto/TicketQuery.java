package com.wj.omniagent.modules.ticket.pojo.dto;

import com.wj.omniagent.common.pojo.dto.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "工单分页查询参数")
public class TicketQuery extends PageQuery {

    @Schema(description = "搜索关键词（标题/工单号）", example = "蓝屏")
    private String keyword;

    @Schema(description = "工单状态ID", example = "1")
    private Integer statusId;

    @Schema(description = "工单优先级ID", example = "1")
    private Integer priorityId;

    @Schema(description = "工单分类ID", example = "1")
    private Integer categoryId;
}
