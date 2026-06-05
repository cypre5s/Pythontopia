package com.pythontopia.learner.profile.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** applyDimensionUpdate 返回结果：写入后该维度子对象的完整快照。 */
public record DimensionUpdateResult(Long userId, String dimensionKey, JsonNode finalDimension) {}
