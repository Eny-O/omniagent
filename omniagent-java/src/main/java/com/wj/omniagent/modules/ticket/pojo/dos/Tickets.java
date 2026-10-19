package com.wj.omniagent.modules.ticket.pojo.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单主表实体
 */
@Data
@TableName("tickets")
@Schema(description = "工单主表实体")
public class Tickets {
    @TableId(type = IdType.AUTO)
    @Schema(description = "工单ID", example = "1")
    private Long id;

    @Schema(description = "工单编号", example = "TKT-20261015-001")
    private String ticketNo;

    @Schema(description = "工单标题", example = "电脑蓝屏无法启动")
    private String title;

    @Schema(description = "工单描述", example = "开机后蓝屏，错误代码0x00001")
    private String description;

    @Schema(description = "工单分类ID", example = "1")
    private Integer categoryId;

    @Schema(description = "工单优先级ID", example = "1")
    private Integer priorityId;

    @Schema(description = "工单状态ID", example = "1")
    private Integer statusId;

    @Schema(description = "创建人ID", example = "1")
    private Long creatorId;

    @Schema(description = "当前处理人ID", example = "2")
    private Long currentAssigneeId;

    @Schema(description = "所属部门ID（用于主管按部门查看工单）", example = "1")
    private Long deptId;

    @Schema(description = "工单来源", example = "WEB")
    private String source;

    @Schema(description = "联系人电话", example = "13800000001")
    private String contactPhone;

    @Schema(description = "联系人邮箱", example = "user@example.com")
    private String contactEmail;

    @Schema(description = "位置", example = "A栋3楼301")
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
