package com.hospital.dto.doctor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员更新医生的入参 DTO。
 */
@Data
public class DoctorUpdateDTO {

    @NotNull(message = "医生 ID 不能为空")
    private Long id;

    /** 可换科室，可选 */
    private Long departmentId;

    @Size(max = 32, message = "姓名最多 32 字")
    private String name;

    @Size(max = 32, message = "职称最多 32 字")
    private String title;

    @Size(max = 255, message = "简介最多 255 字")
    private String intro;

    /** 可换绑定账号，可选 */
    private Long userId;
}
