package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户账号，对应 tb_user。
 * role：0 患者、1 管理员、2 医生。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_user")
public class User extends BaseEntity {

    /** 登录名，当前设计使用手机号 */
    private String username;

    /** BCrypt 加密后的密码，禁止序列化给前端 */
    @JsonIgnore
    private String password;

    private String phone;

    /** 0患者 1管理员 2医生 */
    private Integer role;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
