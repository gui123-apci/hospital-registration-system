package com.hospital.service;

import com.hospital.dto.payment.PayDTO;
import com.hospital.dto.payment.RefundDTO;

/**
 * 支付业务接口：支付 + 退号退款。
 */
public interface PaymentService {

    /**
     * 支付：待支付订单 → 已支付，号源锁定 → 已售出，落一条成功支付单。
     *
     * @param dto    orderId + 支付渠道
     * @param userId 从 token 解出的账号，校验订单归属
     */
    void pay(PayDTO dto, Long userId);

    /**
     * 退号：已支付订单 → 已退号，号源回可用，库存回补，支付单退款，唯一键让位。
     */
    void refund(RefundDTO dto, Long userId);
}
