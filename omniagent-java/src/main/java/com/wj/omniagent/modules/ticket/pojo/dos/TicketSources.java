package com.wj.omniagent.modules.ticket.pojo.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单来源字典实体
 * <p>
 * 对应表 ticket_sources。与分类/状态/优先级不同，来源采用「编码字典」：
 * tickets.source 直接存储 source_code（字符串），不另设外键 id 列，
 * 因此本字典主要作为前端下拉选项与 Agent 取值的权威来源。
 */
@Data
@TableName("ticket_sources")
@Schema(description = "工单来源字典实体")
public class TicketSources {

    @TableId(type = IdType.AUTO)
    @Schema(description = "来源ID")
    private Integer id;

    @Schema(description = "来源编码，如 WEB/APP/EMAIL/PHONE/AGENT", example = "WEB")
    private String sourceCode;

    @Schema(description = "来源名称，如 网页/移动端/智能助手", example = "网页")
    private String sourceName;

    @Schema(description = "排序号")
    private Integer sortOrder;

}
