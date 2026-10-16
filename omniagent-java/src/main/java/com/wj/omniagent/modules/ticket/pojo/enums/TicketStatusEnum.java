package com.wj.omniagent.modules.ticket.pojo.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工单状态枚举
 */
@Getter
@AllArgsConstructor
public enum TicketStatusEnum {

    DRAFT("DRAFT", "草稿", 1),
    PENDING("PENDING", "待处理", 2),
    PROCESSING("PROCESSING", "处理中", 3),
    RESOLVED("RESOLVED", "已解决", 4),
    CLOSED("CLOSED", "已关闭", 5);

    private final String code;
    private final String name;
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
                return target == PENDING || target == PROCESSING || target == RESOLVED || target == CLOSED;
            case PENDING:
                return target == PROCESSING || target == RESOLVED || target == CLOSED;
            case PROCESSING:
                return target == RESOLVED || target == CLOSED;
            case RESOLVED:
                return target == CLOSED;
            case CLOSED:
                return false;
            default:
                return false;
        }
    }

    /**
     * 是否终态
     */
    public boolean isTerminal() {
        return this == CLOSED;
    }
}
