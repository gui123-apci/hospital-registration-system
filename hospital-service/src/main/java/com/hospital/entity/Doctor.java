package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 医生，对应 tb_doctor。
 * userId 可空：医生不一定绑定登录账号。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_doctor")
public class Doctor extends BaseEntity {

    private Long departmentId;

    private String name;

    /** 职称，如主任医师 */
    private String title;

    private String intro;

    /** 关联 tb_user.id，角色为医生时使用 */
    private Long userId;
}
