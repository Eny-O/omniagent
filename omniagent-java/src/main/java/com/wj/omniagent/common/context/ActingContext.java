package com.wj.omniagent.common.context;

/**
 * 操作人上下文（请求级）。
 * <p>
 * 用于承载"当前操作人"的身份，来源有二：
 * 1) 浏览器 / Session 登录用户 —— 由 SecurityUtils 从 LoginUserPrincipal 直接读取，不经过本上下文；
 * 2) 机器身份（Agent）—— 由 ActingContextInterceptor 解析 X-Acting-User / X-Acting-Role 头写入。
 * <p>
 * 第3批的数据范围过滤与能力校验将通过 SecurityUtils 统一读取：优先 Session，其次本上下文。
 */
public class ActingContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> MACHINE = new ThreadLocal<>();

    public static void set(Long userId, String role, boolean machine) {
        USER_ID.set(userId);
        ROLE.set(role);
        MACHINE.set(machine);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static String getRole() {
        return ROLE.get();
    }

    public static boolean isMachine() {
        return Boolean.TRUE.equals(MACHINE.get());
    }

    public static void clear() {
        USER_ID.remove();
        ROLE.remove();
        MACHINE.remove();
    }
}
