package com.pythontopia.mainline.dto;

import com.pythontopia.mainline.practice.dto.StagePracticeQuestionDto;
import java.util.List;

/**
 * stage 学习页详情（GET /api/mainline/stages/{stageId}，§9）：静态定义 + 当前用户已完成 id 集合。
 *
 * <p>R-14 三件套契约（Phase 0 冻结）：
 *
 * <ul>
 *   <li><b>看</b>：{@code videos} = 本章全部内置课程视频；{@code requiredVideoSec} = 本章总时长 × 80%。
 *   <li><b>学</b>：{@code coursewarePageIds} = 本小节 KC {@code page_refs} 对应页；{@code requiredPages} = 页数。
 *   <li><b>练</b>：{@code questions} = 本小节客观题（选/判/填，{@code requiredQuestions} 道全对）+
 *       {@code exampleProblemIds} OJ 编程题（{@code requiredExamples} 道走训练场判题）。
 * </ul>
 *
 * 进度集合：{@code questionsCorrectIds} 为已答对的客观题 id；{@code pagesReadIds}/{@code examplesDoneIds}
 * 与 {@code StageProgressDto} 命名一致。
 */
public record StageDetailDto(
    long id,
    int chapter,
    String chapterTitle,
    int index,
    String title,
    List<String> kcCodes,
    String videoKeyword,
    List<Long> coursewarePageIds,
    List<Long> exampleProblemIds,
    int requiredVideoSec,
    int requiredPages,
    int requiredExamples,
    int requiredQuestions,
    List<StageVideoDto> videos,
    List<StageExampleDto> examples,
    List<StagePracticeQuestionDto> questions,
    int videoWatchedSec,
    List<Long> pagesReadIds,
    List<Long> examplesDoneIds,
    List<Long> questionsCorrectIds,
    boolean completed) {}
