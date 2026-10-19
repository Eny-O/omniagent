package com.wj.omniagent.common.enums;

import java.util.Arrays;

/**
 * 用户角色枚举。
 * <p>
 * 注意：AGENT 不是业务角色，它是贯穿所有角色的智能辅助层，
 * 后端授权对象始终是下列业务角色。users.role 列直接存储枚举 code。
 */
public enum UserRoleEnum {
    REPORTER("REPORTER", "报障人"),
    HANDLER("HANDLER", "处理人"),
    SUPERVISOR("SUPERVISOR", "主管"),
    ADMIN("ADMIN", "管理员");

    private final String code;
    private final String desc;

    UserRoleEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 由角色编码解析枚举；null / 空 / 未知值返回 null，调用方决定回退策略。
     */
    public static UserRoleEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }

    /** Spring Security 授权字符串，形如 ROLE_REPORTER。 */
    public String toAuthority() {
        return "ROLE_" + code;
    }
}
