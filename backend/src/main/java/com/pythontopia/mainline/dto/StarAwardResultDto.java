package com.pythontopia.mainline.dto;

/** 补星端点返回（§9）。starAwarded：本次是否真正发放补星（已发放/未达标/已封顶均 false）。 */
public record StarAwardResultDto(boolean starAwarded) {}
