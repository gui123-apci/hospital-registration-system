package com.hospital.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.common.enums.UserRole;
import com.hospital.entity.User;
import com.hospital.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动时确保存在一个管理员账号，否则管理端接口没人能调用。
 * 账号密码来自配置 hospital.admin.*，可用环境变量 ADMIN_USERNAME / ADMIN_PASSWORD 覆盖。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    @Value("${hospital.admin.username:admin}")
    private String adminUsername;

    @Value("${hospital.admin.password:admin123}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, adminUsername));
        if (exists != null && exists > 0) {
            log.info("管理员账号已存在，跳过初始化: {}", adminUsername);
            return;
        }

        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole(UserRole.ADMIN.getCode());
        userMapper.insert(admin);
        log.info("已初始化管理员账号: {}（请尽快修改默认密码）", adminUsername);
    }
}
