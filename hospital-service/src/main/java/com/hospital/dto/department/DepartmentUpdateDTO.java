package com.hospital.dto.department;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员更新科室的入参 DTO。
 */
@Data
public class DepartmentUpdateDTO {

    /** 必须指定要更新的科室 */
    @NotNull(message = "科室 ID 不能为空")
    private Long id;

    @Size(max = 32, message = "科室名称最多 32 字")
    private String name;

    @Size(max = 255, message = "简介最多 255 字")
    private String description;
}