package com.wj.omniagent.modules.ticket.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wj.omniagent.common.pojo.result.PageResult;
import com.wj.omniagent.modules.ticket.pojo.dos.Tickets;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketCreateDTO;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketQuery;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketUpdateStatusDTO;
import com.wj.omniagent.modules.ticket.pojo.vo.TicketPageVO;
import com.wj.omniagent.modules.ticket.pojo.vo.TicketVO;

/**
 * 工单 Service
 */
public interface TicketService extends IService<Tickets> {

    /**
     * 创建工单
     */
    TicketVO createTicket(TicketCreateDTO request, Long operatorId);

    /**
     * 根据ID查询工单详情
     */
    TicketVO getTicketById(Long id);

    /**
     * 分页查询工单
     */
    PageResult<TicketPageVO> pageTickets(TicketQuery query);

    /**
     * 更新工单状态
     */
    void updateTicketStatus(Long id, TicketUpdateStatusDTO targetStatusCode, Long operatorId);

    /**
     * 删除工单
     */
    void deleteTicket(Long id);
}

