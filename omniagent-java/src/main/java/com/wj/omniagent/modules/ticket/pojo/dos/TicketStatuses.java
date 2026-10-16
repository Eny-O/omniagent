package com.wj.omniagent.modules.ticket.pojo.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 工单状态字典实体
 * <p>
 * 对应表 ticket_statuses。
 */
@Data
@TableName("ticket_statuses")
@Schema(description = "工单状态字典实体")
public class TicketStatuses {

    @TableId(type = IdType.AUTO)
    @Schema(description = "状态ID")
    private Integer id;

    @Schema(description = "状态编码", example = "PENDING")
    private String statusCode;

    @Schema(description = "状态名称", example = "待处理")
    private String statusName;

    @Schema(description = "是否为终态，如已关闭")
    private Boolean isTerminal;

    @Schema(description = "排序号")
    private Integer sortOrder;
}
