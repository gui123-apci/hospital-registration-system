package com.hospital.service;

import com.hospital.vo.dashboard.DashboardVO;

/**
 * 数据看板统计接口。
 */
public interface DashboardService {

    /**
     * 看板汇总数据：今日挂号量、收入、订单状态分布、科室占比、7天趋势。
     */
    DashboardVO overview();
}
