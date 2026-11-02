package com.hospital.common.enums;

import lombok.Getter;

/**
 * 用户角色，数值与 tb_user.role 对应。
 * 设计文档约定：患者 PATIENT、管理员 ADMIN、医生 DOCTOR。
 */
@Getter
public enum UserRole {

    PATIENT(0, "患者"),
    ADMIN(1, "管理员"),
    DOCTOR(2, "医生");

    /** 数据库存储值 */
    private final Integer code;

    /** 中文描述，用于返回给前端 */
    private final String desc;

    UserRole(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * Spring Security 要求的权限字符串格式：ROLE_ + 枚举名。
     * 对应 hasRole("ADMIN") / hasAnyRole("ADMIN", "DOCTOR") 的写法。
     */
    public String authority() {
        return "ROLE_" + name();
    }

    /**
     * 按数据库值反查枚举，未知值按患者处理，避免脏数据导致登录直接报错。
     */
    public static UserRole of(Integer code) {
        if (code == null) {
            return PATIENT;
        }
        for (UserRole role : values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        return PATIENT;
    }
}
