package com.hospital.service;

import com.hospital.common.result.PageResult;
import com.hospital.dto.department.DepartmentCreateDTO;
import com.hospital.dto.department.DepartmentUpdateDTO;
import com.hospital.vo.department.DepartmentVO;

import java.util.List;

/**
 * 科室业务接口。
 */
public interface DepartmentService {

    /** 新增科室 */
    DepartmentVO create(DepartmentCreateDTO dto);

    /** 更新科室 */
    DepartmentVO update(DepartmentUpdateDTO dto);

    /** 删除科室（有医生/排班引用时不允许删除） */
    void delete(Long id);

    /** 根据 ID 查询 */
    DepartmentVO getById(Long id);

    /** 查询全部科室（患者端首页用，不需要分页） */
    List<DepartmentVO> list();

    /** 管理端分页查询 */
    PageResult<DepartmentVO> page(long pageNum, long pageSize);
}