package com.wj.omniagent.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wj.omniagent.common.enums.ErrorCodeEnum;
import com.wj.omniagent.common.pojo.result.PageResult;
import com.wj.omniagent.exception.BusinessException;
import com.wj.omniagent.modules.user.mapper.DepartmentMapper;
import com.wj.omniagent.modules.user.pojo.dos.Departments;
import com.wj.omniagent.modules.user.pojo.dto.DepartmentCreateDTO;
import com.wj.omniagent.modules.user.pojo.dto.DepartmentQuery;
import com.wj.omniagent.modules.user.pojo.dto.DepartmentUpdateDTO;
import com.wj.omniagent.modules.user.pojo.vo.DepartmentPageVO;
import com.wj.omniagent.modules.user.pojo.vo.DepartmentVO;
import com.wj.omniagent.modules.user.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl extends ServiceImpl<DepartmentMapper, Departments> implements DepartmentService {

    private final DepartmentMapper departmentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepartmentVO createDepartment(DepartmentCreateDTO dto) {
        Assert.notNull(dto, "创建部门请求不能为空");
        Assert.hasText(dto.getDeptCode(), "部门编码不能为空");
        Assert.hasText(dto.getDeptName(), "部门名称不能为空");

        // 部门编码唯一性校验
        Departments existed = findByDeptCode(dto.getDeptCode());
        if (existed != null) {
            throw new BusinessException(ErrorCodeEnum.DEPT_ALREADY_EXISTS);
        }

        // 父部门存在性校验
        validateParentId(null, dto.getParentId());

        Departments department = new Departments();
        department.setDeptCode(dto.getDeptCode().trim().toUpperCase());
        department.setDeptName(dto.getDeptName().trim());
        department.setParentId(dto.getParentId());
        department.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        department.setCreatedAt(LocalDateTime.now());
        department.setUpdatedAt(LocalDateTime.now());

        try {
            departmentMapper.insert(department);
        } catch (DuplicateKeyException e) {
            log.warn("部门编码重复，deptCode={}", dto.getDeptCode());
            throw new BusinessException(ErrorCodeEnum.DEPT_ALREADY_EXISTS);
        }

        return convertToVO(department);
    }

    @Override
    public DepartmentVO getDepartmentById(Long id) {
        Assert.notNull(id, "部门ID不能为空");
        Departments department = departmentMapper.selectById(id);
        if (department == null) {
            throw new BusinessException(ErrorCodeEnum.DEPT_NOT_FOUND);
        }
        return convertToVO(department);
    }

    @Override
    public List<DepartmentVO> listAllDepartments() {
        LambdaQueryWrapper<Departments> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Departments::getSortOrder)
                .orderByDesc(Departments::getCreatedAt);
        return departmentMapper.selectList(wrapper).stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<DepartmentPageVO> pageDepartments(DepartmentQuery query) {
        Assert.notNull(query, "查询参数不能为空");

        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
        pageSize = Math.min(pageSize, 100);

        Page<Departments> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Departments> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(Departments::getDeptCode, query.getKeyword())
                    .or()
                    .like(Departments::getDeptName, query.getKeyword());
        }
        if (query.getParentId() != null) {
            wrapper.eq(Departments::getParentId, query.getParentId());
        }
        wrapper.orderByAsc(Departments::getSortOrder)
                .orderByDesc(Departments::getCreatedAt);

        Page<Departments> departmentsPage = departmentMapper.selectPage(page, wrapper);
        return PageResult.of(
                departmentsPage.getTotal(),
                departmentsPage.getRecords().stream()
                        .map(this::convertToPageVO)
                        .collect(Collectors.toList()),
                pageNum,
                pageSize
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepartmentVO updateDepartment(Long id, DepartmentUpdateDTO dto) {
        Assert.notNull(id, "部门ID不能为空");
        Assert.notNull(dto, "更新部门请求不能为空");

        Departments department = departmentMapper.selectById(id);
        if (department == null) {
            throw new BusinessException(ErrorCodeEnum.DEPT_NOT_FOUND);
        }

        // 父部门合法性校验
        validateParentId(id, dto.getParentId());

        if (StringUtils.hasText(dto.getDeptName())) {
            department.setDeptName(dto.getDeptName().trim());
        }
        if (dto.getParentId() != null) {
            department.setParentId(dto.getParentId());
        }
        if (dto.getSortOrder() != null) {
            department.setSortOrder(dto.getSortOrder());
        }

        department.setUpdatedAt(LocalDateTime.now());
        departmentMapper.updateById(department);

        return convertToVO(department);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDepartment(Long id) {
        Assert.notNull(id, "部门ID不能为空");
        Departments department = departmentMapper.selectById(id);
        if (department == null) {
            throw new BusinessException(ErrorCodeEnum.DEPT_NOT_FOUND);
        }

        // 校验是否存在子部门
        if (hasChildren(id)) {
            throw new BusinessException(ErrorCodeEnum.DEPT_HAS_CHILDREN);
        }

        departmentMapper.deleteById(id);
    }

    private Departments findByDeptCode(String deptCode) {
        if (!StringUtils.hasText(deptCode)) {
            return null;
        }
        LambdaQueryWrapper<Departments> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Departments::getDeptCode, deptCode.trim().toUpperCase());
        return departmentMapper.selectOne(wrapper);
    }

    private void validateParentId(Long currentId, Long parentId) {
        if (parentId == null) {
            return;
        }
        if (currentId != null && currentId.equals(parentId)) {
            throw new BusinessException(ErrorCodeEnum.DEPT_PARENT_SELF);
        }
        Departments parent = departmentMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException(ErrorCodeEnum.DEPT_PARENT_NOT_FOUND);
        }
    }

    private boolean hasChildren(Long id) {
        LambdaQueryWrapper<Departments> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Departments::getParentId, id);
        return departmentMapper.selectCount(wrapper) > 0;
    }

    private DepartmentVO convertToVO(Departments department) {
        if (department == null) {
            return null;
        }
        DepartmentVO vo = new DepartmentVO();
        BeanUtils.copyProperties(department, vo);
        return vo;
    }

    private DepartmentPageVO convertToPageVO(Departments department) {
        if (department == null) {
            return null;
        }
        DepartmentPageVO vo = new DepartmentPageVO();
        BeanUtils.copyProperties(department, vo);
        return vo;
    }
}
