package com.wj.omniagent.modules.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wj.omniagent.common.enums.ErrorCodeEnum;
import com.wj.omniagent.common.pojo.result.PageResult;

import com.wj.omniagent.modules.ticket.mapper.TicketMapper;
import com.wj.omniagent.modules.ticket.pojo.dos.Tickets;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketCreateDTO;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketQuery;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketUpdateStatusDTO;
import com.wj.omniagent.modules.ticket.pojo.enums.TicketStatusEnum;
import com.wj.omniagent.modules.ticket.pojo.vo.TicketPageVO;
import com.wj.omniagent.modules.ticket.pojo.vo.TicketVO;
import com.wj.omniagent.modules.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl extends ServiceImpl<TicketMapper, Tickets> implements TicketService {

    private final TicketMapper ticketMapper;

    private final AtomicInteger sequence = new AtomicInteger(0);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketVO createTicket(TicketCreateDTO dto, Long operatorId) {
        Assert.notNull(dto, "创建工单请求不能为空");
        Assert.hasText(dto.getTitle(), "工单标题不能为空");

        Tickets ticket = new Tickets();
        BeanUtils.copyProperties(dto, ticket);

        // 处理默认值和特殊字段
        if (StringUtils.hasText(dto.getTitle())) {
            ticket.setTitle(dto.getTitle().trim());
        }
        ticket.setStatusId(TicketStatusEnum.DRAFT.getSortOrder());
        ticket.setCreatorId(operatorId);
        if (!StringUtils.hasText(dto.getSource())) {
            ticket.setSource("WEB");
        } else {
            ticket.setSource(dto.getSource().trim());
        }
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());

        // 生成工单号，数据库唯一索引 + 重试保证唯一性
        int retry = 0;
        while (retry < 3) {
            ticket.setTicketNo(generateTicketNo());
            try {
                ticketMapper.insert(ticket);
                break;
            } catch (DuplicateKeyException e) {
                log.warn("工单号重复，重新生成，ticketNo={}", ticket.getTicketNo());
                retry++;
                if (retry >= 3) {
                    throw new RuntimeException("工单号生成失败，请重试");
                }
            }
        }

        return convertToVO(ticket);
    }

    @Override
    public TicketVO getTicketById(Long id) {
        Assert.notNull(id, "工单ID不能为空");
        Tickets ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new RuntimeException(ErrorCodeEnum.NOT_FOUND.getMessage());
        }
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
    public void updateTicketStatus(Long id, TicketUpdateStatusDTO ticketUpdateStatusDTO, Long operatorId) {
        Assert.notNull(id, "工单ID不能为空");
        Assert.notNull(ticketUpdateStatusDTO, "目标状态不能为空");

        Tickets ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new RuntimeException(ErrorCodeEnum.NOT_FOUND.getMessage());
        }

        TicketStatusEnum currentStatus = getStatusBySortOrder(ticket.getStatusId());
        TicketStatusEnum targetStatus = TicketStatusEnum.fromCode(ticketUpdateStatusDTO.getTargetStatusCode());
        if (targetStatus == null) {
            throw new RuntimeException("目标状态不存在");
        }

        // 幂等：相同状态直接返回
        if (currentStatus == targetStatus) {
            return;
        }

        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new RuntimeException(
                    String.format("状态流转不合法：%s -> %s", currentStatus.getName(), targetStatus.getName())
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

        // TODO: 记录 ticket_history（后续补充）
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTicket(Long id) {
        Assert.notNull(id, "工单ID不能为空");
        Tickets ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new RuntimeException(ErrorCodeEnum.NOT_FOUND.getMessage());
        }
        ticketMapper.deleteById(id);
    }

    private String generateTicketNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int seq = sequence.incrementAndGet() % 1000;
        return String.format("TKT-%s-%03d", date, seq);
    }

    private TicketStatusEnum getStatusBySortOrder(Integer statusId) {
        if (statusId == null) {
            return TicketStatusEnum.DRAFT;
        }
        return Arrays.stream(TicketStatusEnum.values())
                .filter(s -> s.getSortOrder().equals(statusId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("工单状态异常"));
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
}
