package com.pythontopia.world.calendar;

import java.util.List;

/**
 * 游戏内日历推进的纯函数工具，集中放置以便 {@code WorldAdvanceService} 与后续定时任务共用
 * 同一套星期/季节轮转逻辑。
 */
public final class CalendarMath {

  /** 下标 0=sunday … 6=saturday，与 V106 中 {@code available_days} 的约定一致。 */
  static final List<String> WEEKDAYS =
      List.of("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday");

  static final List<String> SEASONS = List.of("spring", "summer", "autumn", "winter");

  private CalendarMath() {}

  /** 将星期向后推进一天；无法识别的输入回退为 {@code monday}。 */
  public static String nextWeekday(String current) {
    int idx = Math.max(0, WEEKDAYS.indexOf(safe(current, "monday")));
    return WEEKDAYS.get((idx + 1) % WEEKDAYS.size());
  }

  /** 返回从 0 开始的下标，对应 V106 {@code available_days} 整型数组。 */
  public static int weekdayIndex(String weekday) {
    int idx = WEEKDAYS.indexOf(safe(weekday, "monday"));
    return idx < 0 ? 1 : idx;
  }

  /**
   * 每 28 个游戏内日期换一次季节（spring→summer→autumn→winter 循环）；换季时月份索引一并 +1
   * （超过 12 回绕到 1），否则月份与季节保持不变。
   */
  public static SeasonRoll advanceSeason(int currentDayIndex, int currentMonthIndex, String currentSeason) {
    int newDay = currentDayIndex + 1;
    int seasonsPerYear = SEASONS.size();
    int daysPerSeason = 28;
    if (newDay > 1 && (newDay - 1) % daysPerSeason == 0) {
      int currentSeasonIdx = Math.max(0, SEASONS.indexOf(safe(currentSeason, "spring")));
      int nextSeasonIdx = (currentSeasonIdx + 1) % seasonsPerYear;
      int newMonth = currentMonthIndex >= 12 ? 1 : currentMonthIndex + 1;
      return new SeasonRoll(newMonth, SEASONS.get(nextSeasonIdx));
    }
    return new SeasonRoll(currentMonthIndex, safe(currentSeason, "spring"));
  }

  private static String safe(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  public record SeasonRoll(int monthIndex, String season) {}
}
