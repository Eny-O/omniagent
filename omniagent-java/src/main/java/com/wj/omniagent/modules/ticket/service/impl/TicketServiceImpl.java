package com.wj.omniagent.modules.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wj.omniagent.common.context.ActingContext;
import com.wj.omniagent.common.enums.ErrorCodeEnum;
import com.wj.omniagent.common.pojo.result.PageResult;
import com.wj.omniagent.common.utils.SecurityUtils;

import com.wj.omniagent.exception.BusinessException;
import com.wj.omniagent.modules.ticket.mapper.TicketCategoryMapper;
import com.wj.omniagent.modules.ticket.mapper.TicketMapper;
import com.wj.omniagent.modules.ticket.mapper.TicketPriorityMapper;
import com.wj.omniagent.modules.ticket.mapper.TicketSourceMapper;
import com.wj.omniagent.modules.ticket.mapper.TicketAssignmentsMapper;
import com.wj.omniagent.modules.ticket.mapper.TicketHistoryMapper;
import com.wj.omniagent.modules.user.mapper.UserMapper;
import com.wj.omniagent.modules.user.pojo.dos.Users;
import com.wj.omniagent.modules.user.service.security.LoginUserPrincipal;
import com.wj.omniagent.modules.ticket.pojo.dos.TicketCategories;
import com.wj.omniagent.modules.ticket.pojo.dos.TicketPriorities;
import com.wj.omniagent.modules.ticket.pojo.dos.TicketSources;
import com.wj.omniagent.modules.ticket.pojo.dos.Tickets;
import com.wj.omniagent.modules.ticket.pojo.dos.TicketAssignments;
import com.wj.omniagent.modules.ticket.pojo.dos.TicketHistory;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketCreateDTO;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketQuery;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketUpdateDTO;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketUpdateStatusDTO;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketAssignDTO;
import com.wj.omniagent.modules.ticket.pojo.enums.DataScope;
import com.wj.omniagent.modules.ticket.pojo.enums.TicketStatusEnum;
import com.wj.omniagent.modules.ticket.pojo.enums.TicketActionEnum;
import com.wj.omniagent.modules.ticket.pojo.vo.TicketPageVO;
import com.wj.omniagent.modules.ticket.pojo.vo.TicketVO;
import com.wj.omniagent.modules.ticket.service.TicketService;
import com.wj.omniagent.modules.ticket.util.TicketNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl extends ServiceImpl<TicketMapper, Tickets> implements TicketService {

    private final TicketMapper ticketMapper;

    private final TicketNoGenerator ticketNoGenerator;

    private final TicketCategoryMapper ticketCategoryMapper;

    private final TicketPriorityMapper ticketPriorityMapper;

    private final TicketSourceMapper ticketSourceMapper;

    private final UserMapper userMapper;

    private final TicketAssignmentsMapper ticketAssignmentMapper;
    private final TicketHistoryMapper ticketHistoryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketVO createTicket(TicketCreateDTO dto, Long operatorId) {
        Assert.notNull(dto, "创建工单请求不能为空");
        Assert.hasText(dto.getTitle(), "工单标题不能为空");

        // 校验外键字典是否存在（分类/优先级/来源），非法值直接返回友好错误
        assertCategoryExists(dto.getCategoryId());
        assertPriorityExists(dto.getPriorityId());
        String effectiveSource = StringUtils.hasText(dto.getSource()) ? dto.getSource().trim() : "WEB";
        assertSourceExists(effectiveSource);

        Tickets ticket = new Tickets();
        BeanUtils.copyProperties(dto, ticket);

        // 处理默认值和特殊字段
        if (StringUtils.hasText(dto.getTitle())) {
            ticket.setTitle(dto.getTitle().trim());
        }
        ticket.setStatusId(TicketStatusEnum.DRAFT.getSortOrder());
        ticket.setCreatorId(operatorId);
        ticket.setSource(effectiveSource);
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());

        // 生成工单号，数据库唯一索引 + 重试保证唯一性
        int retry = 0;
        while (retry < 3) {
            ticket.setTicketNo(ticketNoGenerator.generate());
            try {
                ticketMapper.insert(ticket);
                break;
            } catch (DuplicateKeyException e) {
                log.warn("工单号重复，重新生成，ticketNo={}", ticket.getTicketNo());
                retry++;
                if (retry >= 3) {
                    throw new BusinessException(ErrorCodeEnum.TICKET_GENER_FAILED_RETRY);
                }
            }
        }

        recordHistory(ticket.getId(), operatorId, TicketActionEnum.CREATE, null, null, null,
                "创建工单 " + ticket.getTicketNo());
        return convertToVO(ticket);
    }

    @Override
    public TicketVO getTicketById(Long id) {
        Assert.notNull(id, "工单ID不能为空");
        Tickets ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(ErrorCodeEnum.TICKET_NOT_FOUND);
        }
        assertViewable(ticket);
        return convertToVO(ticket);
    }

    @Override
    public PageResult<TicketPageVO> pageTickets(TicketQuery query) {
        Assert.notNull(query, "查询参数不能为空");

        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
        pageSize = Math.min(pageSize, 100);

        Page<Tickets> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Tickets> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(Tickets::getTitle, query.getKeyword())
                    .or()
                    .like(Tickets::getTicketNo, query.getKeyword());
        }
        if (query.getStatusId() != null) {
            wrapper.eq(Tickets::getStatusId, query.getStatusId());
        }
        if (query.getPriorityId() != null) {
            wrapper.eq(Tickets::getPriorityId, query.getPriorityId());
        }
        if (query.getCategoryId() != null) {
            wrapper.eq(Tickets::getCategoryId, query.getCategoryId());
        }

        // 数据范围过滤：按当前角色限制可见工单（P1 第3批）
        applyDataScope(wrapper);

        wrapper.orderByDesc(Tickets::getCreatedAt);

        Page<Tickets> ticketsPage = ticketMapper.selectPage(page, wrapper);
        return PageResult.of(
                ticketsPage.getTotal(),
                ticketsPage.getRecords().stream()
                        .map(this::convertToPageVO)
                        .collect(Collectors.toList()),
                pageNum,
                pageSize
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketVO updateTicket(Long id, TicketUpdateDTO dto, Long operatorId) {
        Assert.notNull(id, "工单ID不能为空");
        Assert.notNull(dto, "更新工单请求不能为空");

        Tickets ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(ErrorCodeEnum.TICKET_NOT_FOUND);
        }

        // 已关闭工单不允许再编辑基本信息
        TicketStatusEnum currentStatus = getStatusBySortOrder(ticket.getStatusId());
        if (currentStatus.isTerminal()) {
            throw new BusinessException(ErrorCodeEnum.TICKET_CLOSED_NOT_UPDATE);
        }

        // 角色 + 归属 + 状态校验：报障人仅未处理前可改自己的；处理人/主管需归属本部门或本人；管理员放行
        assertEditable(ticket, currentStatus);

        // 仅更新非空字段，避免把已有值覆盖成 null
        Tickets beforeUpdate = new Tickets();
        BeanUtils.copyProperties(ticket, beforeUpdate);
        applyUpdateFields(ticket, dto);
        ticket.setUpdatedAt(LocalDateTime.now());

        ticketMapper.updateById(ticket);
        recordUpdateHistory(ticket.getId(), operatorId, beforeUpdate, ticket);

        return convertToVO(ticket);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTicketStatus(Long id, TicketUpdateStatusDTO ticketUpdateStatusDTO, Long operatorId) {
        Assert.notNull(id, "工单ID不能为空");
        Assert.notNull(ticketUpdateStatusDTO, "目标状态不能为空");

        Tickets ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(ErrorCodeEnum.TICKET_NOT_FOUND);
        }

        TicketStatusEnum currentStatus = getStatusBySortOrder(ticket.getStatusId());
        TicketStatusEnum targetStatus = TicketStatusEnum.fromCode(ticketUpdateStatusDTO.getTargetStatusCode());
        if (targetStatus == null) {
            throw new BusinessException(ErrorCodeEnum.TICKET_STATUS_NOT_FOUND, "工单目标状态不存在");
        }

        // 幂等：相同状态直接返回
        if (currentStatus == targetStatus) {
            return;
        }

        // 角色 + 归属校验：仅处理人 / 主管 / 管理员可推进状态
        assertCanProcess(ticket);

        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new BusinessException(ErrorCodeEnum.TICKET_STATUS_INVALID_TRANSITION,
                    String.format("工单状态状态流转不合法：%s -> %s", currentStatus.getName(), targetStatus.getName())
            );
        }

        ticket.setStatusId(targetStatus.getSortOrder());
        if (targetStatus == TicketStatusEnum.RESOLVED) {
            ticket.setResolvedAt(LocalDateTime.now());
        }
        if (targetStatus == TicketStatusEnum.CLOSED) {
            ticket.setClosedAt(LocalDateTime.now());
            if (ticket.getResolvedAt() == null) {
                ticket.setResolvedAt(LocalDateTime.now());
            }
        }
        ticket.setUpdatedAt(LocalDateTime.now());

        ticketMapper.updateById(ticket);

        recordHistory(ticket.getId(), operatorId, TicketActionEnum.STATUS_CHANGE, "status",
                currentStatus.name(), targetStatus.name(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTicket(Long id) {
        Assert.notNull(id, "工单ID不能为空");
        Tickets ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(ErrorCodeEnum.TICKET_NOT_FOUND);
        }
        // 仅管理员可硬删除工单；调试期（未鉴权）放行
        assertCanDelete();
        recordHistory(id, SecurityUtils.getCurrentUserId(), TicketActionEnum.DELETE, null, null, null, "删除工单");
        ticketMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawTicket(Long id, Long operatorId) {
        Assert.notNull(id, "工单ID不能为空");
        Tickets ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(ErrorCodeEnum.TICKET_NOT_FOUND);
        }
        TicketStatusEnum currentStatus = getStatusBySortOrder(ticket.getStatusId());
        // 仅报障人未处理前 / 管理员可撤回；调试期（未鉴权）放行
        assertCanWithdraw(ticket, currentStatus);
        ticket.setStatusId(TicketStatusEnum.CANCELLED.getSortOrder());
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        recordHistory(ticket.getId(), operatorId, TicketActionEnum.WITHDRAW, "status",
                currentStatus.name(), TicketStatusEnum.CANCELLED.name(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTicket(Long id, TicketAssignDTO dto, Long operatorId) {
        Assert.notNull(id, "工单ID不能为空");
        Assert.notNull(dto, "派单请求不能为空");
        Assert.notNull(dto.getAssigneeId(), "被指派处理人不能为空");

        Tickets ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(ErrorCodeEnum.TICKET_NOT_FOUND);
        }
        Long oldAssigneeId = ticket.getCurrentAssigneeId();
        // 被指派人必须存在
        if (userMapper.selectById(dto.getAssigneeId()) == null) {
            throw new BusinessException(ErrorCodeEnum.ASSIGNEE_NOT_FOUND);
        }
        TicketStatusEnum currentStatus = getStatusBySortOrder(ticket.getStatusId());
        // 仅 SUPERVISOR（本部门）/ ADMIN 可派单；调试期（未鉴权）放行
        assertCanAssign(ticket);
        // 终态工单不可再派单
        if (currentStatus.isTerminal()) {
            throw new BusinessException(ErrorCodeEnum.TICKET_NO_PERMISSION, "已关闭/已取消的工单不可派单");
        }

        // 落分配记录：先将该工单的历史派单记录置为非当前，再插入当前派单（is_current=true）
        LambdaUpdateWrapper<TicketAssignments> uw = new LambdaUpdateWrapper<>();
        uw.eq(TicketAssignments::getTicketId, id).set(TicketAssignments::getIsCurrent, false);
        ticketAssignmentMapper.update(null, uw);

        TicketAssignments assignment = new TicketAssignments();
        assignment.setTicketId(id);
        assignment.setAssignorId(operatorId);
        assignment.setAssigneeId(dto.getAssigneeId());
        assignment.setRemark(dto.getRemark());
        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setIsCurrent(true);
        ticketAssignmentMapper.insert(assignment);

        // 更新工单：写入当前处理人 + 进入处理中（DRAFT/PENDING 才流转，已 PROCESSING 则保持）
        ticket.setCurrentAssigneeId(dto.getAssigneeId());
        if (currentStatus == TicketStatusEnum.DRAFT || currentStatus == TicketStatusEnum.PENDING) {
            ticket.setStatusId(TicketStatusEnum.PROCESSING.getSortOrder());
        }
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);

        recordHistory(ticket.getId(), operatorId, TicketActionEnum.ASSIGN, "current_assignee_id",
                oldAssigneeId == null ? null : oldAssigneeId.toString(),
                dto.getAssigneeId().toString(), dto.getRemark());
    }

    private void applyUpdateFields(Tickets ticket, TicketUpdateDTO dto) {
        if (StringUtils.hasText(dto.getTitle())) {
            ticket.setTitle(dto.getTitle().trim());
        }
        if (dto.getDescription() != null) {
            ticket.setDescription(dto.getDescription().trim());
        }
        if (dto.getCategoryId() != null) {
            assertCategoryExists(dto.getCategoryId());
            ticket.setCategoryId(dto.getCategoryId());
        }
        if (dto.getPriorityId() != null) {
            assertPriorityExists(dto.getPriorityId());
            ticket.setPriorityId(dto.getPriorityId());
        }
        if (dto.getCurrentAssigneeId() != null) {
            ticket.setCurrentAssigneeId(dto.getCurrentAssigneeId());
        }
        if (StringUtils.hasText(dto.getContactPhone())) {
            ticket.setContactPhone(dto.getContactPhone().trim());
        } else if (dto.getContactPhone() != null) {
            // 显式传空字符串表示清空
            ticket.setContactPhone(null);
        }
        if (StringUtils.hasText(dto.getContactEmail())) {
            ticket.setContactEmail(dto.getContactEmail().trim());
        } else if (dto.getContactEmail() != null) {
            ticket.setContactEmail(null);
        }
        if (StringUtils.hasText(dto.getLocation())) {
            ticket.setLocation(dto.getLocation().trim());
        } else if (dto.getLocation() != null) {
            ticket.setLocation(null);
        }
        if (dto.getExpectedResolveAt() != null) {
            ticket.setExpectedResolveAt(dto.getExpectedResolveAt());
        }
    }

    // ===================== 审计历史 =====================

    /**
     * 记录一条工单操作历史。
     */
    private void recordHistory(Long ticketId, Long operatorId, TicketActionEnum action,
                               String fieldName, String oldValue, String newValue, String remark) {
        TicketHistory history = new TicketHistory();
        history.setTicketId(ticketId);
        history.setOperatorId(operatorId);
        history.setOperation(action.getCode());
        history.setFieldName(fieldName);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setRemark(remark);
        history.setCreatedAt(LocalDateTime.now());
        ticketHistoryMapper.insert(history);
    }

    /**
     * 记录单个字段的变更（值未变化则跳过）。
     */
    private void recordFieldChange(Long ticketId, Long operatorId, String fieldName,
                                   Object oldValue, Object newValue) {
        if (oldValue == null && newValue == null) {
            return;
        }
        if (oldValue != null && oldValue.equals(newValue)) {
            return;
        }
        recordHistory(ticketId, operatorId, TicketActionEnum.UPDATE, fieldName,
                oldValue == null ? null : oldValue.toString(),
                newValue == null ? null : newValue.toString(), null);
    }

    /**
     * 对比更新前后工单，逐字段记录变更历史。
     */
    private void recordUpdateHistory(Long ticketId, Long operatorId, Tickets before, Tickets after) {
        recordFieldChange(ticketId, operatorId, "title", before.getTitle(), after.getTitle());
        recordFieldChange(ticketId, operatorId, "description", before.getDescription(), after.getDescription());
        recordFieldChange(ticketId, operatorId, "category_id", before.getCategoryId(), after.getCategoryId());
        recordFieldChange(ticketId, operatorId, "priority_id", before.getPriorityId(), after.getPriorityId());
        recordFieldChange(ticketId, operatorId, "current_assignee_id", before.getCurrentAssigneeId(), after.getCurrentAssigneeId());
        recordFieldChange(ticketId, operatorId, "contact_phone", before.getContactPhone(), after.getContactPhone());
        recordFieldChange(ticketId, operatorId, "contact_email", before.getContactEmail(), after.getContactEmail());
        recordFieldChange(ticketId, operatorId, "location", before.getLocation(), after.getLocation());
        recordFieldChange(ticketId, operatorId, "expected_resolve_at", before.getExpectedResolveAt(), after.getExpectedResolveAt());
    }

    private TicketStatusEnum getStatusBySortOrder(Integer statusId) {
        if (statusId == null) {
            return TicketStatusEnum.DRAFT;
        }
        return Arrays.stream(TicketStatusEnum.values())
                .filter(s -> s.getSortOrder().equals(statusId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.TICKET_STATUS_ERROR, "工单状态异常"));
    }

    /**
     * 校验工单分类是否存在（空值跳过）
     */
    private void assertCategoryExists(Integer categoryId) {
        if (categoryId == null) {
            return;
        }
        if (ticketCategoryMapper.selectCount(
                new LambdaQueryWrapper<TicketCategories>().eq(TicketCategories::getId, categoryId)) == 0) {
            throw new BusinessException(ErrorCodeEnum.TICKET_CATEGORY_NOT_FOUND);
        }
    }

    /**
     * 校验工单优先级是否存在（空值跳过，优先级非必填）
     */
    private void assertPriorityExists(Integer priorityId) {
        if (priorityId == null) {
            return;
        }
        if (ticketPriorityMapper.selectCount(
                new LambdaQueryWrapper<TicketPriorities>().eq(TicketPriorities::getId, priorityId)) == 0) {
            throw new BusinessException(ErrorCodeEnum.TICKET_PRIORITY_NOT_FOUND);
        }
    }

    /**
     * 校验工单来源编码是否存在（空值跳过）
     */
    private void assertSourceExists(String sourceCode) {
        if (!StringUtils.hasText(sourceCode)) {
            return;
        }
        if (ticketSourceMapper.selectCount(
                new LambdaQueryWrapper<TicketSources>().eq(TicketSources::getSourceCode, sourceCode.trim())) == 0) {
            throw new BusinessException(ErrorCodeEnum.TICKET_SOURCE_NOT_FOUND, "工单来源不存在: " + sourceCode);
        }
    }

    private TicketPageVO convertToPageVO(Tickets ticket) {
        if (ticket == null) {
            return null;
        }
        TicketPageVO vo = new TicketPageVO();
        BeanUtils.copyProperties(ticket, vo);
        return vo;
    }

    private TicketVO convertToVO(Tickets ticket) {
        if (ticket == null) {
            return null;
        }
        TicketVO vo = new TicketVO();
        BeanUtils.copyProperties(ticket, vo);
        return vo;
    }

    // ==================== 角色权限 ====================

    /**
     * 解析当前用户的数据范围。
     * 调试期或未鉴权（role 为空）按 ALL 宽松处理；正式启用鉴权后应改为"空角色即无权"。
     */
    private DataScope resolveDataScope() {
        String role = SecurityUtils.getCurrentRole();
        if (role == null) {
            return DataScope.ALL;
        }
        return switch (role) {
            case "REPORTER" -> DataScope.SELF;
            case "HANDLER" -> DataScope.ASSIGNED;
            case "SUPERVISOR" -> DataScope.DEPARTMENT;
            case "ADMIN" -> DataScope.ALL;
            default -> DataScope.ALL;
        };
    }

    /**
     * 当前用户部门ID：Session 从 Principal 取；机器身份（Agent）按 ActingContext.userId 查用户表。
     */
    private Long resolveCurrentDeptId() {
        Authentication authentication = SecurityUtils.getCurrentAuthentication();
        if (SecurityUtils.isAuthenticated() && authentication.getPrincipal() instanceof LoginUserPrincipal principal) {
            return principal.getDeptId();
        }
        Long actingUid = ActingContext.getUserId();
        if (actingUid != null) {
            Users u = userMapper.selectById(actingUid);
            if (u != null) {
                return u.getDeptId();
            }
        }
        return null;
    }

    /**
     * 列表 / 查询的数据范围过滤。
     */
    private void applyDataScope(LambdaQueryWrapper<Tickets> wrapper) {
        DataScope scope = resolveDataScope();
        Long uid = SecurityUtils.getCurrentUserId();
        switch (scope) {
            case SELF -> {
                if (uid != null) {
                    wrapper.eq(Tickets::getCreatorId, uid);
                }
            }
            case ASSIGNED -> {
                if (uid != null) {
                    wrapper.and(w -> w.eq(Tickets::getCreatorId, uid).or().eq(Tickets::getCurrentAssigneeId, uid));
                }
            }
            case DEPARTMENT -> {
                Long deptId = resolveCurrentDeptId();
                if (deptId != null) {
                    wrapper.eq(Tickets::getDeptId, deptId);
                }
            }
            case ALL -> { /* 不过滤 */ }
        }
    }

    /**
     * 查看权限：依据 DataScope 判断当前用户能否查看该工单。
     */
    private void assertViewable(Tickets ticket) {
        String role = SecurityUtils.getCurrentRole();
        if (role == null) {
            return; // 调试期宽松
        }
        DataScope scope = resolveDataScope();
        Long uid = SecurityUtils.getCurrentUserId();
        Long deptId = resolveCurrentDeptId();
        boolean allowed = switch (scope) {
            case SELF -> uid != null && ticket.getCreatorId() != null && ticket.getCreatorId().equals(uid);
            case ASSIGNED -> uid != null && ((ticket.getCreatorId() != null && ticket.getCreatorId().equals(uid))
                    || (ticket.getCurrentAssigneeId() != null && ticket.getCurrentAssigneeId().equals(uid)));
            case DEPARTMENT -> deptId != null && ticket.getDeptId() != null && ticket.getDeptId().equals(deptId);
            case ALL -> true;
        };
        if (!allowed) {
            throw new BusinessException(ErrorCodeEnum.TICKET_NO_PERMISSION);
        }
    }

    /**
     * 修改提交内容权限：
     *  - ADMIN 放行；
     *  - REPORTER 仅未处理前（DRAFT/PENDING）且是本人创建的；
     *  - HANDLER 需是本人创建或分配给本人的；
     *  - SUPERVISOR 需本部门。
     */
    private void assertEditable(Tickets ticket, TicketStatusEnum status) {
        String role = SecurityUtils.getCurrentRole();
        if (role == null) {
            return; // 调试期宽松
        }
        Long uid = SecurityUtils.getCurrentUserId();
        Long deptId = resolveCurrentDeptId();
        switch (role) {
            case "ADMIN" -> { /* 放行 */ }
            case "REPORTER" -> {
                if (status != TicketStatusEnum.DRAFT && status != TicketStatusEnum.PENDING) {
                    throw new BusinessException(ErrorCodeEnum.TICKET_NO_PERMISSION, "已处理的工单不允许报障人修改");
                }
                if (uid == null || ticket.getCreatorId() == null || !ticket.getCreatorId().equals(uid)) {
                    throw new BusinessException(ErrorCodeEnum.TICKET_NO_PERMISSION, "仅能修改本人创建的工单");
                }
            }
            case "HANDLER" -> {
                if (!isOwnerOrAssignee(ticket, uid)) {
                    throw new BusinessException(ErrorCodeEnum.TICKET_NO_PERMISSION, "仅能修改本人创建或分配的工单");
                }
            }
            case "SUPERVISOR" -> {
                if (deptId == null || ticket.getDeptId() == null || !deptId.equals(ticket.getDeptId())) {
                    throw new BusinessException(ErrorCodeEnum.TICKET_NO_PERMISSION, "仅能修改本部门工单");
                }
            }
            default -> throw new BusinessException(ErrorCodeEnum.TICKET_NO_PERMISSION);
        }
    }

    /**
     * 推进状态权限：仅 HANDLER / SUPERVISOR / ADMIN，且需归属本部门或本人。
     */
    private void assertCanProcess(Tickets ticket) {
        String role = SecurityUtils.getCurrentRole();
        if (role == null) {
            return; // 调试期宽松
        }
        if (!"HANDLER".equals(role) && !"SUPERVISOR".equals(role) && !"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCodeEnum.TICKET_NO_PERMISSION, "报障人无权限推进工单状态");
        }
        Long uid = SecurityUtils.getCurrentUserId();
        Long deptId = resolveCurrentDeptId();
        if ("HANDLER".equals(role)) {
            if (!isOwnerOrAssignee(ticket, uid)) {
                throw new BusinessException(ErrorCodeEnum.TICKET_NO_PERMISSION, "仅能处理本人创建或分配的工单");
            }
        } else if ("SUPERVISOR".equals(role)) {
            if (deptId == null || ticket.getDeptId() == null || !deptId.equals(ticket.getDeptId())) {
                throw new BusinessException(ErrorCodeEnum.TICKET_NO_PERMISSION, "仅能处理本部门工单");
            }
        }
    }

    /**
     * 删除权限：仅 ADMIN；调试期放行。
     */
    private void assertCanDelete() {
        String role = SecurityUtils.getCurrentRole();
        if (role == null) {
            return; // 调试期宽松
        }
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCodeEnum.TICKET_NO_PERMISSION, "仅管理员可删除工单");
        }
    }

    /**
     * 撤回权限：仅 REPORTER 本人在未处理前（DRAFT/PENDING）可撤回；ADMIN 放行；调试期放行。
     */
    private void assertCanWithdraw(Tickets ticket, TicketStatusEnum status) {
        String role = SecurityUtils.getCurrentRole();
        if (role == null) {
            return; // 调试期宽松
        }
        Long uid = SecurityUtils.getCurrentUserId();
        switch (role) {
            case "ADMIN" -> { /* 放行 */ }
            case "REPORTER" -> {
                if (status != TicketStatusEnum.DRAFT && status != TicketStatusEnum.PENDING) {
                    throw new BusinessException(ErrorCodeEnum.TICKET_NO_PERMISSION, "已处理的工单不允许撤回");
                }
                if (uid == null || ticket.getCreatorId() == null || !ticket.getCreatorId().equals(uid)) {
                    throw new BusinessException(ErrorCodeEnum.TICKET_NO_PERMISSION, "仅能撤回本人创建的工单");
                }
            }
            default -> throw new BusinessException(ErrorCodeEnum.TICKET_NO_PERMISSION, "仅报障人/管理员可撤回工单");
        }
    }

    /**
     * 派单权限：仅 SUPERVISOR（本部门）/ ADMIN 可派单；调试期放行。
     */
    private void assertCanAssign(Tickets ticket) {
        String role = SecurityUtils.getCurrentRole();
        if (role == null) {
            return; // 调试期宽松
        }
        Long deptId = resolveCurrentDeptId();
        switch (role) {
            case "ADMIN" -> { /* 放行 */ }
            case "SUPERVISOR" -> {
                if (deptId == null || ticket.getDeptId() == null || !deptId.equals(ticket.getDeptId())) {
                    throw new BusinessException(ErrorCodeEnum.TICKET_NO_PERMISSION, "仅能派本部门工单");
                }
            }
            default -> throw new BusinessException(ErrorCodeEnum.TICKET_NO_PERMISSION, "仅主管/管理员可派单");
        }
    }

    private boolean isOwnerOrAssignee(Tickets ticket, Long uid) {
        if (uid == null) {
            return false;
        }
        return (ticket.getCreatorId() != null && ticket.getCreatorId().equals(uid))
                || (ticket.getCurrentAssigneeId() != null && ticket.getCurrentAssigneeId().equals(uid));
    }
}
