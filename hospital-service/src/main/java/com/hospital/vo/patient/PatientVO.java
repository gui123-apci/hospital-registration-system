package com.hospital.vo.patient;

import com.hospital.entity.Patient;
import lombok.Data;

/**
 * 就诊人出参。
 *
 * 身份证脱敏返回：保留前 6 位（地区码）和后 4 位，中间掩码。
 * 前端编辑时若把含 * 的脱敏值原样提交，Service 会识别为"未修改"并忽略，不会写坏真实数据。
 */
@Data
public class PatientVO {

    private Long id;

    private String name;

    /** 脱敏身份证，如 110101********1234 */
    private String idCard;

    /** 0本人 1父母 2子女 3其他 */
    private Integer relation;

    private String phone;

    public static PatientVO from(Patient patient) {
        PatientVO vo = new PatientVO();
        vo.setId(patient.getId());
        vo.setName(patient.getName());
        vo.setIdCard(maskIdCard(patient.getIdCard()));
        vo.setRelation(patient.getRelation());
        vo.setPhone(patient.getPhone());
        return vo;
    }

    /** 保留前 6 后 4；长度不足 11 位时全部掩码，避免短号码被反推 */
    private static String maskIdCard(String idCard) {
        if (idCard == null || idCard.isBlank()) {
            return idCard;
        }
        if (idCard.length() <= 10) {
            return "*".repeat(idCard.length());
        }
        return idCard.substring(0, 6)
                + "*".repeat(idCard.length() - 10)
                + idCard.substring(idCard.length() - 4);
    }
}
