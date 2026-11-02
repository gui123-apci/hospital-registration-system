package com.hospital.security;

import com.hospital.common.enums.UserRole;
import com.hospital.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security 使用的登录用户对象。
 * 把数据库里的 tb_user 包装成 UserDetails，供鉴权与控制器注入使用。
 */
@Getter
public class LoginUser implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final String phone;

    /** 角色值：0患者 1管理员 2医生 */
    private final Integer role;

    public LoginUser(Long userId, String username, String password, String phone, Integer role) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.phone = phone;
        this.role = role;
    }

    public static LoginUser from(User user) {
        return new LoginUser(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getPhone(),
                user.getRole());
    }

    /**
     * 权限列表，Spring Security 的角色校验认 ROLE_XXX 这个前缀。
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(UserRole.of(role).authority()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
