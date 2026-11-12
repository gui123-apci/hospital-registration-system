package com.hospital.common.constant;

/**
 * 订单业务常量：数值与提示文案共用同一处，改常量不用满项目找文案。
 */
public final class OrderConstants {

    /** 支付超时时长（分钟），超时未支付的订单由 M2c 定时任务释放 */
    public static final int PAY_TIMEOUT_MINUTES = 15;

    private OrderConstants() {
    }
}
