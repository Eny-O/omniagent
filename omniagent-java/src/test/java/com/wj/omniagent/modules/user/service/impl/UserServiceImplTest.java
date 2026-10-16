package com.wj.omniagent.modules.user.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wj.omniagent.common.enums.ErrorCodeEnum;
import com.wj.omniagent.common.pojo.result.PageResult;
import com.wj.omniagent.exception.BusinessException;
import com.wj.omniagent.modules.user.mapper.UserMapper;
import com.wj.omniagent.modules.user.pojo.dos.Users;
import com.wj.omniagent.modules.user.pojo.dto.UserCreateDTO;
import com.wj.omniagent.modules.user.pojo.dto.UserQuery;
import com.wj.omniagent.modules.user.pojo.dto.UserUpdateDTO;
import com.wj.omniagent.modules.user.pojo.vo.UserPageVO;
import com.wj.omniagent.modules.user.pojo.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 用户 Service 单元测试
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private UserCreateDTO createDTO;

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(passwordEncoder.encode(any())).thenReturn("$2a$10$encoded");

        createDTO = new UserCreateDTO();
        createDTO.setUsername("zhangsan");
        createDTO.setPassword("123456");
        createDTO.setRealName("张三");
        createDTO.setEmail("zhangsan@example.com");
        createDTO.setPhone("13800000001");
        createDTO.setDeptId(1L);
        createDTO.setStatus(1);
    }

    // ==================== 创建用户 ====================

    @Test
    @DisplayName("创建用户成功")
    void createUser_Success() {
        // given
        when(userMapper.selectOne(any())).thenReturn(null);
        when(userMapper.insert(any(Users.class))).thenAnswer(invocation -> {
            Users user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        });

        // when
        UserVO result = userService.createUser(createDTO);

        // then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("zhangsan", result.getUsername());
        assertEquals("张三", result.getRealName());
        assertEquals("zhangsan@example.com", result.getEmail());
        assertEquals("13800000001", result.getPhone());
        assertEquals(1, result.getStatus());
        verify(userMapper, times(1)).insert(any(Users.class));
    }

    @Test
    @DisplayName("创建用户-用户名已存在时抛出异常")
    void createUser_UsernameExists_ThrowsException() {
        // given
        when(userMapper.selectOne(any())).thenReturn(buildUser(1L, "zhangsan"));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.createUser(createDTO)
        );
        assertEquals(ErrorCodeEnum.USER_ALREADY_EXISTS.getCode(), exception.getErrorCode());
        verify(userMapper, never()).insert(any(Users.class));
    }

    @Test
    @DisplayName("创建用户-数据库唯一索引冲突时抛出异常")
    void createUser_DuplicateKey_ThrowsException() {
        // given
        when(userMapper.selectOne(any())).thenReturn(null);
        when(userMapper.insert(any(Users.class))).thenThrow(DuplicateKeyException.class);

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.createUser(createDTO)
        );
        assertEquals(ErrorCodeEnum.USER_ALREADY_EXISTS.getCode(), exception.getErrorCode());
    }

    @Test
    @DisplayName("创建用户-用户名为空时抛出异常")
    void createUser_UsernameBlank_ThrowsException() {
        // given
        createDTO.setUsername("  ");

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.createUser(createDTO)
        );
        assertEquals("用户名不能为空", exception.getMessage());
    }

    @Test
    @DisplayName("创建用户-密码为空时抛出异常")
    void createUser_PasswordBlank_ThrowsException() {
        // given
        createDTO.setPassword(null);

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.createUser(createDTO)
        );
        assertEquals("密码不能为空", exception.getMessage());
    }

    // ==================== 查询用户 ====================

    @Test
    @DisplayName("根据ID查询用户成功")
    void getUserById_Success() {
        // given
        Users user = buildUser(1L, "zhangsan");
        when(userMapper.selectById(1L)).thenReturn(user);

        // when
        UserVO result = userService.getUserById(1L);

        // then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("zhangsan", result.getUsername());
    }

    @Test
    @DisplayName("根据ID查询用户-不存在时抛出异常")
    void getUserById_NotFound_ThrowsException() {
        // given
        when(userMapper.selectById(999L)).thenReturn(null);

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.getUserById(999L)
        );
        assertEquals(ErrorCodeEnum.USER_NOT_FOUND.getCode(), exception.getErrorCode());
    }

    // ==================== 分页查询 ====================

    @Test
    @DisplayName("分页查询用户成功")
    @SuppressWarnings("unchecked")
    void pageUsers_Success() {
        // given
        UserQuery query = new UserQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setKeyword("zhangsan");

        Users user = buildUser(1L, "zhangsan");
        Page<Users> page = new Page<>(1, 10, 1);
        page.setRecords(Collections.singletonList(user));

        when(userMapper.selectPage(any(Page.class), any())).thenReturn(page);

        // when
        PageResult<UserPageVO> result = userService.pageUsers(query);

        // then
        assertNotNull(result);
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("zhangsan", result.getRecords().get(0).getUsername());
    }

    @Test
    @DisplayName("分页查询-默认分页参数修正")
    @SuppressWarnings("unchecked")
    void pageUsers_DefaultPageParams() {
        // given
        UserQuery query = new UserQuery();

        Page<Users> page = new Page<>(1, 10, 0);
        page.setRecords(Collections.emptyList());

        when(userMapper.selectPage(any(Page.class), any())).thenReturn(page);

        // when
        PageResult<UserPageVO> result = userService.pageUsers(query);

        // then
        assertEquals(1, result.getPageNum());
        assertEquals(10, result.getPageSize());
    }

    // ==================== 更新用户 ====================

    @Test
    @DisplayName("更新用户成功")
    void updateUser_Success() {
        // given
        Users user = buildUser(1L, "zhangsan");
        user.setRealName("张三");
        user.setEmail("old@example.com");
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(Users.class))).thenReturn(1);

        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setRealName("张三丰");
        dto.setEmail("new@example.com");
        dto.setPhone("13900000002");
        dto.setStatus(0);

        // when
        UserVO result = userService.updateUser(1L, dto);

        // then
        assertEquals("张三丰", result.getRealName());
        assertEquals("new@example.com", result.getEmail());
        assertEquals("13900000002", result.getPhone());
        assertEquals(0, result.getStatus());
        verify(userMapper, times(1)).updateById(any(Users.class));
    }

    @Test
    @DisplayName("更新用户-传空字符串可清空可选字段")
    void updateUser_ClearOptionalFields() {
        // given
        Users user = buildUser(1L, "zhangsan");
        user.setRealName("张三");
        user.setEmail("old@example.com");
        user.setPhone("13800000001");
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(Users.class))).thenReturn(1);

        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setRealName("");
        dto.setEmail("");
        dto.setPhone("");

        // when
        userService.updateUser(1L, dto);

        // then
        assertNull(user.getRealName());
        assertNull(user.getEmail());
        assertNull(user.getPhone());
    }

    @Test
    @DisplayName("更新用户-用户不存在时抛出异常")
    void updateUser_NotFound_ThrowsException() {
        // given
        when(userMapper.selectById(999L)).thenReturn(null);
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setRealName("李四");

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.updateUser(999L, dto)
        );
        assertEquals(ErrorCodeEnum.USER_NOT_FOUND.getCode(), exception.getErrorCode());
        verify(userMapper, never()).updateById(any(Users.class));
    }

    @Test
    @DisplayName("更新用户-非法状态时抛出异常")
    void updateUser_InvalidStatus_ThrowsException() {
        // given
        Users user = buildUser(1L, "zhangsan");
        when(userMapper.selectById(1L)).thenReturn(user);

        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setStatus(2);

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.updateUser(1L, dto)
        );
        assertEquals(ErrorCodeEnum.PARAM_ERROR.getCode(), exception.getErrorCode());
        verify(userMapper, never()).updateById(any(Users.class));
    }

    // ==================== 删除用户 ====================

    @Test
    @DisplayName("删除用户成功")
    void deleteUser_Success() {
        // given
        Users user = buildUser(1L, "zhangsan");
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.deleteById(1L)).thenReturn(1);

        // when
        userService.deleteUser(1L);

        // then
        verify(userMapper, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("删除用户-不存在时抛出异常")
    void deleteUser_NotFound_ThrowsException() {
        // given
        when(userMapper.selectById(999L)).thenReturn(null);

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.deleteUser(999L)
        );
        assertEquals(ErrorCodeEnum.USER_NOT_FOUND.getCode(), exception.getErrorCode());
        verify(userMapper, never()).deleteById(any(Long.class));
    }

    // ==================== 私有工具方法 ====================

    private Users buildUser(Long id, String username) {
        Users user = new Users();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash("$2a$10$...");
        user.setStatus(1);
        return user;
    }
}
