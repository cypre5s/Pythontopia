package com.pythontopia.world.boss.review;

import com.pythontopia.world.boss.dto.BossInternalProblem;

/** An immutable approved-bank selection with a collision-free public/judging problem id. */
public record ApprovedBossProblem(long reviewId, int version, BossInternalProblem problem) {}
