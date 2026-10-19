package com.wj.omniagent.modules.ticket.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wj.omniagent.common.pojo.result.PageResult;
import com.wj.omniagent.modules.ticket.pojo.dos.Tickets;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketCreateDTO;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketQuery;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketUpdateDTO;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketUpdateStatusDTO;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketAssignDTO;
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
     * 更新工单基本信息
     */
    TicketVO updateTicket(Long id, TicketUpdateDTO dto, Long operatorId);

    /**
     * 更新工单状态
     */
    void updateTicketStatus(Long id, TicketUpdateStatusDTO targetStatusCode, Long operatorId);

    /**
     * 删除工单
     */
    void deleteTicket(Long id);

    /**
     * 撤回工单（报障人未处理前 / 管理员）
     */
    void withdrawTicket(Long id, Long operatorId);

    /**
     * 派单（主管 / 管理员）：指定处理人并进入处理中
     */
    void assignTicket(Long id, TicketAssignDTO dto, Long operatorId);
}

