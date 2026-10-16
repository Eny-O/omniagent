package com.wj.omniagent.modules.ticket.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wj.omniagent.common.pojo.result.PageResult;
import com.wj.omniagent.modules.ticket.mapper.TicketMapper;
import com.wj.omniagent.modules.ticket.pojo.dos.Tickets;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketCreateDTO;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketQuery;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketUpdateStatusDTO;
import com.wj.omniagent.modules.ticket.pojo.enums.TicketStatusEnum;
import com.wj.omniagent.modules.ticket.pojo.vo.TicketPageVO;
import com.wj.omniagent.modules.ticket.pojo.vo.TicketVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 工单 Service 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    @Mock
    private TicketMapper ticketMapper;

    @InjectMocks
    private TicketServiceImpl ticketService;

    private TicketCreateDTO createDTO;

    @BeforeEach
    void setUp() {
        createDTO = new TicketCreateDTO();
        createDTO.setTitle("电脑蓝屏无法启动");
        createDTO.setDescription("开机后蓝屏，错误代码0x00001");
        createDTO.setCategoryId(1);
        createDTO.setPriorityId(1);
        createDTO.setCurrentAssigneeId(2L);
        createDTO.setSource("WEB");
    }

    // ==================== 创建工单 ====================

    @Test
    @DisplayName("创建工单成功")
    void createTicket_Success() {
        // given
        when(ticketMapper.insert(any(Tickets.class))).thenAnswer(invocation -> {
            Tickets ticket = invocation.getArgument(0);
            ticket.setId(1L);
            return 1;
        });

        // when
        TicketVO result = ticketService.createTicket(createDTO, 1L);

        // then
        assertNotNull(result);
        assertEquals("电脑蓝屏无法启动", result.getTitle());
        assertEquals(TicketStatusEnum.DRAFT.getSortOrder(), result.getStatusId());
        assertEquals(1L, result.getCreatorId());
        assertEquals("WEB", result.getSource());
        assertNotNull(result.getTicketNo());
        assertTrue(result.getTicketNo().startsWith("TKT-"));
        verify(ticketMapper, times(1)).insert(any(Tickets.class));
    }

    @Test
    @DisplayName("创建工单-标题为空时抛出异常")
    void createTicket_TitleBlank_ThrowsException() {
        // given
        createDTO.setTitle("  ");

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ticketService.createTicket(createDTO, 1L)
        );
        assertEquals("工单标题不能为空", exception.getMessage());
        verify(ticketMapper, never()).insert(any(Tickets.class));
    }

    @Test
    @DisplayName("创建工单-工单号重复时重试成功")
    void createTicket_DuplicateTicketNo_RetrySuccess() {
        // given
        when(ticketMapper.insert(any(Tickets.class)))
                .thenThrow(DuplicateKeyException.class)
                .thenAnswer(invocation -> {
                    Tickets ticket = invocation.getArgument(0);
                    ticket.setId(1L);
                    return 1;
                });

        // when
        TicketVO result = ticketService.createTicket(createDTO, 1L);

        // then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(ticketMapper, times(2)).insert(any(Tickets.class));
    }

    @Test
    @DisplayName("创建工单-工单号重试3次后仍失败则抛出异常")
    void createTicket_DuplicateTicketNo_ExhaustRetry_ThrowsException() {
        // given
        when(ticketMapper.insert(any(Tickets.class))).thenThrow(DuplicateKeyException.class);

        // when & then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> ticketService.createTicket(createDTO, 1L)
        );
        assertEquals("工单号生成失败，请重试", exception.getMessage());
        verify(ticketMapper, times(3)).insert(any(Tickets.class));
    }

    // ==================== 查询工单 ====================

    @Test
    @DisplayName("根据ID查询工单成功")
    void getTicketById_Success() {
        // given
        Tickets ticket = buildTicket(1L, "TKT-20261015-001", TicketStatusEnum.DRAFT);
        when(ticketMapper.selectById(1L)).thenReturn(ticket);

        // when
        TicketVO result = ticketService.getTicketById(1L);

        // then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("TKT-20261015-001", result.getTicketNo());
        assertEquals("电脑蓝屏", result.getTitle());
    }

    @Test
    @DisplayName("根据ID查询工单-不存在时抛出异常")
    void getTicketById_NotFound_ThrowsException() {
        // given
        when(ticketMapper.selectById(999L)).thenReturn(null);

        // when & then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> ticketService.getTicketById(999L)
        );
        assertEquals("资源不存在", exception.getMessage());
    }

    // ==================== 分页查询 ====================

    @Test
    @DisplayName("分页查询工单成功")
    @SuppressWarnings("unchecked")
    void pageTickets_Success() {
        // given
        TicketQuery query = new TicketQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setKeyword("蓝屏");

        Tickets ticket = buildTicket(1L, "TKT-20261015-001", TicketStatusEnum.DRAFT);
        Page<Tickets> page = new Page<>(1, 10, 1);
        page.setRecords(Collections.singletonList(ticket));

        when(ticketMapper.selectPage(any(Page.class), any())).thenReturn(page);

        // when
        PageResult<TicketPageVO> result = ticketService.pageTickets(query);

        // then
        assertNotNull(result);
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getPageNum());
        assertEquals(10, result.getPageSize());
        assertEquals(1, result.getRecords().size());
        assertEquals("TKT-20261015-001", result.getRecords().get(0).getTicketNo());
    }

    @Test
    @DisplayName("分页查询-默认分页参数修正")
    @SuppressWarnings("unchecked")
    void pageTickets_DefaultPageParams() {
        // given
        TicketQuery query = new TicketQuery();
        // pageNum/pageSize 均为 null，应被修正为 1/10

        Page<Tickets> page = new Page<>(1, 10, 0);
        page.setRecords(Collections.emptyList());

        when(ticketMapper.selectPage(any(Page.class), any())).thenReturn(page);

        // when
        PageResult<TicketPageVO> result = ticketService.pageTickets(query);

        // then
        assertEquals(1, result.getPageNum());
        assertEquals(10, result.getPageSize());
    }

    // ==================== 更新状态 ====================

    @Test
    @DisplayName("更新工单状态成功")
    void updateTicketStatus_Success() {
        // given
        Tickets ticket = buildTicket(1L, "TKT-20261015-001", TicketStatusEnum.DRAFT);
        when(ticketMapper.selectById(1L)).thenReturn(ticket);
        when(ticketMapper.updateById(any(Tickets.class))).thenReturn(1);

        TicketUpdateStatusDTO dto = new TicketUpdateStatusDTO();
        dto.setTargetStatusCode("PENDING");

        // when
        ticketService.updateTicketStatus(1L, dto, 1L);

        // then
        assertEquals(TicketStatusEnum.PENDING.getSortOrder(), ticket.getStatusId());
        verify(ticketMapper, times(1)).updateById(ticket);
    }

    @Test
    @DisplayName("更新工单状态-相同状态幂等处理")
    void updateTicketStatus_SameStatus_Idempotent() {
        // given
        Tickets ticket = buildTicket(1L, "TKT-20261015-001", TicketStatusEnum.DRAFT);
        when(ticketMapper.selectById(1L)).thenReturn(ticket);

        TicketUpdateStatusDTO dto = new TicketUpdateStatusDTO();
        dto.setTargetStatusCode("DRAFT");

        // when
        ticketService.updateTicketStatus(1L, dto, 1L);

        // then
        verify(ticketMapper, never()).updateById(any(Tickets.class));
    }

    @Test
    @DisplayName("更新工单状态-非法流转时抛出异常")
    void updateTicketStatus_IllegalTransition_ThrowsException() {
        // given
        Tickets ticket = buildTicket(1L, "TKT-20261015-001", TicketStatusEnum.CLOSED);
        when(ticketMapper.selectById(1L)).thenReturn(ticket);

        TicketUpdateStatusDTO dto = new TicketUpdateStatusDTO();
        dto.setTargetStatusCode("PENDING");

        // when & then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> ticketService.updateTicketStatus(1L, dto, 1L)
        );
        assertTrue(exception.getMessage().contains("状态流转不合法"));
        verify(ticketMapper, never()).updateById(any(Tickets.class));
    }

    // ==================== 删除工单 ====================

    @Test
    @DisplayName("删除工单成功")
    void deleteTicket_Success() {
        // given
        Tickets ticket = buildTicket(1L, "TKT-20261015-001", TicketStatusEnum.DRAFT);
        when(ticketMapper.selectById(1L)).thenReturn(ticket);
        when(ticketMapper.deleteById(1L)).thenReturn(1);

        // when
        ticketService.deleteTicket(1L);

        // then
        verify(ticketMapper, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("删除工单-不存在时抛出异常")
    void deleteTicket_NotFound_ThrowsException() {
        // given
        when(ticketMapper.selectById(999L)).thenReturn(null);

        // when & then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> ticketService.deleteTicket(999L)
        );
        assertEquals("资源不存在", exception.getMessage());
        verify(ticketMapper, never()).deleteById(any(Long.class));
    }

    // ==================== 私有工具方法 ====================

    private Tickets buildTicket(Long id, String ticketNo, TicketStatusEnum status) {
        Tickets ticket = new Tickets();
        ticket.setId(id);
        ticket.setTicketNo(ticketNo);
        ticket.setTitle("电脑蓝屏");
        ticket.setDescription("开机后蓝屏");
        ticket.setStatusId(status.getSortOrder());
        ticket.setPriorityId(1);
        ticket.setCategoryId(1);
        ticket.setCreatorId(1L);
        ticket.setSource("WEB");
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());
        return ticket;
    }
}
