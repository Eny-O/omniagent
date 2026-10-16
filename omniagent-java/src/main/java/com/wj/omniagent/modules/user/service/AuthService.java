package com.wj.omniagent.modules.user.service;

import com.wj.omniagent.modules.user.pojo.dto.LoginDTO;
import com.wj.omniagent.modules.user.pojo.vo.LoginUserVO;

/**
 * 认证服务
 */
public interface AuthService {

    /**
     * 用户登录
     *
     * @param dto 登录请求
     * @return 登录成功的用户信息
     */
    LoginUserVO login(LoginDTO dto);

    /**
     * 用户登出
     */
    void logout();

    /**
     * 获取当前登录用户信息
     *
     * @return 当前用户信息，未登录返回 null
     */
    LoginUserVO getCurrentUser();
}
