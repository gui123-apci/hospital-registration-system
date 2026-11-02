package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.common.enums.UserRole;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.result.ResultCode;
import com.hospital.dto.auth.LoginRequest;
import com.hospital.dto.auth.LoginResponse;
import com.hospital.dto.auth.RegisterRequest;
import com.hospital.dto.auth.UserInfoResponse;
import com.hospital.entity.User;
import com.hospital.mapper.UserMapper;
import com.hospital.security.LoginUser;
import com.hospital.service.AuthService;
import com.hospital.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证业务实现。
 * 密码只以 BCrypt 密文入库，登录校验交给 Spring Security 的 AuthenticationManager。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    private final AuthenticationManager authenticationManager;

    private final JwtUtil jwtUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfoResponse register(RegisterRequest request) {
        // 登录名当前就是手机号，所以查重按 username 判断即可
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getPhone()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getPhone());
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        // 自助注册只能是患者，管理员/医生由后台创建
        user.setRole(UserRole.PATIENT.getCode());
        userMapper.insert(user);

        log.info("患者注册成功: userId={}, username={}", user.getId(), user.getUsername());
        return UserInfoResponse.from(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (AuthenticationException e) {
            // 统一提示“用户名或密码错误”，不区分用户不存在和密码错误，避免账号探测
            log.warn("登录失败: username={}, reason={}", request.getUsername(), e.getMessage());
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }

        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        String token = jwtUtil.generateToken(loginUser);
        UserInfoResponse userInfo = new UserInfoResponse(
                loginUser.getUserId(),
                loginUser.getUsername(),
                loginUser.getPhone(),
                loginUser.getRole(),
                UserRole.of(loginUser.getRole()).getDesc());

        return new LoginResponse(token, "Bearer", jwtUtil.getExpireSeconds(), userInfo);
    }

    @Override
    public UserInfoResponse currentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return UserInfoResponse.from(user);
    }
}
