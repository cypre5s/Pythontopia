package com.pythontopia.world.calendar;

import java.util.List;

/**
 * Pure helpers for the in-game calendar tick. Centralised so {@code WorldAdvanceService} and
 * future cron jobs share identical week/season rotation logic.
 */
public final class CalendarMath {

  /** Index 0 = sunday … 6 = saturday, mirroring the {@code available_days} convention from V106. */
  static final List<String> WEEKDAYS =
      List.of("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday");

  static final List<String> SEASONS = List.of("spring", "summer", "autumn", "winter");

  private CalendarMath() {}

  /** Advance the weekday by one calendar day. Unknown inputs roll over to {@code monday}. */
  public static String nextWeekday(String current) {
    int idx = Math.max(0, WEEKDAYS.indexOf(safe(current, "monday")));
    return WEEKDAYS.get((idx + 1) % WEEKDAYS.size());
  }

  /** Zero-based index for use with the V106 {@code available_days} integer array. */
  public static int weekdayIndex(String weekday) {
    int idx = WEEKDAYS.indexOf(safe(weekday, "monday"));
    return idx < 0 ? 1 : idx;
  }

  /**
   * Roll the season once every 28 in-game days (4-month year, 7-day weeks). When the season rolls,
   * the month index also bumps; otherwise it stays. Returns {@code [newMonthIndex, newSeason]} as a
   * 2-element array of strings to keep the call site terse.
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
