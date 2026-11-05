package com.hospital.dto.doctor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员新增医生的入参 DTO。
 */
@Data
public class DoctorCreateDTO {

    /** 医生必须属于某个科室，不可空 */
    @NotNull(message = "科室不能为空")
    private Long departmentId;

    @NotBlank(message = "医生姓名不能为空")
    @Size(max = 32, message = "姓名最多 32 字")
    private String name;

    @Size(max = 32, message = "职称最多 32 字")
    private String title;

    @Size(max = 255, message = "简介最多 255 字")
    private String intro;

    /** 可空：新增医生时不一定同时建账号 */
    private Long userId;
}
