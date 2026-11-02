package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 科室，对应 tb_department。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_department")
public class Department extends BaseEntity {

    private String name;

    private String description;
}
