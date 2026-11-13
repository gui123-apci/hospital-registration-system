package com.hospital.service;

import com.hospital.dto.patient.PatientCreateDTO;
import com.hospital.dto.patient.PatientUpdateDTO;
import com.hospital.vo.patient.PatientVO;

import java.util.List;

/**
 * 就诊人业务接口。
 */
public interface PatientService {

    /** 我的就诊人列表 */
    List<PatientVO> listMine(Long userId);

    /** 新增就诊人（家人） */
    PatientVO create(PatientCreateDTO dto, Long userId);

    /** 更新就诊人（补全身份证、改姓名等） */
    PatientVO update(PatientUpdateDTO dto, Long userId);
}
