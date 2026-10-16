package com.wj.omniagent.modules.ticket.pojo.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单分类字典实体
 * <p>
 * 对应表 ticket_categories，支持树形结构（parent_id 自关联）。
 */
@Data
@TableName("ticket_categories")
@Schema(description = "工单分类字典实体")
public class TicketCategories {

    @TableId(type = IdType.AUTO)
    @Schema(description = "分类ID")
    private Integer id;

    @Schema(description = "分类编码", example = "HARDWARE")
    private String categoryCode;

    @Schema(description = "分类名称", example = "硬件故障")
    private String categoryName;

    @Schema(description = "上级分类ID，null 表示一级分类")
    private Integer parentId;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
