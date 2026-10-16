package com.wj.omniagent.modules.ticket.pojo.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 工单优先级字典实体
 * <p>
 * 对应表 ticket_priorities，含 SLA 指标。
 */
@Data
@TableName("ticket_priorities")
@Schema(description = "工单优先级字典实体")
public class TicketPriorities {

    @TableId(type = IdType.AUTO)
    @Schema(description = "优先级ID")
    private Integer id;

    @Schema(description = "优先级编码", example = "HIGH")
    private String priorityCode;

    @Schema(description = "优先级名称", example = "高")
    private String priorityName;

    @Schema(description = "响应 SLA（分钟）")
    private Integer responseSlaMinutes;

    @Schema(description = "解决 SLA（分钟）")
    private Integer resolveSlaMinutes;

    @Schema(description = "排序号")
    private Integer sortOrder;
}
