package com.wj.omniagent.modules.ticket.pojo.enums;

import java.util.Arrays;

/**
 * 工单审计动作枚举，对应 ticket_history.operation 列取值。
 */
public enum TicketActionEnum {
    CREATE("CREATE", "创建工单"),
    UPDATE("UPDATE", "修改工单"),
    ASSIGN("ASSIGN", "派单/转派"),
    STATUS_CHANGE("STATUS_CHANGE", "状态流转"),
    WITHDRAW("WITHDRAW", "撤回工单"),
    DELETE("DELETE", "删除工单"),
    TRANSFER_DEPT("TRANSFER_DEPT", "转部门");

    private final String code;
    private final String desc;

    TicketActionEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static TicketActionEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }
}
