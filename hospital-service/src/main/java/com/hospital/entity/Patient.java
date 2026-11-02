package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 就诊人，对应 tb_patient。
 * 一个账号可以添加多个家人，身份证号全局唯一。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_patient")
public class Patient extends BaseEntity {

    /** 所属账号 */
    private Long userId;

    private String name;

    private String idCard;

    /** 0本人 1父母 2子女 3其他 */
    private Integer relation;

    private String phone;
}
