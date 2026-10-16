package com.wj.omniagent.modules.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wj.omniagent.common.pojo.result.PageResult;
import com.wj.omniagent.modules.user.pojo.dos.Departments;
import com.wj.omniagent.modules.user.pojo.dto.DepartmentCreateDTO;
import com.wj.omniagent.modules.user.pojo.dto.DepartmentQuery;
import com.wj.omniagent.modules.user.pojo.dto.DepartmentUpdateDTO;
import com.wj.omniagent.modules.user.pojo.vo.DepartmentPageVO;
import com.wj.omniagent.modules.user.pojo.vo.DepartmentVO;

/**
 * 部门 Service
 */
public interface DepartmentService extends IService<Departments> {

    /**
     * 创建部门
     */
    DepartmentVO createDepartment(DepartmentCreateDTO dto);

    /**
     * 根据ID查询部门详情
     */
    DepartmentVO getDepartmentById(Long id);

    /**
     * 分页查询部门
     */
    PageResult<DepartmentPageVO> pageDepartments(DepartmentQuery query);

    /**
     * 更新部门信息
     */
    DepartmentVO updateDepartment(Long id, DepartmentUpdateDTO dto);

    /**
     * 删除部门
     */
    void deleteDepartment(Long id);
}
