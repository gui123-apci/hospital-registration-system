package com.hospital.controller;

import com.hospital.common.result.Result;
import com.hospital.dto.auth.LoginDTO;
import com.hospital.dto.auth.RegisterDTO;
import com.hospital.security.LoginUser;
import com.hospital.service.AuthService;
import com.hospital.vo.auth.LoginVO;
import com.hospital.vo.auth.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册、登录、当前用户。
 * 前两个接口在 SecurityConfig 中放行，其余需要携带 JWT。
 */
@Tag(name = "认证", description = "注册 / 登录 / 当前用户")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "患者注册", description = "手机号即登录名，密码使用 BCrypt 加密入库")
    @PostMapping("/register")
    public Result<UserInfoVO> register(@Valid @RequestBody RegisterDTO request) {
        return Result.success("注册成功", authService.register(request));
    }

    @Operation(summary = "登录", description = "成功后返回 JWT，后续请求头携带 Authorization: Bearer {token}")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO request) {
        return Result.success("登录成功", authService.login(request));
    }

    @Operation(summary = "当前登录用户", description = "用于前端校验登录态、获取角色")
    @GetMapping("/me")
    public Result<UserInfoVO> me(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(authService.currentUser(loginUser.getUserId()));
    }
}
