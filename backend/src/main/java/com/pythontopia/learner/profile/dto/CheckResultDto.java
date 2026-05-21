package com.pythontopia.learner.profile.dto;

import java.util.List;

public record CheckResultDto(Boolean passed, List<String> reasons) {}
