package com.wj.omniagent.modules.ticket.pojo.vo.dict;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 工单来源下拉选项
 * <p>
 * 与分类/状态/优先级不同，本 VO 的 value 为「来源编码字符串」（如 WEB/AGENT），
 * 直接对应 tickets.source 列的取值，方便前端 el-select 与创建工单参数对齐。
 */
@Data
@Schema(description = "工单来源下拉选项")
public class SourceOptionVO {

    @Schema(description = "来源编码，作为 el-select 的 value，与 tickets.source 一致", example = "WEB")
    private String value;

    @Schema(description = "来源名称，作为 el-select 的 label", example = "网页")
    private String label;

    @Schema(description = "排序号")
    private Integer sortOrder;
}
