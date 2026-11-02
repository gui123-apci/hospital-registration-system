package com.hospital.common.result;

import lombok.Getter;

/**
 * 业务返回码。
 * 分段约定：1xxx 参数、2xxx 业务、3xxx 权限、5xxx 系统。
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),

    PARAM_ERROR(1001, "参数校验失败"),

    USERNAME_EXISTS(2001, "该手机号已注册"),
    LOGIN_FAILED(2002, "用户名或密码错误"),
    USER_NOT_FOUND(2003, "用户不存在"),

    PATIENT_NOT_FOUND(2011, "就诊人不存在"),
    PATIENT_NOT_OWNED(2012, "无权操作该就诊人"),
    ID_CARD_EXISTS(2013, "该身份证已添加为就诊人"),

    DEPARTMENT_NOT_FOUND(2021, "科室不存在"),
    DEPARTMENT_NAME_EXISTS(2022, "科室名称已存在"),
    DEPARTMENT_IN_USE(2023, "科室下存在医生或排班，无法删除"),

    DOCTOR_NOT_FOUND(2031, "医生不存在"),
    DOCTOR_IN_USE(2032, "医生存在排班，无法删除"),

    SCHEDULE_NOT_FOUND(2041, "排班不存在"),
    SCHEDULE_IN_USE(2042, "排班已产生挂号单，无法删除"),

    UNAUTHORIZED(3001, "未登录或登录已失效"),
    FORBIDDEN(3002, "没有权限访问该资源"),

    SYSTEM_ERROR(5000, "系统繁忙，请稍后重试");

    private final Integer code;
    private final String msg;

    ResultCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
