package com.wj.omniagent.common.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分页查询基础参数
 * <p>
 * 其他分页查询 DTO 可继承此类，统一提供 pageNum 和 pageSize 字段。
 */
@Data
@Schema(description = "分页查询请求参数")
public class PageQuery {
    @Schema(description = "当前页码，默认第1页", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页显示条数，默认10条", example = "10")
    private Integer pageSize = 10;
}
