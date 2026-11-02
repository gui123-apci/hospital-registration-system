package com.hospital;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 智慧医院就医服务系统启动类。
 * @ConfigurationPropertiesScan 用于扫描 JwtProperties 这类配置绑定类。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.hospital.mapper")
public class HospitalServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalServiceApplication.class, args);
    }
}
