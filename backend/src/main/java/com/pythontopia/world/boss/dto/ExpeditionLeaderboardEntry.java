package com.pythontopia.world.boss.dto;

/** 班级排行榜单条：某同学的最高单局成绩 + 名次。 */
public record ExpeditionLeaderboardEntry(
    int rank, long userId, String displayName, int bestScore, int bestDepth, boolean self) {}
