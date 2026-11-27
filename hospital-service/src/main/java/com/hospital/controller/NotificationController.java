package com.hospital.controller;

import com.hospital.common.result.Result;
import com.hospital.entity.Notification;
import com.hospital.security.LoginUser;
import com.hospital.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 站内通知接口：患者端铃铛用。
 * 一律按登录用户查，不接受前端传 userId。
 */
@Tag(name = "站内通知", description = "未读数、通知列表、标记已读")
@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "我的通知列表")
    @GetMapping("/list")
    public Result<List<Notification>> list(@AuthenticationPrincipal LoginUser loginUser,
                                           @RequestParam(defaultValue = "20") int limit) {
        return Result.success(notificationService.list(loginUser.getUserId(), limit));
    }

    @Operation(summary = "未读数量", description = "铃铛角标，前端定时轮询")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(notificationService.unreadCount(loginUser.getUserId()));
    }

    @Operation(summary = "单条标记已读")
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id,
                                 @AuthenticationPrincipal LoginUser loginUser) {
        notificationService.markRead(id, loginUser.getUserId());
        return Result.success();
    }

    @Operation(summary = "全部标记已读")
    @PostMapping("/read-all")
    public Result<Void> markAllRead(@AuthenticationPrincipal LoginUser loginUser) {
        notificationService.markAllRead(loginUser.getUserId());
        return Result.success();
    }
}
