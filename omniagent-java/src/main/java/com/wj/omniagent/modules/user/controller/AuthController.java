package com.wj.omniagent.modules.user.controller;

import com.wj.omniagent.common.pojo.result.ResponseResult;
import com.wj.omniagent.modules.user.pojo.dto.LoginDTO;
import com.wj.omniagent.modules.user.pojo.vo.LoginUserVO;
import com.wj.omniagent.modules.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 认证相关接口
 */
@Tag(name = "认证管理", description = "登录、登出、当前用户信息")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public ResponseResult<LoginUserVO> login(@RequestBody @Valid LoginDTO dto) {
        LoginUserVO vo = authService.login(dto);
        return ResponseResult.success(vo);
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public ResponseResult<Void> logout() {
        authService.logout();
        return ResponseResult.success();
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/me")
    public ResponseResult<LoginUserVO> me() {
        LoginUserVO vo = authService.getCurrentUser();
        if (vo == null) {
            return ResponseResult.error(com.wj.omniagent.common.enums.ErrorCodeEnum.USER_NOT_LOGIN);
        }
        return ResponseResult.success(vo);
    }
}
