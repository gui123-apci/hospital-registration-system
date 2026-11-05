package com.hospital.service;

import com.hospital.common.result.PageResult;
import com.hospital.dto.doctor.DoctorCreateDTO;
import com.hospital.dto.doctor.DoctorUpdateDTO;
import com.hospital.vo.doctor.DoctorVO;

import java.util.List;

/**
 * 医生业务接口。
 */
public interface DoctorService {

    /** 新增医生（需校验科室存在） */
    DoctorVO create(DoctorCreateDTO dto);

    /** 更新医生 */
    DoctorVO update(DoctorUpdateDTO dto);

    /** 删除医生（需校验没有排班引用） */
    void delete(Long id);

    /** 根据 ID 查询 */
    DoctorVO getById(Long id);

    /** 管理端全量 */
    List<DoctorVO> list();

    /** 患者端按科室查询 */
    List<DoctorVO> list(Long departmentId);

    /** 管理端分页 */
    PageResult<DoctorVO> page(long pageNum, long pageSize);
}
