package com.wj.omniagent.modules.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wj.omniagent.common.pojo.result.PageResult;
import com.wj.omniagent.modules.user.pojo.dos.Users;
import com.wj.omniagent.modules.user.pojo.dto.UserCreateDTO;
import com.wj.omniagent.modules.user.pojo.dto.UserQuery;
import com.wj.omniagent.modules.user.pojo.dto.UserUpdateDTO;
import com.wj.omniagent.modules.user.pojo.vo.UserPageVO;
import com.wj.omniagent.modules.user.pojo.vo.UserVO;

/**
 * 用户 Service
 */
public interface UserService extends IService<Users> {

    /**
     * 创建用户
     */
    UserVO createUser(UserCreateDTO dto);

    /**
     * 根据ID查询用户详情
     */
    UserVO getUserById(Long id);

    /**
     * 分页查询用户
     */
    PageResult<UserPageVO> pageUsers(UserQuery query);

    /**
     * 更新用户信息
     */
    UserVO updateUser(Long id, UserUpdateDTO dto);

    /**
     * 删除用户
     */
    void deleteUser(Long id);
}
