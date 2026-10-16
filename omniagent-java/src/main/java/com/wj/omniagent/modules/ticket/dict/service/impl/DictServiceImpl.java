package com.wj.omniagent.modules.ticket.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wj.omniagent.modules.ticket.dict.service.DictService;
import com.wj.omniagent.modules.ticket.mapper.TicketCategoryMapper;
import com.wj.omniagent.modules.ticket.mapper.TicketPriorityMapper;
import com.wj.omniagent.modules.ticket.mapper.TicketStatusMapper;
import com.wj.omniagent.modules.ticket.pojo.dos.TicketCategories;
import com.wj.omniagent.modules.ticket.pojo.dos.TicketPriorities;
import com.wj.omniagent.modules.ticket.pojo.dos.TicketStatuses;
import com.wj.omniagent.modules.ticket.pojo.vo.dict.CategoryOptionVO;
import com.wj.omniagent.modules.ticket.pojo.vo.dict.DictGroupVO;
import com.wj.omniagent.modules.ticket.pojo.vo.dict.PriorityOptionVO;
import com.wj.omniagent.modules.ticket.pojo.vo.dict.StatusOptionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 工单字典 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private final TicketCategoryMapper categoryMapper;

    private final TicketStatusMapper statusMapper;

    private final TicketPriorityMapper priorityMapper;

    @Override
    public DictGroupVO getAllDicts() {
        DictGroupVO group = new DictGroupVO();
        group.setCategories(listCategories());
        group.setStatuses(listStatuses());
        group.setPriorities(listPriorities());
        return group;
    }

    @Override
    public List<CategoryOptionVO> listCategories() {
        return categoryMapper.selectList(
                        new LambdaQueryWrapper<TicketCategories>().orderByAsc(TicketCategories::getSortOrder))
                .stream()
                .map(this::toCategoryVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<StatusOptionVO> listStatuses() {
        return statusMapper.selectList(
                        new LambdaQueryWrapper<TicketStatuses>().orderByAsc(TicketStatuses::getSortOrder))
                .stream()
                .map(this::toStatusVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<PriorityOptionVO> listPriorities() {
        return priorityMapper.selectList(
                        new LambdaQueryWrapper<TicketPriorities>().orderByAsc(TicketPriorities::getSortOrder))
                .stream()
                .map(this::toPriorityVO)
                .collect(Collectors.toList());
    }

    private CategoryOptionVO toCategoryVO(TicketCategories e) {
        CategoryOptionVO vo = new CategoryOptionVO();
        vo.setValue(e.getId());
        vo.setLabel(e.getCategoryName());
        vo.setParentId(e.getParentId());
        vo.setSortOrder(e.getSortOrder());
        return vo;
    }

    private StatusOptionVO toStatusVO(TicketStatuses e) {
        StatusOptionVO vo = new StatusOptionVO();
        vo.setValue(e.getId());
        vo.setLabel(e.getStatusName());
        vo.setIsTerminal(e.getIsTerminal());
        vo.setSortOrder(e.getSortOrder());
        return vo;
    }

    private PriorityOptionVO toPriorityVO(TicketPriorities e) {
        PriorityOptionVO vo = new PriorityOptionVO();
        vo.setValue(e.getId());
        vo.setLabel(e.getPriorityName());
        vo.setResponseSlaMinutes(e.getResponseSlaMinutes());
        vo.setResolveSlaMinutes(e.getResolveSlaMinutes());
        vo.setSortOrder(e.getSortOrder());
        return vo;
    }
}
