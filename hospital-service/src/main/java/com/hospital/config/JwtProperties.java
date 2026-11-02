package com.hospital.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定 application.yml 中的 hospital.jwt 配置。
 * 密钥不写死在代码里，通过环境变量 JWT_SECRET 覆盖。
 */
@Data
@ConfigurationProperties(prefix = "hospital.jwt")
public class JwtProperties {

    /** 签名密钥，HS256 要求至少 32 字节 */
    private String secret;

    /** token 有效期（小时） */
    private Long expireHours = 2L;

    /** 签发者，解析时会校验，防止别的系统的 token 混进来 */
    private String issuer = "hospital-service";
}
