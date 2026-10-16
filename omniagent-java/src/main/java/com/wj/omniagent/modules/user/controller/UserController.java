package com.wj.omniagent.modules.user.controller;

import com.wj.omniagent.common.pojo.result.PageResult;
import com.wj.omniagent.common.pojo.result.ResponseResult;
import com.wj.omniagent.modules.user.pojo.dto.UserCreateDTO;
import com.wj.omniagent.modules.user.pojo.dto.UserQuery;
import com.wj.omniagent.modules.user.pojo.dto.UserUpdateDTO;
import com.wj.omniagent.modules.user.pojo.vo.UserPageVO;
import com.wj.omniagent.modules.user.pojo.vo.UserVO;
import com.wj.omniagent.modules.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理 Controller
 */
@Tag(name = "用户管理", description = "系统用户 CRUD 接口")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    @Operation(summary = "创建用户")
    @PostMapping
    public ResponseResult<UserVO> create(@RequestBody @Valid UserCreateDTO dto) {
        UserVO vo = userService.createUser(dto);
        return ResponseResult.success(vo);
    }

    @Operation(summary = "查询用户详情")
    @GetMapping("/{id}")
    public ResponseResult<UserVO> getById(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        return ResponseResult.success(userService.getUserById(id));
    }

    @Operation(summary = "分页查询用户列表")
    @GetMapping("/page")
    public ResponseResult<PageResult<UserPageVO>> page(UserQuery query) {
        return ResponseResult.success(userService.pageUsers(query));
    }

    @Operation(summary = "更新用户信息")
    @PutMapping("/{id}")
    public ResponseResult<UserVO> update(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @RequestBody @Valid UserUpdateDTO dto) {
        UserVO vo = userService.updateUser(id, dto);
        return ResponseResult.success(vo);
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public ResponseResult<Void> delete(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseResult.success();
    }
}
