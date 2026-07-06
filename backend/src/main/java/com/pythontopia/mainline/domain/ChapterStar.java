package com.pythontopia.mainline.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

/** chapter_star 投影：每学生×章 0..3★ 唯一权威 + breakdown 构成。 */
public record ChapterStar(
    long id,
    long userId,
    int chapter,
    int stars,
    Integer bestTestScore,
    JsonNode breakdown,
    OffsetDateTime updatedAt) {}
