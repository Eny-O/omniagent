package com.wj.omniagent.modules.ticket.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 独立序列访问 Mapper。
 *
 * <p>工单号序号由独立的 PostgreSQL 序列 {@code ticket_no_seq} 提供，而非读取表内最大号。
 * {@code nextval} 是数据库层的原子操作，并发事务各自拿到不同值，从根本上消除了
 * “读最大号 + 1” 的竞态窗口。</p>
 */
@Mapper
public interface SequenceMapper {

    @Select("SELECT nextval('ticket_no_seq')")
    long nextTicketNoSerial();
}
