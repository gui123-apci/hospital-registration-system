package com.hospital.client;

import com.hospital.vo.triage.TriageScheduleVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 导诊 Agent 工具执行结果的请求级暂存。
 *
 * 为什么需要它：模型调 listSchedules 是"内部"行为，ChatClient 只把最终的自然语言
 * 返回给上层，工具查到的号源（scheduleId 等）对上层是透明的。但前端要"一键挂号"，
 * 必须有结构化的 scheduleId / doctorId / departmentId，不能靠从模型文本里抠。
 *
 * 所以工具执行时把号源写进这里，同一请求线程内 Service 读出来随 answer 一起返回。
 * ThreadLocal 保证并发隔离；务必在 finally 里 clear，否则线程池复用会读到上次的脏数据。
 */
@Component
public class TriageScheduleHolder {

    private final ThreadLocal<List<TriageScheduleVO>> schedules = new ThreadLocal<>();

    public void put(List<TriageScheduleVO> list) {
        schedules.set(list);
    }

    /** 读取并清理，一次性消费，避免残留 */
    public List<TriageScheduleVO> take() {
        List<TriageScheduleVO> list = schedules.get();
        schedules.remove();
        return list == null ? new ArrayList<>() : list;
    }

    public void clear() {
        schedules.remove();
    }
}
