package com.hospital.vo.patient;

import com.hospital.entity.Patient;
import lombok.Data;

/**
 * 就诊人出参。
 */
@Data
public class PatientVO {

    private Long id;

    private String name;

    private String idCard;

    /** 0本人 1父母 2子女 3其他 */
    private Integer relation;

    private String phone;

    public static PatientVO from(Patient patient) {
        PatientVO vo = new PatientVO();
        vo.setId(patient.getId());
        vo.setName(patient.getName());
        vo.setIdCard(patient.getIdCard());
        vo.setRelation(patient.getRelation());
        vo.setPhone(patient.getPhone());
        return vo;
    }
}
