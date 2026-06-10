package com.pythontopia.mainline.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

/**
 * chapter_test 行投影（后端内部权威）。
 *
 * <p>{@code questions} 含完整题面 + 答案 + 解析 + 证据页 id，<b>绝不</b>整体序列化给前端；
 * 下发题面时经 {@code ChapterTestPaperMapper} 去答案投影（§4.2）。
 */
public record ChapterTestRecord(
    long id,
    long userId,
    int chapter,
    ChapterTestStatus status,
    int attemptNo,
    JsonNode questions,
    JsonNode profileSnapshot,
    JsonNode generatorMeta,
    int timeLimitSec,
    OffsetDateTime startedAt,
    OffsetDateTime deadlineAt,
    OffsetDateTime submittedAt,
    Integer score,
    JsonNode detail,
    JsonNode integrity,
    OffsetDateTime createdAt) {}
