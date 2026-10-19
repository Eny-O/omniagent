package com.wj.omniagent.modules.ticket.pojo.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单评论 / 备注实体
 * <p>对应库表 ticket_comments（权威定义见 omniagent_ticket_schema.sql 第 4 节关联表）：
 * 承载工单的处理内容、沟通记录与内部备注。
 * author_type 取值：USER=用户, OPERATOR=处理人, SYSTEM=系统；
 * is_internal=TRUE 表示内部备注，仅处理人 / 主管 / 管理员可见，报障人不可见。
 */
@Data
@TableName("ticket_comments")
@Schema(description = "工单评论/备注实体")
public class TicketComments {

    @TableId(type = IdType.AUTO)
    @Schema(description = "评论ID", example = "1")
    private Long id;

    @Schema(description = "工单ID", example = "1")
    private Long ticketId;

    @Schema(description = "作者用户ID（用户或代操作身份）", example = "3")
    private Long authorId;

    @Schema(description = "作者类型：USER=用户, OPERATOR=处理人, SYSTEM=系统", example = "OPERATOR")
    private String authorType;

    @Schema(description = "评论内容", example = "已联系用户，预计今天上门处理")
    private String content;

    @Schema(description = "是否内部备注（TRUE 仅处理人/主管/管理员可见，报障人不可见）", example = "false")
    private Boolean isInternal;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
