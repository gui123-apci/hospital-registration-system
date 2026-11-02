package com.hospital.config;

import com.hospital.security.JwtAuthenticationFilter;
import com.hospital.security.RestAccessDeniedHandler;
import com.hospital.security.RestAuthenticationEntryPoint;
import com.hospital.security.UserDetailsServiceImpl;
import com.hospital.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置：无状态 JWT + 角色权限。
 *
 * 规则：
 * 1. 白名单放行登录注册和接口文档；
 * 2. /admin/** 仅管理员，/doctor/** 管理员或医生；
 * 3. 其余接口都需要登录（患者端）；
 * 4. 不使用 Session、不弹登录页，鉴权失败直接返回 JSON。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** 无需登录即可访问的路径 */
    private static final String[] PUBLIC_URLS = {
            "/auth/login",
            "/auth/register",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/knife4j/**",
            "/favicon.ico",
            "/error"
    };

    /**
     * 密码编码器，注册时加密、登录时校验都用它。
     * BCrypt 每次加密结果不同，但 matches 能正确比对，比 MD5 安全。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * JWT 过滤器。手动 new 出来而不是加 @Component，
     * 避免 Boot 把它当成普通 Servlet Filter 再注册一遍。
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtil jwtUtil,
                                                           UserDetailsServiceImpl userDetailsService) {
        return new JwtAuthenticationFilter(jwtUtil, userDetailsService);
    }

    /**
     * 登录时使用的认证管理器：内部用 DaoAuthenticationProvider，
     * 由它调 UserDetailsServiceImpl 查库 + PasswordEncoder 比对密码。
     */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsServiceImpl userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // 前后端分离 + JWT，不需要 CSRF token 和默认登录页
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // 不创建 Session，登录态完全靠 token
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // 浏览器跨域预检请求直接放行
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/doctor/**").hasAnyRole("ADMIN", "DOCTOR")
                        .anyRequest().authenticated())
                // JWT 过滤器要排在用户名密码登录过滤器之前
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
