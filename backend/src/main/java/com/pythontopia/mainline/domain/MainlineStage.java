package com.pythontopia.mainline.domain;

import java.util.List;

/**
 * mainline_stage 静态定义投影（seed，全局共享）。
 *
 * <p>沿用本仓库约定：领域模型用不可变 record + JdbcTemplate 行映射（参见 {@code world.save.SaveSlot}），
 * 不引入 JPA 实体（本工程零 @Entity，且测试 profile 排除 JPA 自动装配）。
 */
public record MainlineStage(
    long id,
    int chapter,
    int stageIndex,
    String title,
    List<String> kcCodes,
    String videoKeyword,
    List<Long> coursewarePageIds,
    List<Long> exampleProblemIds,
    int requiredVideoSec,
    int requiredPages,
    int requiredExampleCount) {}
