package com.wj.omniagent.modules.ticket.controller;

import com.wj.omniagent.common.pojo.result.PageResult;
import com.wj.omniagent.common.pojo.result.ResponseResult;
import com.wj.omniagent.common.utils.SecurityUtils;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketCreateDTO;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketQuery;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketUpdateDTO;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketUpdateStatusDTO;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketAssignDTO;
import com.wj.omniagent.modules.ticket.pojo.dto.TicketCommentCreateDTO;
import com.wj.omniagent.modules.ticket.pojo.dos.TicketHistory;
import com.wj.omniagent.modules.ticket.pojo.dos.TicketComments;
import com.wj.omniagent.modules.ticket.pojo.vo.TicketPageVO;
import com.wj.omniagent.modules.ticket.pojo.vo.TicketVO;
import com.wj.omniagent.modules.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工单管理 Controller
 */
@Tag(name = "工单管理", description = "工单核心 CRUD 接口")
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Validated
public class TicketController {

    private final TicketService ticketService;

    @Operation(summary = "创建工单")
    @PostMapping
    public ResponseResult<TicketVO> create(@RequestBody @Valid TicketCreateDTO dto) {
        TicketVO vo = ticketService.createTicket(dto, getCurrentUserId());
        return ResponseResult.success(vo);
    }

    @Operation(summary = "查询工单详情")
    @GetMapping("/{id}")
    public ResponseResult<TicketVO> getById(
            @Parameter(description = "工单ID") @PathVariable Long id) {
        return ResponseResult.success(ticketService.getTicketById(id));
    }

    @Operation(summary = "查询工单操作历史（审计轨迹）")
    @GetMapping("/{id}/history")
    public ResponseResult<List<TicketHistory>> getHistory(
            @Parameter(description = "工单ID") @PathVariable Long id) {
        return ResponseResult.success(ticketService.getTicketHistory(id));
    }

    @Operation(summary = "添加工单评论 / 备注")
    @PostMapping("/{id}/comments")
    public ResponseResult<TicketComments> addComment(
            @Parameter(description = "工单ID") @PathVariable Long id,
            @RequestBody @Valid TicketCommentCreateDTO dto) {
        return ResponseResult.success(ticketService.addComment(id, dto, getCurrentUserId()));
    }

    @Operation(summary = "查询工单评论列表")
    @GetMapping("/{id}/comments")
    public ResponseResult<List<TicketComments>> listComments(
            @Parameter(description = "工单ID") @PathVariable Long id) {
        return ResponseResult.success(ticketService.listComments(id));
    }

    @Operation(summary = "分页查询工单列表")
    @GetMapping("/page")
    public ResponseResult<PageResult<TicketPageVO>> page(TicketQuery query) {
        return ResponseResult.success(ticketService.pageTickets(query));
    }

    @Operation(summary = "更新工单基本信息")
    @PutMapping("/{id}")
    public ResponseResult<TicketVO> update(
            @Parameter(description = "工单ID") @PathVariable Long id,
            @RequestBody @Valid TicketUpdateDTO dto) {
        TicketVO vo = ticketService.updateTicket(id, dto, getCurrentUserId());
        return ResponseResult.success(vo);
    }

    @Operation(summary = "更新工单状态")
    @PutMapping("/{id}/status")
    public ResponseResult<Void> updateStatus(
            @Parameter(description = "工单ID") @PathVariable Long id,
            @RequestBody @Valid TicketUpdateStatusDTO targetStatusCode) {
        ticketService.updateTicketStatus(id, targetStatusCode, getCurrentUserId());
        return ResponseResult.success();
    }

    @Operation(summary = "删除工单")
    @DeleteMapping("/{id}")
    public ResponseResult<Void> delete(
            @Parameter(description = "工单ID") @PathVariable Long id) {
        ticketService.deleteTicket(id);
        return ResponseResult.success();
    }

    @Operation(summary = "撤回工单（报障人未处理前）")
    @PostMapping("/{id}/withdraw")
    public ResponseResult<TicketVO> withdraw(
            @Parameter(description = "工单ID") @PathVariable Long id) {
        ticketService.withdrawTicket(id, getCurrentUserId());
        return ResponseResult.success(ticketService.getTicketById(id));
    }

    @Operation(summary = "派单（主管 / 管理员）")
    @PostMapping("/{id}/assign")
    public ResponseResult<TicketVO> assign(
            @Parameter(description = "工单ID") @PathVariable Long id,
            @RequestBody @Valid TicketAssignDTO dto) {
        ticketService.assignTicket(id, dto, getCurrentUserId());
        return ResponseResult.success(ticketService.getTicketById(id));
    }

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId() {
        return SecurityUtils.getCurrentUserId();
    }
}
