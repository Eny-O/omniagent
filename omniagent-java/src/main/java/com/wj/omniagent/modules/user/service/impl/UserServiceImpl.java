package com.wj.omniagent.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import com.wj.omniagent.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, Users> implements UserService {

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO createUser(UserCreateDTO dto) {
        Assert.notNull(dto, "创建用户请求不能为空");
        Assert.hasText(dto.getUsername(), "用户名不能为空");
        Assert.hasText(dto.getPassword(), "密码不能为空");

        // 幂等/唯一性：用户名已存在则直接报错
        Users existed = findByUsername(dto.getUsername());
        if (existed != null) {
            throw new BusinessException(ErrorCodeEnum.USER_ALREADY_EXISTS);
        }

        Users user = new Users();
        user.setUsername(dto.getUsername().trim());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(StringUtils.hasText(dto.getRealName()) ? dto.getRealName().trim() : null);
        user.setEmail(StringUtils.hasText(dto.getEmail()) ? dto.getEmail().trim() : null);
        user.setPhone(StringUtils.hasText(dto.getPhone()) ? dto.getPhone().trim() : null);
        user.setDeptId(dto.getDeptId());
        user.setStatus(resolveStatus(dto.getStatus()));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            log.warn("用户名重复，username={}", dto.getUsername());
            throw new BusinessException(ErrorCodeEnum.USER_ALREADY_EXISTS);
        }

        return convertToVO(user);
    }

    @Override
    public UserVO getUserById(Long id) {
        Assert.notNull(id, "用户ID不能为空");
        Users user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }
        return convertToVO(user);
    }

    @Override
    public PageResult<UserPageVO> pageUsers(UserQuery query) {
        Assert.notNull(query, "查询参数不能为空");

        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
        pageSize = Math.min(pageSize, 100);

        Page<Users> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Users> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(Users::getUsername, query.getKeyword())
                    .or()
                    .like(Users::getRealName, query.getKeyword());
        }
        if (query.getDeptId() != null) {
            wrapper.eq(Users::getDeptId, query.getDeptId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Users::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(Users::getCreatedAt);

        Page<Users> usersPage = userMapper.selectPage(page, wrapper);
        return PageResult.of(
                usersPage.getTotal(),
                usersPage.getRecords().stream()
                        .map(this::convertToPageVO)
                        .collect(Collectors.toList()),
                pageNum,
                pageSize
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateUser(Long id, UserUpdateDTO dto) {
        Assert.notNull(id, "用户ID不能为空");
        Assert.notNull(dto, "更新用户请求不能为空");

        Users user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }

        // 部分更新：仅覆盖非空字段；邮箱/手机传空字符串可清空
        if (StringUtils.hasText(dto.getRealName())) {
            user.setRealName(dto.getRealName().trim());
        } else if (dto.getRealName() != null) {
            user.setRealName(null);
        }

        if (StringUtils.hasText(dto.getEmail())) {
            user.setEmail(dto.getEmail().trim());
        } else if (dto.getEmail() != null) {
            user.setEmail(null);
        }

        if (StringUtils.hasText(dto.getPhone())) {
            user.setPhone(dto.getPhone().trim());
        } else if (dto.getPhone() != null) {
            user.setPhone(null);
        }

        if (dto.getDeptId() != null) {
            user.setDeptId(dto.getDeptId());
        }

        if (dto.getStatus() != null) {
            user.setStatus(resolveStatus(dto.getStatus()));
        }

        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        return convertToVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        Assert.notNull(id, "用户ID不能为空");
        Users user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }
        userMapper.deleteById(id);
    }

    private Users findByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        LambdaQueryWrapper<Users> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Users::getUsername, username.trim());
        return userMapper.selectOne(wrapper);
    }

    private Integer resolveStatus(Integer status) {
        if (status == null) {
            return 1;
        }
        if (status != 0 && status != 1) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "用户状态只能是 0 或 1");
        }
        return status;
    }

    private String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    private UserVO convertToVO(Users user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    private UserPageVO convertToPageVO(Users user) {
        if (user == null) {
            return null;
        }
        UserPageVO vo = new UserPageVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
