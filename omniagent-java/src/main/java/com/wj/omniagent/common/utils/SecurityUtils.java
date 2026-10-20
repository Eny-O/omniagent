package com.wj.omniagent.common.utils;

import com.wj.omniagent.common.context.ActingContext;
import com.wj.omniagent.modules.user.service.security.LoginUserPrincipal;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Security 上下文工具类
 * <p>
 * 提供获取当前登录用户相关信息的方法。
 */
public class SecurityUtils {

    /**
     * 获取当前认证对象
     */
    public static Authentication getCurrentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * 判断当前是否已认证（非匿名）
     */
    public static boolean isAuthenticated() {
        Authentication authentication = getCurrentAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    /**
     * 获取当前登录用户名
     *
     * @return 用户名，未登录返回 null
     */
    public static String getCurrentUsername() {
        Authentication authentication = getCurrentAuthentication();
        if (!isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        if (principal instanceof String) {
            return (String) principal;
        }
        return null;
    }

    /**
     * 获取当前登录用户ID
     *
     * @return 用户ID，未登录返回 null
     */
    public static Long getCurrentUserId() {
        Authentication authentication = getCurrentAuthentication();
        if (isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof LoginUserPrincipal) {
                return ((LoginUserPrincipal) principal).getId();
            }
        }
        // 回退：机器身份（Agent）通过 X-Acting-User 透传的具体用户ID
        return ActingContext.getUserId();
    }

    /**
     * 获取当前操作人角色编码（REPORTER / HANDLER / SUPERVISOR / ADMIN）。
     * <p>
     * 优先取 Session 登录用户（LoginUserPrincipal）的角色；
     * 否则取机器身份（ActingContext）透传的角色；
     * 均无则返回 null（调试期未鉴权时为 null，由调用方决定宽松 / 严格策略）。
     *
     * @return 角色编码，无法确定返回 null
     */
    public static String getCurrentRole() {
        Authentication authentication = getCurrentAuthentication();
        if (isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof LoginUserPrincipal) {
                String role = ((LoginUserPrincipal) principal).getRole();
                if (role != null) {
                    return role.trim();
                }
            }
        }
        String actingRole = ActingContext.getRole();
        return actingRole != null ? actingRole.trim() : null;
    }
}
