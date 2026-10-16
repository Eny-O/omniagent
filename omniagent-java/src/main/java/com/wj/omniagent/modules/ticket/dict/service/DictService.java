package com.wj.omniagent.modules.ticket.dict.service;

import com.wj.omniagent.modules.ticket.pojo.vo.dict.CategoryOptionVO;
import com.wj.omniagent.modules.ticket.pojo.vo.dict.DictGroupVO;
import com.wj.omniagent.modules.ticket.pojo.vo.dict.PriorityOptionVO;
import com.wj.omniagent.modules.ticket.pojo.vo.dict.StatusOptionVO;

import java.util.List;

/**
 * 工单字典 Service
 */
public interface DictService {

    /**
     * 查询全部字典（分类/状态/优先级）
     */
    DictGroupVO getAllDicts();

    /**
     * 查询工单分类列表
     */
    List<CategoryOptionVO> listCategories();

    /**
     * 查询工单状态列表
     */
    List<StatusOptionVO> listStatuses();

    /**
     * 查询工单优先级列表
     */
    List<PriorityOptionVO> listPriorities();
}
