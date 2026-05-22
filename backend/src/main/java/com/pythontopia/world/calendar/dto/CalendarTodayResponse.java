package com.pythontopia.world.calendar.dto;

public record CalendarTodayResponse(
    int dayIndex,
    int monthIndex,
    String weekday,
    String season,
    String timeOfDay,
    String weather,
    int inGameMinutes) {}
