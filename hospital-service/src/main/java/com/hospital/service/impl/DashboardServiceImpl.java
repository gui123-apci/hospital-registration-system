package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.common.enums.OrderStatus;
import com.hospital.common.enums.PaymentStatus;
import com.hospital.entity.Department;
import com.hospital.entity.Doctor;
import com.hospital.entity.Order;
import com.hospital.entity.Patient;
import com.hospital.entity.Payment;
import com.hospital.entity.Schedule;
import com.hospital.mapper.DepartmentMapper;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.OrderMapper;
import com.hospital.mapper.PatientMapper;
import com.hospital.mapper.PaymentMapper;
import com.hospital.mapper.ScheduleMapper;
import com.hospital.service.DashboardService;
import com.hospital.vo.dashboard.DashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据看板统计实现：纯 SQL 聚合，不加缓存——管理端 QPS 低，实时性更重要。
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final PatientMapper patientMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final ScheduleMapper scheduleMapper;

    @Override
    public DashboardVO overview() {
        DashboardVO vo = new DashboardVO();
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();

        // 今日挂号量
        vo.setTodayOrderCount(orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .ge(Order::getCreateTime, todayStart)
                .lt(Order::getCreateTime, todayEnd)));

        // 今日收入（支付成功的支付单金额合计）
        List<Payment> todayPayments = paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getStatus, PaymentStatus.SUCCESS.getCode())
                .ge(Payment::getCreateTime, todayStart)
                .lt(Payment::getCreateTime, todayEnd));
        vo.setTodayRevenue(todayPayments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // 订单状态分布
        List<Order> allOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .select(Order::getStatus));
        vo.setStatusDistribution(allOrders.stream()
                .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting())));

        // 科室挂号量占比（通过排班反查科室）
        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .select(Schedule::getId, Schedule::getDepartmentId));
        Map<Long, Long> scheduleToDept = schedules.stream()
                .collect(Collectors.toMap(Schedule::getId, Schedule::getDepartmentId));
        List<Department> departments = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                .select(Department::getId, Department::getName));
        Map<Long, String> deptNames = departments.stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));

        List<Order> ordersWithSchedule = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .select(Order::getScheduleId));
        Map<String, Long> deptDist = new LinkedHashMap<>();
        for (Order order : ordersWithSchedule) {
            Long deptId = scheduleToDept.get(order.getScheduleId());
            String deptName = deptId != null ? deptNames.getOrDefault(deptId, "未知科室") : "未知科室";
            deptDist.merge(deptName, 1L, Long::sum);
        }
        vo.setDepartmentDistribution(deptDist);

        // 最近 7 天挂号趋势
        Map<String, Long> weeklyTrend = new LinkedHashMap<>();
        Map<String, BigDecimal> weeklyRevenue = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String label = date.format(fmt);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();

            Long count = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                    .ge(Order::getCreateTime, start)
                    .lt(Order::getCreateTime, end));
            weeklyTrend.put(label, count);

            List<Payment> dayPayments = paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                    .eq(Payment::getStatus, PaymentStatus.SUCCESS.getCode())
                    .ge(Payment::getCreateTime, start)
                    .lt(Payment::getCreateTime, end));
            BigDecimal revenue = dayPayments.stream()
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            weeklyRevenue.put(label, revenue);
        }
        vo.setWeeklyTrend(weeklyTrend);
        vo.setWeeklyRevenue(weeklyRevenue);

        // 基础数据总量
        vo.setTotalPatients(patientMapper.selectCount(null));
        vo.setTotalDoctors(doctorMapper.selectCount(null));
        vo.setTotalDepartments(departmentMapper.selectCount(null));

        return vo;
    }
}
