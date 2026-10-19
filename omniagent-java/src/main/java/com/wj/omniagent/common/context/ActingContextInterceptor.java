package com.wj.omniagent.common.context;

import com.wj.omniagent.common.enums.ErrorCodeEnum;
import com.wj.omniagent.common.enums.UserRoleEnum;
import com.wj.omniagent.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 操作人上下文拦截器。
 * <p>
 * 在预检阶段解析机器身份（Agent）请求头：
 * - X-API-Key：机器密钥，配置后必须携带且匹配（调试期未配置则不校验）；
 * - X-Acting-Role：业务角色编码（REPORTER/HANDLER/SUPERVISOR/ADMIN），机器身份必填；
 * - X-Acting-User：机器身份所代表的具体用户ID（数字），用于数据范围 SELF 判断，可空。
 * <p>
 * 浏览器 / Session 登录用户不经过本拦截器设值，由 SecurityUtils 从 Principal 读取。
 * 本拦截器不触碰 SecurityConfig（全放行调试不受影响）。
 */
@Component
public class ActingContextInterceptor implements HandlerInterceptor {

    /** 机器密钥；未配置（空）则不校验 API Key，兼容调试期。 */
    @Value("${acting.api-key:}")
    private String expectedApiKey;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String apiKey = request.getHeader("X-API-Key");
        // 无 API Key 头的请求视为浏览器 / Session / 调试请求，不在此构造机器身份
        if (!StringUtils.hasText(apiKey)) {
            return true;
        }

        // 配置了机器密钥则必须匹配
        if (StringUtils.hasText(expectedApiKey) && !expectedApiKey.equals(apiKey)) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED, "API Key 校验失败");
        }

        String actingRole = request.getHeader("X-Acting-Role");
        if (!StringUtils.hasText(actingRole)) {
            throw new BusinessException(ErrorCodeEnum.ROLE_INVALID, "机器身份必须携带 X-Acting-Role 头");
        }
        UserRoleEnum roleEnum = UserRoleEnum.fromCode(actingRole);
        if (roleEnum == null) {
            throw new BusinessException(ErrorCodeEnum.ROLE_INVALID, "非法的业务角色编码: " + actingRole);
        }

        Long userId = null;
        String actingUser = request.getHeader("X-Acting-User");
        if (StringUtils.hasText(actingUser)) {
            try {
                userId = Long.parseLong(actingUser.trim());
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "X-Acting-User 须为数字用户ID");
            }
        }

        ActingContext.set(userId, roleEnum.getCode(), true);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        ActingContext.clear();
    }
}
