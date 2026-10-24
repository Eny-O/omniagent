package com.wj.omniagent.common.utils;

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
        return null;
    }

    /**
     * 获取当前操作人角色编码（REPORTER / HANDLER / SUPERVISOR / ADMIN）。
     * <p>
     * 身份一律来自 JWT 过滤器构建的 LoginUserPrincipal；无则返回 null。
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
        return null;
    }
}
