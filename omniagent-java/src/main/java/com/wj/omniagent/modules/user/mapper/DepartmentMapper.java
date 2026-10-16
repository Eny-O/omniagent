package com.wj.omniagent.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wj.omniagent.modules.user.pojo.dos.Departments;
import org.apache.ibatis.annotations.Mapper;

/**
 * 部门 Mapper
 */
@Mapper
public interface DepartmentMapper extends BaseMapper<Departments> {
}
