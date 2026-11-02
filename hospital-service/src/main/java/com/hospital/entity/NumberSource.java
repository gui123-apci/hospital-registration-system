package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 具体号源，对应 tb_number_source。
 * 一个排班有 N 个号源，sourceNo 从 1 开始。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_number_source")
public class NumberSource extends BaseEntity {

    private Long scheduleId;

    private Integer sourceNo;

    /** 0可用 1锁定 2已售出 3已就诊 */
    private Integer status;
}
