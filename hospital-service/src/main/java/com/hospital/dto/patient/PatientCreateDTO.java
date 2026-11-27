package com.hospital.dto.patient;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增就诊人入参。
 * 差异点：不传 userId——就诊人挂在当前登录账号下，从 token 解出。
 * 身份证/手机号可先不填，之后 update 补全（注册时"本人"就诊人就是空着身份证进来的）。
 */
@Data
public class PatientCreateDTO {

    @NotBlank(message = "就诊人姓名不能为空")
    @Size(max = 32, message = "姓名不能超过 32 字")
    private String name;

    /** 18 位身份证，必填 */
    @NotBlank(message = "身份证号不能为空")
    @Pattern(regexp = "^\\d{17}[\\dXx]$", message = "身份证号格式不正确")
    private String idCard;

    /** 0本人 1父母 2子女 3其他 */
    @NotNull(message = "关系不能为空")
    @Min(value = 0, message = "关系不合法")
    @Max(value = 3, message = "关系不合法")
    private Integer relation;

    @Pattern(regexp = "^(1[3-9]\\d{9})?$", message = "手机号格式不正确")
    private String phone;
}
