package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内通知，对应 tb_notification。
 * type：1支付成功 2医生叫号 3报告出具；readFlag：0未读 1已读。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_notification")
public class Notification extends BaseEntity {

    /** 接收用户（患者端按登录用户查，医生端暂不推送） */
    private Long userId;

    private Integer type;

    private String title;

    private String content;

    private Integer readFlag;
}
