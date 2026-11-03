package com.hospital.dto.department;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员新增科室的入参。
 */
@Data
public class DepartmentCreateDTO {

    @NotBlank(message = "科室名称不能为空")
    @Size(max = 32, message = "科室名称最多 32 字")
    private String name;

    @Size(max = 255, message = "简介最多 255 字")
    private String description;
}