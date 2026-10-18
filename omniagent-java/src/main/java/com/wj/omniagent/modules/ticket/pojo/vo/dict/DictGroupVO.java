package com.wj.omniagent.modules.ticket.pojo.vo.dict;

import com.wj.omniagent.modules.ticket.pojo.vo.dict.SourceOptionVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 工单全部字典分组
 * <p>
 * 前端登录后一次请求即可拿到所有下拉选项，避免多次请求。
 */
@Data
@Schema(description = "工单全部字典分组")
public class DictGroupVO {

    @Schema(description = "分类列表")
    private List<CategoryOptionVO> categories;

    @Schema(description = "状态列表")
    private List<StatusOptionVO> statuses;

    @Schema(description = "优先级列表")
    private List<PriorityOptionVO> priorities;

    @Schema(description = "来源列表")
    private List<SourceOptionVO> sources;
}
