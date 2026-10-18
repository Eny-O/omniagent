package com.wj.omniagent.modules.ticket.dict.controller;

import com.wj.omniagent.common.pojo.result.ResponseResult;
import com.wj.omniagent.modules.ticket.dict.service.DictService;
import com.wj.omniagent.modules.ticket.pojo.vo.dict.CategoryOptionVO;
import com.wj.omniagent.modules.ticket.pojo.vo.dict.DictGroupVO;
import com.wj.omniagent.modules.ticket.pojo.vo.dict.PriorityOptionVO;
import com.wj.omniagent.modules.ticket.pojo.vo.dict.SourceOptionVO;
import com.wj.omniagent.modules.ticket.pojo.vo.dict.StatusOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工单字典 Controller
 * <p>
 * 提供工单分类、状态、优先级的字典查询，用于前端下拉框动态加载，替换硬编码选项。
 */
@Tag(name = "工单字典", description = "工单分类/状态/优先级字典查询")
@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @Operation(summary = "查询全部字典", description = "一次性返回分类/状态/优先级，前端登录后批量加载")
    @GetMapping("/all")
    public ResponseResult<DictGroupVO> all() {
        return ResponseResult.success(dictService.getAllDicts());
    }

    @Operation(summary = "查询工单分类")
    @GetMapping("/categories")
    public ResponseResult<List<CategoryOptionVO>> categories() {
        return ResponseResult.success(dictService.listCategories());
    }

    @Operation(summary = "查询工单状态")
    @GetMapping("/statuses")
    public ResponseResult<List<StatusOptionVO>> statuses() {
        return ResponseResult.success(dictService.listStatuses());
    }

    @Operation(summary = "查询工单优先级")
    @GetMapping("/priorities")
    public ResponseResult<List<PriorityOptionVO>> priorities() {
        return ResponseResult.success(dictService.listPriorities());
    }

    @Operation(summary = "查询工单来源", description = "返回工单来源编码与名称（如 WEB/APP/EMAIL/PHONE/AGENT），用于来源下拉框")
    @GetMapping("/sources")
    public ResponseResult<List<SourceOptionVO>> sources() {
        return ResponseResult.success(dictService.listSources());
    }
}
