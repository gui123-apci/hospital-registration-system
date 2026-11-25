package com.hospital.client;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.entity.Department;
import com.hospital.entity.Doctor;
import com.hospital.entity.Schedule;
import com.hospital.mapper.DepartmentMapper;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.ScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 导诊 Agent 的工具集：把「查科室 / 查医生 / 查号源」封装成模型可调用的工具。
 *
 * 核心认知：模型只负责「决定调哪个工具、怎么组合」，真正查库的是这些方法。
 * 工具入参用「科室名」而不是「科室 id」——模型只会填人类语言（心内科），
 * 让它填数字 id 会乱填；内部拿名字先查科室，再查医生/排班。
 *
 * 返回给模型的是 JSON 字符串，模型读完后自己组织成自然语言回答患者。
 */
@Component
@RequiredArgsConstructor
public class TriageTools {

    private final DepartmentMapper departmentMapper;
    private final DoctorMapper doctorMapper;
    private final ScheduleMapper scheduleMapper;
    private final ObjectMapper objectMapper;

    @Tool(description = "查询医院全部科室列表，返回科室名称和简介")
    public String listDepartments() {
        List<Department> depts = departmentMapper.selectList(null);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Department d : depts) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", d.getName());
            m.put("description", d.getDescription());
            list.add(m);
        }
        return toJson(list);
    }

    @Tool(description = "查询指定科室的医生列表，返回医生姓名、职称和简介")
    public String listDoctors(@ToolParam(description = "科室名称，如：心内科") String departmentName) {
        Department dept = findByDeptName(departmentName);
        if (dept == null) {
            return "未找到科室：" + departmentName;
        }
        List<Doctor> doctors = doctorMapper.selectList(new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getDepartmentId, dept.getId()));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Doctor doc : doctors) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", doc.getName());
            m.put("title", doc.getTitle());
            m.put("intro", doc.getIntro());
            list.add(m);
        }
        return toJson(list);
    }

    @Tool(description = "查询指定科室最近 7 天内可预约的号源，返回医生、日期、时段、剩余号数和价格")
    public String listSchedules(@ToolParam(description = "科室名称，如：心内科") String departmentName) {
        Department dept = findByDeptName(departmentName);
        if (dept == null) {
            return "未找到科室：" + departmentName;
        }
        LocalDate today = LocalDate.now();
        // 可预约 = 未停诊(status=1) + 还有号(leftNum>0) + 日期在今天起 7 天内
        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getDepartmentId, dept.getId())
                .eq(Schedule::getStatus, 1)
                .gt(Schedule::getLeftNum, 0)
                .ge(Schedule::getWorkDate, today)
                .lt(Schedule::getWorkDate, today.plusDays(7))
                .orderByAsc(Schedule::getWorkDate)
                .orderByAsc(Schedule::getTimeSlot));

        // 一次查全科室医生，拼成 id→姓名，避免每个排班都回查医生表
        Map<Long, String> doctorNames = doctorMapper.selectList(new LambdaQueryWrapper<Doctor>()
                        .eq(Doctor::getDepartmentId, dept.getId()))
                .stream()
                .collect(Collectors.toMap(Doctor::getId, Doctor::getName));

        List<Map<String, Object>> list = new ArrayList<>();
        for (Schedule s : schedules) {
            Map<String, Object> m = new HashMap<>();
            m.put("doctor", doctorNames.getOrDefault(s.getDoctorId(), "未知医生"));
            m.put("date", s.getWorkDate().toString());
            m.put("timeSlot", slotName(s.getTimeSlot()));
            m.put("leftNum", s.getLeftNum());
            m.put("price", s.getPrice());
            list.add(m);
        }
        return toJson(list);
    }

    private Department findByDeptName(String name) {
        return departmentMapper.selectOne(new LambdaQueryWrapper<Department>()
                .eq(Department::getName, name)
                .last("LIMIT 1"));
    }

    /** 时段数字转中文，方便模型读给患者听 */
    private String slotName(Integer timeSlot) {
        if (timeSlot == null) {
            return "未知";
        }
        return switch (timeSlot) {
            case 1 -> "上午";
            case 2 -> "下午";
            case 3 -> "晚间";
            default -> "未知";
        };
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
