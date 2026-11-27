package com.hospital;

import com.hospital.client.TriageTools;
import com.hospital.entity.Department;
import com.hospital.entity.Doctor;
import com.hospital.entity.Schedule;
import com.hospital.mapper.DepartmentMapper;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.ScheduleMapper;
import com.hospital.util.SlotTimeUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 导诊工具集成测试：验证号源推荐不会包含当天已过时段。
 * 与 OrderServiceImpl.create 的挂号门槛对齐——推荐和下单用同一套时间判断。
 */
@SpringBootTest
class TriageToolsTest {

    @Autowired private TriageTools triageTools;
    @Autowired private DepartmentMapper departmentMapper;
    @Autowired private DoctorMapper doctorMapper;
    @Autowired private ScheduleMapper scheduleMapper;

    private final List<Long> departmentIds = new ArrayList<>();
    private final List<Long> doctorIds = new ArrayList<>();
    private final List<Long> scheduleIds = new ArrayList<>();

    private long seq = System.nanoTime();
    private String departmentName;

    private synchronized long nextSeq() {
        return ++seq;
    }

    @Test
    void listSchedulesFiltersExpiredSlots() {
        // 建一个科室 + 医生
        long departmentId = createDepartment();
        long doctorId = createDoctor(departmentId);

        // 创建两个排班：今天上午（可能已过期）和明天上午（肯定未过期）
        long todayScheduleId = createSchedule(doctorId, departmentId, LocalDate.now(), 1);
        long tomorrowScheduleId = createSchedule(doctorId, departmentId, LocalDate.now().plusDays(1), 1);

        // 调用导诊工具查询
        String result = triageTools.listSchedules(departmentName);

        // 结果应该是有效 JSON（不是"未找到科室"）
        assertThat(result).doesNotContain("未找到科室");

        // 如果今天上午已过期，结果中不应包含今天的排班
        boolean todayExpired = SlotTimeUtil.isExpired(LocalDate.now(), 1);
        if (todayExpired) {
            assertThat(result).doesNotContain("\"scheduleId\":" + todayScheduleId);
        }
        // 明天的排班必须出现
        assertThat(result).contains("\"scheduleId\":" + tomorrowScheduleId);
    }

    private long createDepartment() {
        Department department = new Department();
        departmentName = "测试科室" + nextSeq();
        department.setName(departmentName);
        department.setDescription("导诊测试用");
        departmentMapper.insert(department);
        departmentIds.add(department.getId());
        return department.getId();
    }

    private long createDoctor(long departmentId) {
        Doctor doctor = new Doctor();
        doctor.setDepartmentId(departmentId);
        doctor.setName("测试医生" + nextSeq());
        doctor.setTitle("主任医师");
        doctorMapper.insert(doctor);
        doctorIds.add(doctor.getId());
        return doctor.getId();
    }

    private long createSchedule(long doctorId, long departmentId, LocalDate workDate, int timeSlot) {
        Schedule schedule = new Schedule();
        schedule.setDoctorId(doctorId);
        schedule.setDepartmentId(departmentId);
        schedule.setWorkDate(workDate);
        schedule.setTimeSlot(timeSlot);
        schedule.setTotalNum(10);
        schedule.setLeftNum(10);
        schedule.setPrice(new BigDecimal("10.00"));
        schedule.setStatus(1); // AVAILABLE
        scheduleMapper.insert(schedule);
        scheduleIds.add(schedule.getId());
        return schedule.getId();
    }

    @AfterEach
    void cleanup() {
        if (!scheduleIds.isEmpty()) scheduleMapper.deleteBatchIds(scheduleIds);
        if (!doctorIds.isEmpty()) doctorMapper.deleteBatchIds(doctorIds);
        if (!departmentIds.isEmpty()) departmentMapper.deleteBatchIds(departmentIds);
    }
}
