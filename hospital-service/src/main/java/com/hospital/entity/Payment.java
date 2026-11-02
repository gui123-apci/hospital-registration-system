package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单，对应 tb_payment。
 * channel：0模拟 1微信 2支付宝；status：0待支付 1成功 2失败 3退款。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_payment")
public class Payment extends BaseEntity {

    private Long orderId;

    private Long userId;

    private Long patientId;

    private BigDecimal amount;

    private Integer channel;

    private Integer status;

    private LocalDateTime payTime;
}
