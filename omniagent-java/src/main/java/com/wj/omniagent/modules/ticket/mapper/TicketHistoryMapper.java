package com.wj.omniagent.modules.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wj.omniagent.modules.ticket.pojo.dos.TicketHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单操作历史表 Mapper
 */
@Mapper
public interface TicketHistoryMapper extends BaseMapper<TicketHistory> {
}
