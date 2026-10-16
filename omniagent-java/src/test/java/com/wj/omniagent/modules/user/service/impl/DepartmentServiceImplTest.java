package com.wj.omniagent.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 部门 Service 单元测试
 */
@ExtendWith(MockitoExtension.class)
class DepartmentServiceImplTest {

    @Mock
    private DepartmentMapper departmentMapper;

    @InjectMocks
    private DepartmentServiceImpl departmentService;

    private DepartmentCreateDTO createDTO;

    @BeforeEach
    void setUp() {
        createDTO = new DepartmentCreateDTO();
        createDTO.setDeptCode("RD");
        createDTO.setDeptName("研发部");
        createDTO.setParentId(null);
        createDTO.setSortOrder(1);
    }

    // ==================== 创建部门 ====================

    @Test
    @DisplayName("创建部门成功")
    void createDepartment_Success() {
        // given
        when(departmentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(departmentMapper.insert(any(Departments.class))).thenAnswer(invocation -> {
            Departments dept = invocation.getArgument(0);
            dept.setId(1L);
            return 1;
        });

        // when
        DepartmentVO result = departmentService.createDepartment(createDTO);

        // then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("RD", result.getDeptCode());
        assertEquals("研发部", result.getDeptName());
        assertEquals(1, result.getSortOrder());
        verify(departmentMapper, times(1)).insert(any(Departments.class));
    }

    @Test
    @DisplayName("创建部门-部门编码已存在时抛出异常")
    void createDepartment_DeptCodeExists_ThrowsException() {
        // given
        when(departmentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(buildDepartment(1L, "RD", "研发部"));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> departmentService.createDepartment(createDTO)
        );
        assertEquals(ErrorCodeEnum.DEPT_ALREADY_EXISTS.getCode(), exception.getErrorCode());
        verify(departmentMapper, never()).insert(any(Departments.class));
    }

    @Test
    @DisplayName("创建部门-数据库唯一索引冲突时抛出异常")
    void createDepartment_DuplicateKey_ThrowsException() {
        // given
        when(departmentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(departmentMapper.insert(any(Departments.class))).thenThrow(DuplicateKeyException.class);

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> departmentService.createDepartment(createDTO)
        );
        assertEquals(ErrorCodeEnum.DEPT_ALREADY_EXISTS.getCode(), exception.getErrorCode());
    }

    @Test
    @DisplayName("创建部门-上级部门不存在时抛出异常")
    void createDepartment_ParentNotFound_ThrowsException() {
        // given
        createDTO.setParentId(999L);
        when(departmentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(departmentMapper.selectById(999L)).thenReturn(null);

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> departmentService.createDepartment(createDTO)
        );
        assertEquals(ErrorCodeEnum.DEPT_PARENT_NOT_FOUND.getCode(), exception.getErrorCode());
        verify(departmentMapper, never()).insert(any(Departments.class));
    }

    // ==================== 查询部门 ====================

    @Test
    @DisplayName("根据ID查询部门成功")
    void getDepartmentById_Success() {
        // given
        Departments dept = buildDepartment(1L, "RD", "研发部");
        when(departmentMapper.selectById(1L)).thenReturn(dept);

        // when
        DepartmentVO result = departmentService.getDepartmentById(1L);

        // then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("RD", result.getDeptCode());
        assertEquals("研发部", result.getDeptName());
    }

    @Test
    @DisplayName("根据ID查询部门-不存在时抛出异常")
    void getDepartmentById_NotFound_ThrowsException() {
        // given
        when(departmentMapper.selectById(999L)).thenReturn(null);

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> departmentService.getDepartmentById(999L)
        );
        assertEquals(ErrorCodeEnum.DEPT_NOT_FOUND.getCode(), exception.getErrorCode());
    }

    // ==================== 分页查询 ====================

    @Test
    @DisplayName("分页查询部门成功")
    @SuppressWarnings("unchecked")
    void pageDepartments_Success() {
        // given
        DepartmentQuery query = new DepartmentQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setKeyword("研发");

        Departments dept = buildDepartment(1L, "RD", "研发部");
        Page<Departments> page = new Page<>(1, 10, 1);
        page.setRecords(Collections.singletonList(dept));

        when(departmentMapper.selectPage(any(Page.class), any())).thenReturn(page);

        // when
        PageResult<DepartmentPageVO> result = departmentService.pageDepartments(query);

        // then
        assertNotNull(result);
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("RD", result.getRecords().get(0).getDeptCode());
        assertEquals("研发部", result.getRecords().get(0).getDeptName());
    }

    @Test
    @DisplayName("分页查询-默认分页参数修正")
    @SuppressWarnings("unchecked")
    void pageDepartments_DefaultPageParams() {
        // given
        DepartmentQuery query = new DepartmentQuery();

        Page<Departments> page = new Page<>(1, 10, 0);
        page.setRecords(Collections.emptyList());

        when(departmentMapper.selectPage(any(Page.class), any())).thenReturn(page);

        // when
        PageResult<DepartmentPageVO> result = departmentService.pageDepartments(query);

        // then
        assertEquals(1, result.getPageNum());
        assertEquals(10, result.getPageSize());
    }

    // ==================== 更新部门 ====================

    @Test
    @DisplayName("更新部门成功")
    void updateDepartment_Success() {
        // given
        Departments dept = buildDepartment(1L, "RD", "研发部");
        when(departmentMapper.selectById(1L)).thenReturn(dept);
        when(departmentMapper.updateById(any(Departments.class))).thenReturn(1);

        DepartmentUpdateDTO dto = new DepartmentUpdateDTO();
        dto.setDeptName("研发中心");
        dto.setSortOrder(2);

        // when
        DepartmentVO result = departmentService.updateDepartment(1L, dto);

        // then
        assertEquals("研发中心", result.getDeptName());
        assertEquals(2, result.getSortOrder());
        verify(departmentMapper, times(1)).updateById(any(Departments.class));
    }

    @Test
    @DisplayName("更新部门-不存在时抛出异常")
    void updateDepartment_NotFound_ThrowsException() {
        // given
        when(departmentMapper.selectById(999L)).thenReturn(null);
        DepartmentUpdateDTO dto = new DepartmentUpdateDTO();
        dto.setDeptName("研发中心");

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> departmentService.updateDepartment(999L, dto)
        );
        assertEquals(ErrorCodeEnum.DEPT_NOT_FOUND.getCode(), exception.getErrorCode());
        verify(departmentMapper, never()).updateById(any(Departments.class));
    }

    @Test
    @DisplayName("更新部门-上级部门不能是自己")
    void updateDepartment_ParentSelf_ThrowsException() {
        // given
        Departments dept = buildDepartment(1L, "RD", "研发部");
        when(departmentMapper.selectById(1L)).thenReturn(dept);

        DepartmentUpdateDTO dto = new DepartmentUpdateDTO();
        dto.setParentId(1L);

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> departmentService.updateDepartment(1L, dto)
        );
        assertEquals(ErrorCodeEnum.DEPT_PARENT_SELF.getCode(), exception.getErrorCode());
        verify(departmentMapper, never()).updateById(any(Departments.class));
    }

    @Test
    @DisplayName("更新部门-上级部门不存在时抛出异常")
    void updateDepartment_ParentNotFound_ThrowsException() {
        // given
        Departments dept = buildDepartment(1L, "RD", "研发部");
        when(departmentMapper.selectById(1L)).thenReturn(dept);
        when(departmentMapper.selectById(999L)).thenReturn(null);

        DepartmentUpdateDTO dto = new DepartmentUpdateDTO();
        dto.setParentId(999L);

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> departmentService.updateDepartment(1L, dto)
        );
        assertEquals(ErrorCodeEnum.DEPT_PARENT_NOT_FOUND.getCode(), exception.getErrorCode());
        verify(departmentMapper, never()).updateById(any(Departments.class));
    }

    // ==================== 删除部门 ====================

    @Test
    @DisplayName("删除部门成功")
    void deleteDepartment_Success() {
        // given
        Departments dept = buildDepartment(1L, "RD", "研发部");
        when(departmentMapper.selectById(1L)).thenReturn(dept);
        when(departmentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(departmentMapper.deleteById(1L)).thenReturn(1);

        // when
        departmentService.deleteDepartment(1L);

        // then
        verify(departmentMapper, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("删除部门-不存在时抛出异常")
    void deleteDepartment_NotFound_ThrowsException() {
        // given
        when(departmentMapper.selectById(999L)).thenReturn(null);

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> departmentService.deleteDepartment(999L)
        );
        assertEquals(ErrorCodeEnum.DEPT_NOT_FOUND.getCode(), exception.getErrorCode());
        verify(departmentMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("删除部门-存在子部门时抛出异常")
    void deleteDepartment_HasChildren_ThrowsException() {
        // given
        Departments dept = buildDepartment(1L, "RD", "研发部");
        when(departmentMapper.selectById(1L)).thenReturn(dept);
        when(departmentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> departmentService.deleteDepartment(1L)
        );
        assertEquals(ErrorCodeEnum.DEPT_HAS_CHILDREN.getCode(), exception.getErrorCode());
        verify(departmentMapper, never()).deleteById(any(Long.class));
    }

    // ==================== 私有工具方法 ====================

    private Departments buildDepartment(Long id, String deptCode, String deptName) {
        Departments dept = new Departments();
        dept.setId(id);
        dept.setDeptCode(deptCode);
        dept.setDeptName(deptName);
        dept.setSortOrder(0);
        return dept;
    }
}
