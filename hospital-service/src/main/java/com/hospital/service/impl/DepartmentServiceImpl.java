package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.result.PageResult;
import com.hospital.common.result.ResultCode;
import com.hospital.dto.department.DepartmentCreateDTO;
import com.hospital.dto.department.DepartmentUpdateDTO;
import com.hospital.entity.Department;
import com.hospital.entity.Doctor;
import com.hospital.mapper.DepartmentMapper;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.ScheduleMapper;
import com.hospital.service.DepartmentService;
import com.hospital.vo.department.DepartmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 科室业务实现。
 */
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final DoctorMapper doctorMapper;
    private final ScheduleMapper scheduleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepartmentVO create(DepartmentCreateDTO dto) {
        // 同名校验（业务约束，不是数据库唯一索引——实际可加，但这里先在 Service 层拦）
        checkNameUnique(dto.getName(), null);

        Department department = new Department();
        department.setName(dto.getName());
        department.setDescription(dto.getDescription());
        departmentMapper.insert(department);

        return DepartmentVO.from(department);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepartmentVO update(DepartmentUpdateDTO dto) {
        // 必须存在
        Department exist = departmentMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BusinessException(ResultCode.DEPARTMENT_NOT_FOUND);
        }

        // 改名字的话要查重（但允许和自己同名）
        if (dto.getName() != null && !dto.getName().equals(exist.getName())) {
            checkNameUnique(dto.getName(), dto.getId());
        }

        // 只更新非 null 字段
        if (dto.getName() != null) {
            exist.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            exist.setDescription(dto.getDescription());
        }
        departmentMapper.updateById(exist);

        return DepartmentVO.from(exist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Department exist = departmentMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ResultCode.DEPARTMENT_NOT_FOUND);
        }

        // 被医生引用了？
        Long doctorCount = doctorMapper.selectCount(new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getDepartmentId, id));
        // 被排班引用了？
        Long scheduleCount = scheduleMapper.selectCount(new LambdaQueryWrapper<com.hospital.entity.Schedule>()
                .eq(com.hospital.entity.Schedule::getDepartmentId, id));
        if ((doctorCount != null && doctorCount > 0) || (scheduleCount != null && scheduleCount > 0)) {
            throw new BusinessException(ResultCode.DEPARTMENT_IN_USE);
        }

        departmentMapper.deleteById(id);
    }

    @Override
    public DepartmentVO getById(Long id) {
        Department department = departmentMapper.selectById(id);
        if (department == null) {
            throw new BusinessException(ResultCode.DEPARTMENT_NOT_FOUND);
        }
        return DepartmentVO.from(department);
    }

    @Override
    public List<DepartmentVO> list() {
        List<Department> list = departmentMapper.selectList(null);
        return list.stream().map(DepartmentVO::from).toList();
    }

    @Override
    public PageResult<DepartmentVO> page(long pageNum, long pageSize) {
        Page<Department> page = departmentMapper.selectPage(new Page<>(pageNum, pageSize), null);
        return PageResult.of(page, DepartmentVO::from);
    }

    /**
     * 工具方法：检查名称是否已被占用（排除 excludeId 自己）。
     */
    private void checkNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<Department>()
                .eq(Department::getName, name);
        if (excludeId != null) {
            wrapper.ne(Department::getId, excludeId);
        }
        Long count = departmentMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.DEPARTMENT_NAME_EXISTS);
        }
    }
}