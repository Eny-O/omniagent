package com.wj.omniagent.modules.user.service.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wj.omniagent.common.enums.ErrorCodeEnum;
import com.wj.omniagent.exception.BusinessException;
import com.wj.omniagent.modules.user.mapper.UserMapper;
import com.wj.omniagent.modules.user.pojo.dos.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;

/**
 * Spring Security 用户加载服务
 * <p>
 * 从 users 表按 username 查询用户，封装为 UserDetails。
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "用户名不能为空");
        }

        LambdaQueryWrapper<Users> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Users::getUsername, username.trim());
        Users user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        // 默认赋予普通用户角色，后续接入 RBAC 后可扩展
        return new LoginUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getStatus(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                isEnabled(user)
        );
    }

    private boolean isEnabled(Users user) {
        // status: 1=启用, 0=禁用
        return user.getStatus() != null && user.getStatus() == 1;
    }
}
