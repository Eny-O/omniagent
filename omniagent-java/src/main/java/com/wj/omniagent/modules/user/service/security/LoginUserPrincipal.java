package com.wj.omniagent.modules.user.service.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * 登录用户 Principal
 * <p>
 * 扩展 UserDetails，携带用户ID，便于业务层直接获取当前用户ID。
 */
@Data
public class LoginUserPrincipal implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long id;

    private final String username;

    @JsonIgnore
    private final String password;

    private final Integer status;

    private final String role;

    private final Long deptId;

    @JsonIgnore
    private final Collection<? extends GrantedAuthority> authorities;

    @JsonIgnore
    private final boolean accountNonExpired;

    @JsonIgnore
    private final boolean accountNonLocked;

    @JsonIgnore
    private final boolean credentialsNonExpired;

    private final boolean enabled;

    public LoginUserPrincipal(Long id, String username, String password, Integer status, String role, Long deptId,
                              Collection<? extends GrantedAuthority> authorities,
                              boolean enabled) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.status = status;
        this.role = role;
        this.deptId = deptId;
        this.authorities = authorities;
        this.accountNonExpired = true;
        this.accountNonLocked = true;
        this.credentialsNonExpired = true;
        this.enabled = enabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
