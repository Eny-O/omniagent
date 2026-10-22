package com.wj.omniagent.modules.ticket.util;

import com.wj.omniagent.modules.ticket.mapper.SequenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 工单号生成器（方案 A：PostgreSQL 序列取号）。
 *
 * <p>格式：TKT-yyyyMMdd-NNN。序号部分由独立序列 {@code ticket_no_seq} 的 {@code nextval} 提供，
 * 该操作在数据库层是原子的，并发插入不会拿到重复序号；因此工单号唯一性由
 * “序列原子取号 + ticket_no 唯一索引兜底” 双重保证，无需应用层重试。</p>
 *
 * <p>注意：序号是全局单调递增、不按日重置（即 TKT-20261021-1000 之后是 TKT-20261022-1001）。
 * 如需 “每日从 001 重置”，应改用计数器表 + SELECT FOR UPDATE（方案 B）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketNoGenerator {

    private final SequenceMapper sequenceMapper;

    private static final String PREFIX = "TKT";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 生成下一个工单号。
     */
    public String generate() {
        long serial = sequenceMapper.nextTicketNoSerial();
        String dateStr = LocalDate.now().format(DATE_FORMAT);
        return String.format("%s-%s-%03d", PREFIX, dateStr, serial);
    }
}
