package com.hospital.dto.patient;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新就诊人入参。字段都可选，传哪个更新哪个（null 跳过），id 必传。
 * 和新增拆开：新增 name/relation 必填，更新允许只补一个身份证、只改一个电话。
 */
@Data
public class PatientUpdateDTO {

    @NotNull(message = "就诊人 ID 不能为空")
    @Positive(message = "就诊人参数不合法")
    private Long id;

    @Size(max = 32, message = "姓名不能超过 32 字")
    private String name;

    @Pattern(regexp = "^(\\d{17}[\\dXx])?$", message = "身份证号格式不正确")
    private String idCard;

    @Min(value = 0, message = "关系不合法")
    @Max(value = 3, message = "关系不合法")
    private Integer relation;

    @Pattern(regexp = "^(1[3-9]\\d{9})?$", message = "手机号格式不正确")
    private String phone;
}
