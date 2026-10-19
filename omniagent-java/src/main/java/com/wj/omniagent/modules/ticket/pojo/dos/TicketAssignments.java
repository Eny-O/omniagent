package com.wj.omniagent.modules.ticket.pojo.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单分配记录表实体
 * <p>对应库表 ticket_assignments（权威定义见 omniagent_ticket_schema.sql 第 4 节）：
 * 每次派单 / 转派都会落一条记录；通过 is_current 标记当前有效派单，历史派单 is_current=false 保留可追溯。
 */
@Data
@TableName("ticket_assignments")
@Schema(description = "工单分配记录实体")
public class TicketAssignments {

    @TableId(type = IdType.AUTO)
    @Schema(description = "分配记录ID", example = "1")
    private Long id;

    @Schema(description = "工单ID", example = "1")
    private Long ticketId;

    @Schema(description = "指派人ID（通常为 SUPERVISOR / ADMIN）", example = "3")
    private Long assignorId;

    @Schema(description = "被指派处理人ID（HANDLER）", example = "2")
    private Long assigneeId;

    @Schema(description = "分配时间")
    private LocalDateTime assignedAt;

    @Schema(description = "是否为当前有效分派（转派时旧记录置 false，新记录置 true）", example = "true")
    private Boolean isCurrent;

    @Schema(description = "派单备注", example = "请运维组优先处理")
    private String remark;
}
