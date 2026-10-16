package com.wj.omniagent.modules.ticket.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wj.omniagent.modules.ticket.mapper.TicketMapper;
import com.wj.omniagent.modules.ticket.pojo.dos.Tickets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工单号生成器。
 *
 * <p>格式：TKT-yyyyMMdd-XXX，每天从 001 开始递增。</p>
 * <p>通过查询当日最大序号保证 JVM 重启后也不会重复；并发冲突由数据库唯一索引 + 上层重试兜底。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketNoGenerator {

    private final TicketMapper ticketMapper;

    private static final String PREFIX = "TKT";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern TICKET_NO_PATTERN = Pattern.compile("^" + PREFIX + "-(\\d{8})-(\\d{3})$");

    /**
     * 生成下一个工单号。
     */
    public String generate() {
        String dateStr = LocalDateTime.now().format(DATE_FORMAT);
        int nextSeq = getNextSequence(dateStr);
        return String.format("%s-%s-%03d", PREFIX, dateStr, nextSeq);
    }

    private int getNextSequence(String dateStr) {
        String prefix = PREFIX + "-" + dateStr + "-";

        LambdaQueryWrapper<Tickets> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(Tickets::getTicketNo, prefix)
                .orderByDesc(Tickets::getTicketNo)
                .last("LIMIT 1");

        Tickets latest;
        try {
            latest = ticketMapper.selectOne(wrapper);
        } catch (Exception e) {
            log.warn("查询当日最大工单号失败，dateStr={}，从 001 开始", dateStr, e);
            return 1;
        }

        if (latest == null || latest.getTicketNo() == null) {
            return 1;
        }

        Matcher matcher = TICKET_NO_PATTERN.matcher(latest.getTicketNo());
        if (matcher.matches()) {
            int seq = Integer.parseInt(matcher.group(2));
            return seq + 1;
        }

        log.warn("最新工单号格式异常，无法解析序号：ticketNo={}", latest.getTicketNo());
        return 1;
    }
}
