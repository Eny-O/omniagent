package com.wj.omniagent.modules.ticket.pojo.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单操作历史表实体
 * <p>对应库表 ticket_history（权威定义见 omniagent_ticket_schema.sql 第 5 节）：
 * 记录工单的创建 / 字段变更 / 状态流转 / 派单 / 撤回 / 删除等操作，用于全程审计追溯。
 */
@Data
@TableName("ticket_history")
@Schema(description = "工单操作历史实体")
public class TicketHistory {

    @TableId(type = IdType.AUTO)
    @Schema(description = "历史记录ID", example = "1")
    private Long id;

    @Schema(description = "工单ID", example = "1")
    private Long ticketId;

    @Schema(description = "操作人ID（用户或代操作身份）", example = "3")
    private Long operatorId;

    @Schema(description = "操作类型：CREATE/UPDATE/ASSIGN/STATUS_CHANGE/WITHDRAW/DELETE", example = "STATUS_CHANGE")
    private String operation;

    @Schema(description = "变更字段名（UPDATE 时填入，如 status / priority_id）", example = "status")
    private String fieldName;

    @Schema(description = "变更前的值", example = "PENDING")
    private String oldValue;

    @Schema(description = "变更后的值", example = "PROCESSING")
    private String newValue;

    @Schema(description = "操作备注", example = "主管派单给一线小王")
    private String remark;

    @Schema(description = "操作时间")
    private LocalDateTime createdAt;
}
