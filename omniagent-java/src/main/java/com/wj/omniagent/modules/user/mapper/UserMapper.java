package com.wj.omniagent.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wj.omniagent.modules.user.pojo.dos.Users;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<Users> {
}
