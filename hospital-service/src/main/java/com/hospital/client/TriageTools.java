package com.hospital.client;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.entity.Department;
import com.hospital.entity.Doctor;
import com.hospital.entity.Schedule;
import com.hospital.mapper.DepartmentMapper;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.ScheduleMapper;
import com.hospital.util.SlotTimeUtil;
import com.hospital.vo.triage.TriageScheduleVO;
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
    private final TriageScheduleHolder scheduleHolder;

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

    @Tool(description = "查询指定科室最近 7 天内可预约的号源，返回号源id、医生、日期、时段、剩余号数和价格")
    public String listSchedules(@ToolParam(description = "科室名称，如：心内科") String departmentName) {
        Department dept = findByDeptName(departmentName);
        if (dept == null) {
            return "未找到科室：" + departmentName;
        }
        LocalDate today = LocalDate.now();
        // 可预约 = 未停诊(status=1) + 还有号(leftNum>0) + 日期在今天起 7 天内 + 时段未过期
        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getDepartmentId, dept.getId())
                .eq(Schedule::getStatus, 1)
                .gt(Schedule::getLeftNum, 0)
                .ge(Schedule::getWorkDate, today)
                .lt(Schedule::getWorkDate, today.plusDays(7))
                .orderByAsc(Schedule::getWorkDate)
                .orderByAsc(Schedule::getTimeSlot));
        // 过滤当天已过时段（与 OrderServiceImpl.create 的挂号门槛对齐）
        schedules.removeIf(s -> SlotTimeUtil.isExpired(s.getWorkDate(), s.getTimeSlot()));

        // 一次查全科室医生，拼成 id→姓名，避免每个排班都回查医生表
        Map<Long, String> doctorNames = doctorMapper.selectList(new LambdaQueryWrapper<Doctor>()
                        .eq(Doctor::getDepartmentId, dept.getId()))
                .stream()
                .collect(Collectors.toMap(Doctor::getId, Doctor::getName));

        List<Map<String, Object>> list = new ArrayList<>();
        List<TriageScheduleVO> holderList = new ArrayList<>();
        for (Schedule s : schedules) {
            String doctorName = doctorNames.getOrDefault(s.getDoctorId(), "未知医生");
            // 给模型看的：带上 scheduleId，模型才能明确"推荐哪个号源"
            Map<String, Object> m = new HashMap<>();
            m.put("scheduleId", s.getId());
            m.put("doctor", doctorName);
            m.put("date", s.getWorkDate().toString());
            m.put("timeSlot", slotName(s.getTimeSlot()));
            m.put("leftNum", s.getLeftNum());
            m.put("price", s.getPrice());
            list.add(m);

            // 给前端看的：结构化号源，三个 id 用于"一键挂号"跳转与校验
            TriageScheduleVO vo = new TriageScheduleVO();
            vo.setScheduleId(s.getId());
            vo.setDoctorId(s.getDoctorId());
            vo.setDepartmentId(s.getDepartmentId());
            vo.setDoctorName(doctorName);
            vo.setWorkDate(s.getWorkDate());
            vo.setTimeSlot(s.getTimeSlot());
            vo.setLeftNum(s.getLeftNum());
            vo.setPrice(s.getPrice());
            holderList.add(vo);
        }
        // 工具执行结果对上层透明，这里显式暂存，供 Service 随 answer 一起返回给前端
        scheduleHolder.put(holderList);
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

    // ==================== 简化版 RAG：内置就医知识库 ====================

    /** 知识库：关键词 → 标准答案。模型遇到流程/政策类问题时调用，避免自己编 */
    private static final Map<String, String> KNOWLEDGE_BASE = Map.of(
            "挂号", "挂号流程：1. 选择科室和医生；2. 选择就诊日期和时段；3. 确认就诊人信息；4. 在线支付挂号费；5. 就诊当天到医院签到候诊。",
            "退号", "退号规则：就诊日前一天 23:59 前可在线退号，费用原路退回；就诊当天不可退号；退号后号源自动释放，可重新挂号。",
            "医保", "医保政策：本平台挂号费支持医保个人账户支付；就诊时请携带医保卡和身份证；异地医保需提前备案。",
            "报告", "报告查询：检验报告一般在就诊后 24 小时内出具；可在「我的报告」中查看历史报告；报告仅供参考，最终诊断以医生面诊为准。",
            "签到", "签到规则：就诊当天需在时段开始前 30 分钟内签到；签到后进入候诊队列，医生按签到顺序叫号；未签到视为爽约。",
            "科室", "科室介绍：本平台设有内科、外科、儿科、妇产科、皮肤科等常见科室；如不确定挂哪个科室，可描述症状由 AI 导诊推荐。"
    );

    @Tool(description = "查询就医流程、医保政策、挂号退号规则等常见问题答案")
    public String queryKnowledge(@ToolParam(description = "用户问题，如：怎么挂号、医保怎么报销") String question) {
        for (Map.Entry<String, String> entry : KNOWLEDGE_BASE.entrySet()) {
            if (question.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "未找到相关答案，请描述更具体的问题，或咨询导诊台。";
    }
}
