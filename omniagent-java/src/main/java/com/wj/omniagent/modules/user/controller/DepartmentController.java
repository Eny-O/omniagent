package com.wj.omniagent.modules.user.controller;

import com.wj.omniagent.common.pojo.result.PageResult;
import com.wj.omniagent.common.pojo.result.ResponseResult;
import com.wj.omniagent.modules.user.pojo.dto.DepartmentCreateDTO;
import com.wj.omniagent.modules.user.pojo.dto.DepartmentQuery;
import com.wj.omniagent.modules.user.pojo.dto.DepartmentUpdateDTO;
import com.wj.omniagent.modules.user.pojo.vo.DepartmentPageVO;
import com.wj.omniagent.modules.user.pojo.vo.DepartmentVO;
import com.wj.omniagent.modules.user.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门管理 Controller
 */
@Tag(name = "部门管理", description = "部门 CRUD 接口")
@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
@Validated
public class DepartmentController {

    private final DepartmentService departmentService;

    @Operation(summary = "创建部门")
    @PostMapping
    public ResponseResult<DepartmentVO> create(@RequestBody @Valid DepartmentCreateDTO dto) {
        DepartmentVO vo = departmentService.createDepartment(dto);
        return ResponseResult.success(vo);
    }

    @Operation(summary = "查询部门详情")
    @GetMapping("/{id}")
    public ResponseResult<DepartmentVO> getById(
            @Parameter(description = "部门ID") @PathVariable Long id) {
        return ResponseResult.success(departmentService.getDepartmentById(id));
    }

    @Operation(summary = "分页查询部门列表")
    @GetMapping("/page")
    public ResponseResult<PageResult<DepartmentPageVO>> page(DepartmentQuery query) {
        return ResponseResult.success(departmentService.pageDepartments(query));
    }

    @Operation(summary = "查询所有部门（树形结构用）")
    @GetMapping("/tree")
    public ResponseResult<List<DepartmentVO>> listAll() {
        return ResponseResult.success(departmentService.listAllDepartments());
    }

    @Operation(summary = "更新部门信息")
    @PutMapping("/{id}")
    public ResponseResult<DepartmentVO> update(
            @Parameter(description = "部门ID") @PathVariable Long id,
            @RequestBody @Valid DepartmentUpdateDTO dto) {
        DepartmentVO vo = departmentService.updateDepartment(id, dto);
        return ResponseResult.success(vo);
    }

    @Operation(summary = "删除部门")
    @DeleteMapping("/{id}")
    public ResponseResult<Void> delete(
            @Parameter(description = "部门ID") @PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return ResponseResult.success();
    }
}
