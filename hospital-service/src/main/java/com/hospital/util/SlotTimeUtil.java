package com.hospital.util;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 时段判断工具：排班模块和挂号模块共用同一套"是否已过期"的判断。
 *
 * 单一事实来源：8:00 / 13:00 / 18:00 只在这里定义一份，
 * 谁要判断"今天这个时段开没开始"，都调 isExpired，不允许各自抄一份常量。
 */
public final class SlotTimeUtil {

    /** 各时段开始时刻，下标即 timeSlot：1上午 8:00、2下午 13:00、3晚间 18:00（下标 0 占位） */
    private static final LocalTime[] SLOT_START = {
            LocalTime.MIDNIGHT,
            LocalTime.of(8, 0),
            LocalTime.of(13, 0),
            LocalTime.of(18, 0)
    };

    private SlotTimeUtil() {
    }

    /**
     * 该排班时段是否已经过了：日期已过，或者就是今天但时段开始时刻已到。
     * workDate 精确到天，所以"今天上午"只能在运行时用时刻判断。
     *
     * 脏数据防御：timeSlot 越界（无 CHECK 约束）按"已过期"处理，避免数组越界。
     */
    public static boolean isExpired(LocalDate workDate, Integer timeSlot) {
        LocalDate today = LocalDate.now();
        if (workDate.isBefore(today)) {
            return true;
        }
        if (timeSlot == null || timeSlot < 1 || timeSlot > 3) {
            return true;
        }
        return workDate.isEqual(today) && !LocalTime.now().isBefore(SLOT_START[timeSlot]);
    }
}
