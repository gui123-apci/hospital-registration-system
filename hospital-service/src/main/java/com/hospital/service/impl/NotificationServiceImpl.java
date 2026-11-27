package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hospital.common.enums.NotificationType;
import com.hospital.entity.Notification;
import com.hospital.mapper.NotificationMapper;
import com.hospital.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 站内通知实现：写入即送达，前端轮询未读数。
 *
 * 为什么不用 WebSocket / 消息队列：
 * 通知是"可延后"的信息，患者刷新页面看到也不晚；
 * 引入长连接会带来连接管理、心跳、多实例广播一堆问题，
 * 当前规模用"写库 + 前端轮询"性价比最高。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_LIST_SIZE = 50;
    private static final int READ = 1;
    private static final int UNREAD = 0;

    private final NotificationMapper notificationMapper;

    @Override
    public void send(Long userId, NotificationType type, String title, String content) {
        if (userId == null) {
            return;
        }
        try {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(type.getCode());
            notification.setTitle(title);
            notification.setContent(content);
            notification.setReadFlag(UNREAD);
            notificationMapper.insert(notification);
        } catch (Exception e) {
            // 通知是附加价值，不能因为它失败把支付/叫号的主事务带崩
            log.warn("站内通知发送失败, userId={}, type={}", userId, type.getCode(), e);
        }
    }

    @Override
    public List<Notification> list(Long userId, int limit) {
        int size = Math.min(Math.max(limit, 1), MAX_LIST_SIZE);
        return notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreateTime)
                .orderByDesc(Notification::getId)
                .last("LIMIT " + size));
    }

    @Override
    public long unreadCount(Long userId) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadFlag, UNREAD));
        return count == null ? 0L : count;
    }

    @Override
    public void markRead(Long id, Long userId) {
        // 带 user_id 条件：防止改到别人的通知
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .set(Notification::getReadFlag, READ));
    }

    @Override
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadFlag, UNREAD)
                .set(Notification::getReadFlag, READ));
    }
}
