package com.wj.omniagent.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wj.omniagent.common.enums.ErrorCodeEnum;
import com.wj.omniagent.common.security.JwtUtil;
import com.wj.omniagent.common.utils.SecurityUtils;
import com.wj.omniagent.exception.BusinessException;
import com.wj.omniagent.modules.user.mapper.UserMapper;
import com.wj.omniagent.modules.user.pojo.dos.Users;
import com.wj.omniagent.modules.user.pojo.dto.LoginDTO;
import com.wj.omniagent.modules.user.pojo.vo.LoginUserVO;
import com.wj.omniagent.modules.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDateTime;

/**
 * 认证服务实现（无状态 JWT 方案）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;

    private final UserMapper userMapper;

    private final JwtUtil jwtUtil;

    @Override
    public LoginUserVO login(LoginDTO dto) {
        Assert.notNull(dto, "登录请求不能为空");
        Assert.hasText(dto.getUsername(), "用户名不能为空");
        Assert.hasText(dto.getPassword(), "密码不能为空");

        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(dto.getUsername().trim(), dto.getPassword());
        try {
            authenticationManager.authenticate(token);
            // 认证通过：签发 JWT（无状态，不创建 Session）
            LoginUserVO vo = buildLoginUserVO(dto.getUsername().trim());
            String jwt = jwtUtil.generateToken(vo.getId(), vo.getUsername(), vo.getRole(), vo.getDeptId());
            vo.setToken(jwt);
            log.info("用户登录成功，username={}", dto.getUsername());
            return vo;
        } catch (BadCredentialsException e) {
            log.warn("登录密码错误，username={}", dto.getUsername());
            throw new BusinessException(ErrorCodeEnum.USER_PASSWORD_ERROR, "用户名或密码错误");
        } catch (AuthenticationException e) {
            log.warn("登录认证失败，username={}，原因={}", dto.getUsername(), e.getMessage());
            throw new BusinessException(ErrorCodeEnum.USER_PASSWORD_ERROR, "用户名或密码错误");
        }
    }

    @Override
    public void logout() {
        // 无状态方案：服务端不保存会话，登出由前端删除本地 token 完成。
        // 如需服务端主动失效，需引入 JWT 黑名单（T6 可选）。
    }

    @Override
    public LoginUserVO getCurrentUser() {
        String username = SecurityUtils.getCurrentUsername();
        if (username == null) {
            return null;
        }
        return buildLoginUserVO(username);
    }

    private LoginUserVO buildLoginUserVO(String username) {
        LambdaQueryWrapper<Users> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Users::getUsername, username);
        Users user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }

        LoginUserVO vo = new LoginUserVO();
        BeanUtils.copyProperties(user, vo);
        vo.setLoginAt(LocalDateTime.now());
        return vo;
    }
}
