package com.hospital.vo.dashboard;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 数据看板出参。
 */
@Data
public class DashboardVO {

    /** 今日挂号量 */
    private Long todayOrderCount;

    /** 今日收入（已支付订单金额合计） */
    private BigDecimal todayRevenue;

    /** 订单状态分布：状态码 → 数量 */
    private Map<Integer, Long> statusDistribution;

    /** 科室挂号量占比：科室名 → 数量 */
    private Map<String, Long> departmentDistribution;

    /** 最近 7 天挂号趋势：日期 → 数量 */
    private Map<String, Long> weeklyTrend;

    /** 最近 7 天收入趋势：日期 → 金额 */
    private Map<String, BigDecimal> weeklyRevenue;

    /** 总就诊人数 */
    private Long totalPatients;

    /** 总医生数 */
    private Long totalDoctors;

    /** 总科室数 */
    private Long totalDepartments;
}
