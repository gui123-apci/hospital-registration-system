package com.hospital.service;

import com.hospital.common.result.PageResult;
import com.hospital.dto.schedule.ScheduleCreateDTO;
import com.hospital.dto.schedule.ScheduleUpdateDTO;
import com.hospital.vo.schedule.ScheduleVO;

import java.util.List;

/**
 * 排班业务接口。
 */
public interface ScheduleService {

    ScheduleVO create(ScheduleCreateDTO dto);

    ScheduleVO update(ScheduleUpdateDTO dto);

    void delete(Long id);

    ScheduleVO getById(Long id);

    /**
     * 患者端：查某医生未来若干天可预约的排班。
     *
     * @param doctorId 医生 ID
     * @param days     从今天算起的天数（含今天）
     */
    List<ScheduleVO> listForPatient(Long doctorId, int days);

    /**
     * 管理端分页，doctorId 可空表示不过滤。
     */
    PageResult<ScheduleVO> page(long pageNum, long pageSize, Long doctorId);
}
