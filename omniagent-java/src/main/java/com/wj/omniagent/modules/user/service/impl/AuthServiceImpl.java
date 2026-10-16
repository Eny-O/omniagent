package com.wj.omniagent.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wj.omniagent.common.enums.ErrorCodeEnum;
import com.wj.omniagent.common.utils.SecurityUtils;
import com.wj.omniagent.exception.BusinessException;
import com.wj.omniagent.modules.user.mapper.UserMapper;
import com.wj.omniagent.modules.user.pojo.dos.Users;
import com.wj.omniagent.modules.user.pojo.dto.LoginDTO;
import com.wj.omniagent.modules.user.pojo.vo.LoginUserVO;
import com.wj.omniagent.modules.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDateTime;

/**
 * 认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;

    private final UserMapper userMapper;

    private final HttpServletRequest request;

    private final HttpServletResponse response;

    private final SecurityContextRepository securityContextRepository;

    @Override
    public LoginUserVO login(LoginDTO dto) {
        Assert.notNull(dto, "登录请求不能为空");
        Assert.hasText(dto.getUsername(), "用户名不能为空");
        Assert.hasText(dto.getPassword(), "密码不能为空");

        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(dto.getUsername().trim(), dto.getPassword());

        try {
            Authentication authentication = authenticationManager.authenticate(token);

            // 显式创建 SecurityContext 并写入当前线程
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            // 触发 Session 创建，并显式把 SecurityContext 保存到 Session
            // 保证后端能正确返回 Set-Cookie，前端 axios 后续请求可自动携带
            HttpSession session = request.getSession(true);
            securityContextRepository.saveContext(context, request, response);
            log.info("用户登录成功，username={}，sessionId={}", dto.getUsername(), session.getId());

            return buildLoginUserVO(dto.getUsername().trim());
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken)) {
            log.info("用户登出，username={}", authentication.getName());
        }
        new SecurityContextLogoutHandler().logout(request, null, authentication);
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
