package com.hospital.service;

import com.hospital.dto.report.ReportCreateDTO;
import com.hospital.vo.doctor.WaitingVO;
import com.hospital.vo.order.OrderVO;
import com.hospital.vo.report.ReportVO;
import com.hospital.vo.schedule.ScheduleVO;

import java.util.List;

/**
 * 医生工作台业务接口：叫号 + 开具检验报告。
 * doctorUserId 从 token 解出，服务端据此反查绑定的医生，校验排班归属。
 */
public interface DoctorWorkService {

    /**
     * 当前医生的出诊排班：今天尚未结束的时段 + 未来排班，按日期、时段升序。
     * 工作台据此渲染排班下拉框，id 直接用于候诊队列与叫号。
     */
    List<ScheduleVO> listMySchedules(Long doctorUserId);

    /**
     * 候诊队列：该排班下已签到未就诊的全部患者，按签到时间升序。
     * 供医生端/候诊大屏展示"还有几个人在等"，与叫号取一条是两回事。
     */
    List<WaitingVO> listWaiting(Long scheduleId, Long doctorUserId);

    /**
     * 叫号：取某排班下已签到且未就诊的最早一位，订单置已就诊、号源置已就诊。
     */
    OrderVO callNext(Long scheduleId, Long doctorUserId);

    /**
     * 开具检验报告：仅限"已就诊"订单，且订单所属排班归属当前医生。
     */
    ReportVO createReport(ReportCreateDTO dto, Long doctorUserId);
}
