package com.wj.omniagent.common.pojo.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Schema(description = "批量插入的结果展示对象")
public class BatchInsertResult {

    @Schema(description = "总条数")
    private int totalCount;

    @Schema(description = "成功条数")
    private int successCount;

    @Schema(description = "失败条数")
    private int failCount;

    @Schema(description = "失败详情")
    private List<String> failMessages;
}