package com.wj.omniagent.modules.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wj.omniagent.modules.ticket.pojo.dos.TicketComments;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单评论表 Mapper
 */
@Mapper
public interface TicketCommentsMapper extends BaseMapper<TicketComments> {
}
