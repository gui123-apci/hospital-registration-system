package com.hospital.service;

import com.hospital.common.enums.NotificationType;
import com.hospital.entity.Notification;

import java.util.List;

/**
 * 站内通知服务。
 */
public interface NotificationService {

    /**
     * 发一条站内通知。
     * 通知失败不能影响主业务——支付成功/叫号已经写库了，
     * 这里内部吞异常只记日志，不做事务回滚。
     */
    void send(Long userId, NotificationType type, String title, String content);

    /** 我的通知列表，按时间倒序 */
    List<Notification> list(Long userId, int limit);

    /** 未读数，前端铃铛角标 */
    long unreadCount(Long userId);

    /** 单条标记已读 */
    void markRead(Long id, Long userId);

    /** 全部标记已读 */
    void markAllRead(Long userId);
}
