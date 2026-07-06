package com.pythontopia.learner.profile.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

public record ProfileTurnResponse(
    @JsonAlias("next_question") String nextQuestion,
    @JsonAlias("profile_draft") LearnerProfileDto profileDraft,
    Boolean done,
    @JsonAlias("dimensions_filled") List<String> dimensionsFilled,
    @JsonAlias("evidence_anchors") List<EvidenceAnchorDto> evidenceAnchors,
    @JsonAlias("safety_result") CheckResultDto safetyResult,
    @JsonAlias("grounding_result") CheckResultDto groundingResult) {}
