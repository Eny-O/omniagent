package com.wj.omniagent.modules.ticket.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wj.omniagent.common.pojo.result.PageResult;
import com.wj.omniagent.modules.ticket.pojo.dos.Tickets;
import com.wj.omniagent.modules.ticket.pojo.dos.TicketHistory;
import com.wj.omniagent.modules.ticket.pojo.dos.TicketComments;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketCreateDTO;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketQuery;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketUpdateDTO;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketUpdateStatusDTO;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketAssignDTO;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketCommentCreateDTO;
import com.wj.omniagent.modules.ticket.pojo.vo.TicketPageVO;
import com.wj.omniagent.modules.ticket.pojo.vo.TicketVO;

import java.util.List;

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
     * 提交工单（创建者本人 / 管理员）：将草稿(DRAFT)提交为待处理(PENDING)。
     */
    TicketVO submitTicket(Long id, Long operatorId);

    /**
     * 派单（主管 / 管理员）：指定处理人；不再改变工单状态，状态流转由提交/接受处理等环节推进
     */
    void assignTicket(Long id, TicketAssignDTO dto, Long operatorId);

    /**
     * 转部门（主管 / 管理员）：变更工单所属受理部门，并清除原部门指派避免越权滞留。
     */
    void transferDepartment(Long ticketId, Long newDeptId, Long operatorId);

    /**
     * 查询工单操作历史（审计轨迹），按操作时间升序返回。
     * 仅拥有该工单查看权限（DataScope）的用户可见。
     */
    List<TicketHistory> getTicketHistory(Long ticketId);

    /**
     * 添加工单评论 / 备注（承载处理内容、沟通记录、内部备注）。
     * 仅处理人/主管/管理员可评论（报障人不可评论）；调试期(null 角色)宽松放行。
     */
    TicketComments addComment(Long ticketId, TicketCommentCreateDTO dto, Long operatorId);

    /**
     * 查询工单评论列表，按时间升序返回。
     * 报障人仅可见非内部备注；处理人/主管/管理员/调试期可见全部。
     */
    List<TicketComments> listComments(Long ticketId);
}

