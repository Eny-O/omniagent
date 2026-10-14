package com.wj.omniagent.common.pojo.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 分页结果对象
 */
@Data
@Schema(description = "通用分页返回结果")
public class PageResult<T> {

    @Schema(description = "总记录数", example = "100")
    private Long total;

    @Schema(description = "当前页码", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize;

    @Schema(description = "当前页数据列表")
    private List<T> records;

    /**
     * 快速构建分页结果对象的静态方法
     */
    public static <T> PageResult<T> of(Long total, List<T> records, Integer pageNum, Integer pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setRecords(records);
        return result;
    }
}
