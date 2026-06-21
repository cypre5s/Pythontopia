package com.pythontopia.world.boss.dto;

import java.util.List;

/**
 * 班级无尽远征排行榜（按最高单局得分）。
 *
 * @param entries 已排名的榜单（截断到 limit）。
 * @param selfRank 当前用户在全班中的名次（1 起；未上榜 / 未玩过为 null）。
 * @param selfBest 当前用户最高单局得分（未玩过为 0）。
 * @param inClass 当前用户是否已加入班级（未入班时榜单为空，提示加入班级）。
 */
public record ExpeditionLeaderboardDto(
    List<ExpeditionLeaderboardEntry> entries, Integer selfRank, int selfBest, boolean inClass) {}
