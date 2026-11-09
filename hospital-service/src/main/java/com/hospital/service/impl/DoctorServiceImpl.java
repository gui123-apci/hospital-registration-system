package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.result.PageResult;
import com.hospital.common.result.ResultCode;
import com.hospital.dto.doctor.DoctorCreateDTO;
import com.hospital.dto.doctor.DoctorUpdateDTO;
import com.hospital.entity.Doctor;
import com.hospital.entity.Report;
import com.hospital.mapper.DepartmentMapper;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.ReportMapper;
import com.hospital.mapper.ScheduleMapper;
import com.hospital.service.DoctorService;
import com.hospital.vo.doctor.DoctorVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 医生业务实现。
 */
@Service
@RequiredArgsConstructor
public class DoctorServiceImpl implements DoctorService {

    private final DoctorMapper doctorMapper;
    private final ScheduleMapper scheduleMapper;
    private final DepartmentMapper departmentMapper;
    private final ReportMapper reportMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DoctorVO create(DoctorCreateDTO dto) {
        // 差异点：新增医生必须先校验科室存在（医生实体上 departmentId 是 NOT NULL）
        Long deptCount = departmentMapper.selectById(dto.getDepartmentId()) == null ? 0L : 1L;
        if (deptCount == 0L) {
            throw new BusinessException(ResultCode.DEPARTMENT_NOT_FOUND);
        }

        Doctor doctor = new Doctor();
        doctor.setDepartmentId(dto.getDepartmentId());
        doctor.setName(dto.getName());
        doctor.setTitle(dto.getTitle());
        doctor.setIntro(dto.getIntro());
        doctor.setUserId(dto.getUserId());
        doctorMapper.insert(doctor);

        return DoctorVO.from(doctor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DoctorVO update(DoctorUpdateDTO dto) {
        Doctor exist = doctorMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BusinessException(ResultCode.DOCTOR_NOT_FOUND);
        }

        // 换科室的话，目标科室必须存在
        if (dto.getDepartmentId() != null
                && !dto.getDepartmentId().equals(exist.getDepartmentId())) {
            if (departmentMapper.selectById(dto.getDepartmentId()) == null) {
                throw new BusinessException(ResultCode.DEPARTMENT_NOT_FOUND);
            }
            exist.setDepartmentId(dto.getDepartmentId());
        }

        if (dto.getName() != null) {
            exist.setName(dto.getName());
        }
        if (dto.getTitle() != null) {
            exist.setTitle(dto.getTitle());
        }
        if (dto.getIntro() != null) {
            exist.setIntro(dto.getIntro());
        }
        if (dto.getUserId() != null) {
            exist.setUserId(dto.getUserId());
        }
        doctorMapper.updateById(exist);

        return DoctorVO.from(exist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (doctorMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.DOCTOR_NOT_FOUND);
        }

        // 差异点：医生被 tb_schedule 和 tb_report 两张表引用（tb_doctor.user_id 是可空的一对一，不是外键）
        Long scheduleCount = scheduleMapper.selectCount(
                new LambdaQueryWrapper<com.hospital.entity.Schedule>()
                        .eq(com.hospital.entity.Schedule::getDoctorId, id));
        if (scheduleCount != null && scheduleCount > 0) {
            throw new BusinessException(ResultCode.DOCTOR_IN_USE);
        }

        // 出过报告的医生不能删（报告是就诊历史凭证）
        Long reportCount = reportMapper.selectCount(
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getDoctorId, id));
        if (reportCount != null && reportCount > 0) {
            throw new BusinessException(ResultCode.DOCTOR_IN_USE);
        }

        doctorMapper.deleteById(id);
    }

    @Override
    public DoctorVO getById(Long id) {
        Doctor doctor = doctorMapper.selectById(id);
        if (doctor == null) {
            throw new BusinessException(ResultCode.DOCTOR_NOT_FOUND);
        }
        return DoctorVO.from(doctor);
    }

    @Override
    public List<DoctorVO> list() {
        return doctorMapper.selectList(null).stream().map(DoctorVO::from).toList();
    }

    @Override
    public List<DoctorVO> list(Long departmentId) {
        LambdaQueryWrapper<Doctor> wrapper = new LambdaQueryWrapper<>();
        if (departmentId != null) {
            wrapper.eq(Doctor::getDepartmentId, departmentId);
        }
        return doctorMapper.selectList(wrapper).stream().map(DoctorVO::from).toList();
    }

    @Override
    public PageResult<DoctorVO> page(long pageNum, long pageSize) {
        Page<Doctor> page = doctorMapper.selectPage(
                new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100)), null);
        return PageResult.of(page, DoctorVO::from);
    }
}
