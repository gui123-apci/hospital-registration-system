package com.hospital.vo.department;

import com.hospital.entity.Department;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 返回给前端的科室信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentVO {

    private Long id;
    private String name;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 实体 → VO 的快捷转换 */
    public static DepartmentVO from(Department department) {
        return new DepartmentVO(
                department.getId(),
                department.getName(),
                department.getDescription(),
                department.getCreateTime(),
                department.getUpdateTime());
    }
}
