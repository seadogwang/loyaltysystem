package com.loyalty.platform.common.util;

import java.time.LocalDateTime;

/**
 * 过期时间计算器 — 统一的积分/等级过期逻辑。
 *
 * <p>支持三种过期模式:
 * <ul>
 *   <li>{@code FIXED_DAYS} — 从基准时间起 + N 天</li>
 *   <li>{@code CALENDAR_MONTHS} — N 个完整自然月后的月末最后一天<br>
 *       例如: 5月 + 12个月 = 次年6月30日</li>
 *   <li>{@code CALENDAR_YEARS} — N 个完整自然年后的年末最后一天<br>
 *       例如: 2025年 + 1年 = 2026年12月31日</li>
 * </ul>
 * 如果值为 0，表示永不过期，返回 null。
 *
 * <p>提取自 PointGrantService.calculateExpiryDate 和 RewardExecutor.calculateTierExpiry，
 * 消除两者之间的逻辑重复，确保积分过期和等级过期使用同一算法。
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.8.0
 */
public final class ExpiryCalculator {

    private ExpiryCalculator() {}

    /**
     * 计算过期时间。
     *
     * @param from  基准时间 (通常为 LocalDateTime.now())
     * @param mode  过期模式: FIXED_DAYS / CALENDAR_MONTHS / CALENDAR_YEARS
     * @param value 过期值 (天数、月数或年数；0 表示永不过期)
     * @return 过期时间，或 null 表示永不过期
     */
    public static LocalDateTime calculateExpiry(LocalDateTime from, String mode, int value) {
        if (value == 0) {
            return null;
        }

        if (mode == null || mode.isBlank()) {
            mode = "FIXED_DAYS";
        }

        switch (mode) {
            case "CALENDAR_MONTHS":
                // N 个完整自然月后，月末最后一天
                LocalDateTime monthEnd = from.plusMonths(value);
                monthEnd = monthEnd.withDayOfMonth(monthEnd.toLocalDate().lengthOfMonth());
                return monthEnd.withHour(23).withMinute(59).withSecond(59);

            case "CALENDAR_YEARS":
                // N 个完整自然年后，年末最后一天
                LocalDateTime yearEnd = from.plusYears(value);
                yearEnd = yearEnd.withMonth(12).withDayOfMonth(31);
                return yearEnd.withHour(23).withMinute(59).withSecond(59);

            case "FIXED_DAYS":
            default:
                return from.plusDays(value);
        }
    }
}
