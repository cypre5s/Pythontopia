package com.pythontopia.world.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.world.calendar.CalendarMath.SeasonRoll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure unit coverage for {@link CalendarMath} that does not need Spring, Postgres, or Flyway. The
 * helper is consumed both by {@code WorldAdvanceService.sleep} (season + weekday tick) and by
 * {@code ShopService.currentWeekdayIndex} (V106 {@code available_days} array lookups), so any
 * regression here silently corrupts both the world clock and the shop weekday filter.
 *
 * <p>Boundary lattice exercised below:
 *
 * <ul>
 *   <li>weekday rotation across every Sunday→Saturday→Sunday wrap point
 *   <li>weekday index for the seven canonical strings + null + blank + unknown fallback to "monday"
 *   <li>season roll triggers at day 28→29 / 56→57 / 84→85, with month index wrap from 12 back to 1
 *   <li>non-roll days preserve the input season and month
 *   <li>defensive fallbacks (null / blank / unknown season) collapse to "spring"
 * </ul>
 */
class CalendarMathTest {

  @Nested
  class NextWeekday {

    @Test
    void rotatesSundayToMonday() {
      assertThat(CalendarMath.nextWeekday("sunday")).isEqualTo("monday");
    }

    @Test
    void rotatesSaturdayBackToSunday() {
      assertThat(CalendarMath.nextWeekday("saturday")).isEqualTo("sunday");
    }

    @Test
    void rotatesAllSevenDaysInOrder() {
      String day = "sunday";
      String[] expected = {
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
      };
      for (String next : expected) {
        day = CalendarMath.nextWeekday(day);
        assertThat(day).isEqualTo(next);
      }
    }

    @Test
    void nullInputFallsBackToMondayAndAdvancesToTuesday() {
      assertThat(CalendarMath.nextWeekday(null)).isEqualTo("tuesday");
    }

    @Test
    void blankInputFallsBackToMondayAndAdvancesToTuesday() {
      assertThat(CalendarMath.nextWeekday("   ")).isEqualTo("tuesday");
      assertThat(CalendarMath.nextWeekday("")).isEqualTo("tuesday");
    }

    @Test
    void unknownWeekdayCollapsesToMondayViaSundayIndexZero() {
      assertThat(CalendarMath.nextWeekday("funday")).isEqualTo("monday");
    }
  }

  @Nested
  class WeekdayIndex {

    @Test
    void mapsCanonicalWeekdaysToZeroThroughSix() {
      assertThat(CalendarMath.weekdayIndex("sunday")).isZero();
      assertThat(CalendarMath.weekdayIndex("monday")).isEqualTo(1);
      assertThat(CalendarMath.weekdayIndex("tuesday")).isEqualTo(2);
      assertThat(CalendarMath.weekdayIndex("wednesday")).isEqualTo(3);
      assertThat(CalendarMath.weekdayIndex("thursday")).isEqualTo(4);
      assertThat(CalendarMath.weekdayIndex("friday")).isEqualTo(5);
      assertThat(CalendarMath.weekdayIndex("saturday")).isEqualTo(6);
    }

    @Test
    void unknownWeekdayDefaultsToMondayIndexOne() {
      assertThat(CalendarMath.weekdayIndex("funday")).isEqualTo(1);
    }

    @Test
    void nullAndBlankFallBackToMondayIndexOne() {
      assertThat(CalendarMath.weekdayIndex(null)).isEqualTo(1);
      assertThat(CalendarMath.weekdayIndex("")).isEqualTo(1);
      assertThat(CalendarMath.weekdayIndex("  ")).isEqualTo(1);
    }
  }

  @Nested
  class AdvanceSeason {

    @Test
    void doesNotRollOnFirstDayBecauseNewDayEqualsOne() {
      SeasonRoll roll = CalendarMath.advanceSeason(0, 1, "spring");

      assertThat(roll.season()).isEqualTo("spring");
      assertThat(roll.monthIndex()).isEqualTo(1);
    }

    @Test
    void doesNotRollOnDaysWithinSeasonWindow() {
      assertThat(CalendarMath.advanceSeason(1, 1, "spring").season()).isEqualTo("spring");
      assertThat(CalendarMath.advanceSeason(27, 1, "spring").season()).isEqualTo("spring");
      assertThat(CalendarMath.advanceSeason(27, 1, "spring").monthIndex()).isEqualTo(1);
    }

    @Test
    void rollsSpringToSummerOnDay28Boundary() {
      SeasonRoll roll = CalendarMath.advanceSeason(28, 1, "spring");

      assertThat(roll.season()).isEqualTo("summer");
      assertThat(roll.monthIndex()).isEqualTo(2);
    }

    @Test
    void rollsSummerToAutumnOnDay56Boundary() {
      SeasonRoll roll = CalendarMath.advanceSeason(56, 2, "summer");

      assertThat(roll.season()).isEqualTo("autumn");
      assertThat(roll.monthIndex()).isEqualTo(3);
    }

    @Test
    void rollsAutumnToWinterOnDay84Boundary() {
      SeasonRoll roll = CalendarMath.advanceSeason(84, 3, "autumn");

      assertThat(roll.season()).isEqualTo("winter");
      assertThat(roll.monthIndex()).isEqualTo(4);
    }

    @Test
    void rollsWinterBackToSpringOnDay112BoundaryEndingYear() {
      SeasonRoll roll = CalendarMath.advanceSeason(112, 4, "winter");

      assertThat(roll.season()).isEqualTo("spring");
      assertThat(roll.monthIndex()).isEqualTo(5);
    }

    @Test
    void monthIndexWrapsFromTwelveBackToOneWhenAtCap() {
      SeasonRoll roll = CalendarMath.advanceSeason(28, 12, "spring");

      assertThat(roll.monthIndex()).isOne();
      assertThat(roll.season()).isEqualTo("summer");
    }

    @Test
    void preservesMonthIndexOnNonRollDay() {
      SeasonRoll roll = CalendarMath.advanceSeason(15, 7, "summer");

      assertThat(roll.monthIndex()).isEqualTo(7);
      assertThat(roll.season()).isEqualTo("summer");
    }

    @Test
    void unknownSeasonFallsBackToSpringWhenRolling() {
      SeasonRoll roll = CalendarMath.advanceSeason(28, 1, "monsoon");

      assertThat(roll.season()).isEqualTo("summer");
    }

    @Test
    void nullSeasonOnNonRollDayCollapsesToSpring() {
      SeasonRoll roll = CalendarMath.advanceSeason(5, 1, null);

      assertThat(roll.season()).isEqualTo("spring");
      assertThat(roll.monthIndex()).isEqualTo(1);
    }

    @Test
    void blankSeasonOnNonRollDayCollapsesToSpring() {
      SeasonRoll roll = CalendarMath.advanceSeason(10, 1, "  ");

      assertThat(roll.season()).isEqualTo("spring");
    }

    @Test
    void rollChainAcrossOneFullYearReturnsToSpring() {
      String season = "spring";
      int month = 1;
      int[] boundaryDays = {28, 56, 84, 112};
      String[] expectedSeasons = {"summer", "autumn", "winter", "spring"};
      int[] expectedMonths = {2, 3, 4, 5};

      for (int i = 0; i < boundaryDays.length; i++) {
        SeasonRoll roll = CalendarMath.advanceSeason(boundaryDays[i], month, season);
        season = roll.season();
        month = roll.monthIndex();
        assertThat(season).isEqualTo(expectedSeasons[i]);
        assertThat(month).isEqualTo(expectedMonths[i]);
      }
    }
  }
}
