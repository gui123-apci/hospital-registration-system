package com.hospital.service;

import com.hospital.dto.auth.LoginDTO;
import com.hospital.dto.auth.RegisterDTO;
import com.hospital.vo.auth.LoginVO;
import com.hospital.vo.auth.UserInfoVO;

/**
 * 认证相关业务：注册、登录、获取当前登录用户。
 */
public interface AuthService {

    /** 患者注册，登录名直接复用手机号 */
    UserInfoVO register(RegisterDTO dto);

    /** 登录成功返回 JWT */
    LoginVO login(LoginDTO dto);

    /** 按用户主键查询最新用户信息，供 /auth/me 使用 */
    UserInfoVO currentUser(Long userId);
}
