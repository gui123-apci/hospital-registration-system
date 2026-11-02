package com.hospital.service;

import com.hospital.dto.auth.LoginRequest;
import com.hospital.dto.auth.LoginResponse;
import com.hospital.dto.auth.RegisterRequest;
import com.hospital.dto.auth.UserInfoResponse;

/**
 * 认证相关业务：注册、登录、获取当前登录用户。
 */
public interface AuthService {

    /** 患者注册，登录名直接复用手机号 */
    UserInfoResponse register(RegisterRequest request);

    /** 登录成功返回 JWT */
    LoginResponse login(LoginRequest request);

    /** 按用户主键查询最新用户信息，供 /auth/me 使用 */
    UserInfoResponse currentUser(Long userId);
}
