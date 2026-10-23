package com.wj.omniagent.modules.ticket.pojo.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工单状态枚举
 */
@Getter
@AllArgsConstructor
public enum TicketStatusEnum {

    // dbId = ticket_statuses.id（工单 status_id 的存储值，前后端状态标识统一用它）
    // sortOrder = ticket_statuses.sort_order（仅显示排序/线性流转用，与 id 无关）
    DRAFT("DRAFT", "草稿", 1, 1),
    TO_ASSIGN("TO_ASSIGN", "待派单", 7, 2),
    PENDING("PENDING", "待处理", 2, 3),
    PROCESSING("PROCESSING", "处理中", 3, 4),
    RESOLVED("RESOLVED", "已解决", 4, 5),
    CLOSED("CLOSED", "已关闭", 5, 6),
    CANCELLED("CANCELLED", "已取消", 6, 7);

    private final String code;
    private final String name;
    /** 数据库字典 ticket_statuses.id（tickets.status_id 存此值） */
    private final Integer dbId;
    /** 显示排序（ticket_statuses.sort_order），不作为状态标识 */
    private final Integer sortOrder;

    /**
     * 根据 code 获取枚举
     */
    public static TicketStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TicketStatusEnum status : values()) {
            if (status.getCode().equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 根据数据库字典 id（tickets.status_id 存储值）获取枚举
     */
    public static TicketStatusEnum fromDbId(Integer dbId) {
        if (dbId == null) {
            return null;
        }
        for (TicketStatusEnum status : values()) {
            if (status.getDbId().equals(dbId)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 校验状态流转是否合法
     */
    public boolean canTransitionTo(TicketStatusEnum target) {
        if (target == null) {
            return false;
        }
        // 相同状态允许幂等
        if (this == target) {
            return true;
        }
        switch (this) {
            case DRAFT:
                return target == TO_ASSIGN || target == PENDING || target == PROCESSING || target == RESOLVED || target == CLOSED || target == CANCELLED;
            case TO_ASSIGN:
                return target == PENDING || target == PROCESSING || target == RESOLVED || target == CLOSED || target == CANCELLED;
            case PENDING:
                return target == PROCESSING || target == RESOLVED || target == CLOSED || target == CANCELLED;
            case PROCESSING:
                return target == RESOLVED || target == CLOSED;
            case RESOLVED:
                return target == CLOSED;
            case CLOSED:
                return false;
            case CANCELLED:
                return false;
            default:
                return false;
        }
    }

    /**
     * 是否终态
     */
    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }
}
