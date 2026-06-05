package com.pythontopia.bugmine.dto;

public record BugMineDepthStepDto(
    int layer,
    String key,
    String title,
    String description,
    boolean unlocked) {}
