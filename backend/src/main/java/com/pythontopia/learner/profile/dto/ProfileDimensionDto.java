package com.pythontopia.learner.profile.dto;

import java.util.List;

/**
 * 单个画像维度的对外快照，供前端雷达图与自评 UI 使用。
 *
 * @param key 维度英文 key（professional_background 等）
 * @param label 维度中文标签
 * @param score 0..1 归一化得分（已做读时 decay 的 effective 值；缺省 0.5）
 * @param confidence 0..1 置信度 = min(1, evidence_count/8)，&lt;0.3 视为信号不足、不强断言
 * @param evidenceCount 累计证据条数
 * @param selfEditable 学生是否可自评直写该维度
 * @param tags 该维度累积的标签（如媒介偏好 video/visual）
 */
public record ProfileDimensionDto(
    String key,
    String label,
    double score,
    double confidence,
    int evidenceCount,
    boolean selfEditable,
    List<String> tags) {}
