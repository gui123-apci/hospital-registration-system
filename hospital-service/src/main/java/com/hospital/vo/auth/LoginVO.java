package com.hospital.vo.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功返回：JWT + 用户信息。
 * 前端后续请求放在请求头：Authorization: Bearer {token}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    private String token;

    /** 固定 Bearer，前端拼接 Authorization 头时使用 */
    private String tokenType;

    /** token 有效期（秒） */
    private Long expiresIn;

    private UserInfoVO user;
}
