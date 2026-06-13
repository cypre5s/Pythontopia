using Pythontopia.Core.Dto;

namespace Pythontopia.Core.Api;

/// <summary>
/// Typed access to the Pythontopia backend (prefix <c>/api</c>). Every call attaches
/// the current <see cref="AuthToken"/> as an <c>Authorization: Bearer</c> header when set.
/// All failures surface as <see cref="ApiException"/>.
/// </summary>
public interface IApiClient
{
    /// <summary>JWT bearer token applied to outgoing requests; null clears it.</summary>
    string? AuthToken { get; set; }

    // ---- auth ----
    Task<LoginResponse> LoginAsync(LoginRequest request, CancellationToken ct = default);
    Task<UserDto> RegisterAsync(RegisterRequest request, CancellationToken ct = default);

    // ---- game ----
    Task<GameBootstrapResponse> BootstrapGameAsync(CancellationToken ct = default);

    // ---- mainline (主线地图) ----
    /// <summary>GET /api/mainline/overview — the chapter/stage learning map with看/学/练 progress and stars.</summary>
    Task<MainlineOverviewDto> GetMainlineOverviewAsync(CancellationToken ct = default);
    /// <summary>GET /api/mainline/stages/{id} — full stage detail (videos / examples / questions + progress).</summary>
    Task<StageDetailDto> GetStageDetailAsync(long stageId, CancellationToken ct = default);
    /// <summary>POST /api/mainline/stages/{id}/video-progress — add watched seconds; returns updated progress.</summary>
    Task<StageProgressDto> ReportVideoProgressAsync(long stageId, int deltaSec, CancellationToken ct = default);
    /// <summary>POST /api/mainline/stages/{id}/pages-read — mark courseware pages read; returns updated progress.</summary>
    Task<StageProgressDto> ReportPagesReadAsync(long stageId, IReadOnlyList<long> pageIds, CancellationToken ct = default);

    // ---- review hall (复盘大厅) ----
    /// <summary>GET /api/learner/twin/weekly — 数字孪生周报 (memory brief / insights / mastery deltas / resource runs).</summary>
    Task<WeeklyReportDto> GetWeeklyReportAsync(CancellationToken ct = default);
    /// <summary>GET /api/learner/oracle/risk — per-KC forgetting/failure risk predictions.</summary>
    Task<IReadOnlyList<RiskPredictionDto>> GetRiskPredictionsAsync(CancellationToken ct = default);

    // ---- chapter test (章节测验) ----
    /// <summary>GET /api/mainline/chapters/{chapter}/test/latest — latest chapter test result (chapter 1-7).</summary>
    Task<ChapterTestResultDto> GetLatestChapterTestAsync(int chapter, CancellationToken ct = default);
    /// <summary>POST /api/mainline/chapters/{chapter}/test/prepare — trigger paper generation.</summary>
    Task<TestPrepareResponse> PrepareChapterTestAsync(int chapter, CancellationToken ct = default);
    /// <summary>POST /api/mainline/chapters/{chapter}/test/start — fetch the answer-stripped paper.</summary>
    Task<ChapterTestPaperDto> StartChapterTestAsync(int chapter, CancellationToken ct = default);
    /// <summary>POST /api/mainline/chapters/{chapter}/test/{testId}/submit — submit answers; server-side scored.</summary>
    Task<ChapterTestResultDto> SubmitChapterTestAsync(int chapter, long testId, IReadOnlyDictionary<string, string> answers, CancellationToken ct = default);

    // ---- course examples (例题库) ----
    /// <summary>GET /api/course/examples — worked examples, optionally filtered by KC code.</summary>
    Task<IReadOnlyList<CourseExampleDto>> GetExamplesAsync(string? kcCode = null, CancellationToken ct = default);
    /// <summary>GET /api/course/examples/{id} — a single worked example.</summary>
    Task<CourseExampleDto> GetExampleAsync(long exampleId, CancellationToken ct = default);

    // ---- world calendar (世界日历) ----
    /// <summary>GET /api/world/calendar/today — in-game date / season / weather snapshot.</summary>
    Task<CalendarTodayDto> GetCalendarTodayAsync(CancellationToken ct = default);
    /// <summary>GET /api/world/calendar/events — pending world events.</summary>
    Task<IReadOnlyList<WorldEventDto>> GetWorldEventsAsync(CancellationToken ct = default);

    // ---- learner reflection (学情洞察 / 记忆) ----
    /// <summary>GET /api/learner/insights — reflective insights (student-only).</summary>
    Task<IReadOnlyList<LearnerInsightDto>> GetInsightsAsync(int limit = 10, CancellationToken ct = default);
    /// <summary>GET /api/learner/memory — agent-notebook memory items (student-only).</summary>
    Task<IReadOnlyList<MemoryItemDto>> GetMemoryAsync(string? focusKc = null, int limit = 8, CancellationToken ct = default);

    // ---- bilibili videos (B 站精选) ----
    /// <summary>GET /api/videos/bilibili?keyword= — external Bilibili explainer videos.</summary>
    Task<IReadOnlyList<BilibiliVideoDto>> SearchBilibiliVideosAsync(string keyword, int limit = 8, CancellationToken ct = default);

    // ---- save slots (存档) ----
    /// <summary>GET /api/world/saves — the learner's RPG save slots (0-5).</summary>
    Task<IReadOnlyList<SaveSlotDto>> GetSaveSlotsAsync(CancellationToken ct = default);
    /// <summary>POST /api/world/saves/{slotIndex}/activate — activate a save slot.</summary>
    Task<SaveSlotDto> ActivateSaveSlotAsync(int slotIndex, CancellationToken ct = default);

    // ---- feynman (费曼学弟 teach-back) ----
    /// <summary>POST /api/feynman/sessions — start a teach-back session for a KC (student-only).</summary>
    Task<FeynmanSessionDto> StartFeynmanAsync(string kcCode, CancellationToken ct = default);
    /// <summary>POST /api/feynman/sessions/{id}/teach — submit an explanation; returns the tutee's turn.</summary>
    Task<FeynmanTurnDto> FeynmanTeachAsync(long sessionId, string explanation, CancellationToken ct = default);
    /// <summary>POST /api/feynman/sessions/{id}/judge — judge the teach-back; returns the verdict.</summary>
    Task<FeynmanVerdictDto> FeynmanJudgeAsync(long sessionId, CancellationToken ct = default);

    // ---- boss battle (Boss 副本) ----
    /// <summary>POST /api/world/boss/{chapter}/problem — fetch a boss problem for chapter (1-7) / stage (1-4).</summary>
    Task<BossProblemDto> RequestBossProblemAsync(int chapter, int stage, string? kcCode = null, int? difficulty = null, CancellationToken ct = default);
    /// <summary>POST /api/world/boss/{chapter}/attempt — submit a solution; server-side judged.</summary>
    Task<BossAttemptResultDto> SubmitBossAttemptAsync(int chapter, int stage, string problemId, string code, int elapsedSec, CancellationToken ct = default);

    // ---- endless (无尽模式) ----
    /// <summary>POST /api/mainline/endless/summary — report an endless-run result; returns whether a star was awarded.</summary>
    Task<StarAwardResultDto> SubmitEndlessSummaryAsync(int chapter, int correct, int total, int streak, CancellationToken ct = default);

    // ---- skills (技能树) ----
    /// <summary>GET /api/world/skills — the skill tree plus the learner's unlocked skills.</summary>
    Task<SkillsResponse> GetSkillsAsync(CancellationToken ct = default);
    /// <summary>POST /api/world/skills/{skillKey}/unlock — unlock a skill (409 SKILL_LOCKED if condition unmet).</summary>
    Task<LearnerSkillDto> UnlockSkillAsync(string skillKey, CancellationToken ct = default);

    // ---- npc (NPC 互动) ----
    /// <summary>GET /api/npc/{npcKey}/greeting — the NPC's grounded greeting.</summary>
    Task<NpcGreetingDto> GetNpcGreetingAsync(string npcKey, CancellationToken ct = default);
    /// <summary>POST /api/npc/{npcKey}/dialog — talk to an NPC (student-only).</summary>
    Task<NpcDialogResponseDto> TalkToNpcAsync(string npcKey, string message, CancellationToken ct = default);

    // ---- course documents (课件库 PDF) ----
    /// <summary>GET /api/course/documents/{docId}/pdf — raw PDF bytes (doc 186-192); fetch with bearer then render locally.</summary>
    Task<byte[]> GetCourseDocumentPdfAsync(long docId, CancellationToken ct = default);

    // ---- learning path (学习路径) ----
    /// <summary>GET /api/learner/path/active — the active personalized path (throws 404 PATH_NOT_FOUND if none).</summary>
    Task<LearningPathDto> GetActiveLearningPathAsync(CancellationToken ct = default);
    /// <summary>POST /api/learner/path/plan — (re)plan the personalized path (student-only).</summary>
    Task<PlanPathResponse> PlanLearningPathAsync(CancellationToken ct = default);

    // ---- learner profile / mastery ----
    /// <summary>Returns null when the backend responds 204 (no profile yet).</summary>
    Task<LearnerProfileDto?> GetProfileAsync(CancellationToken ct = default);
    Task<IReadOnlyList<ProfileDimensionDto>> GetProfileDimensionsAsync(CancellationToken ct = default);
    Task UpdateProfileDimensionAsync(string key, DimensionSelfUpdateRequest request, CancellationToken ct = default);
    Task<IReadOnlyList<MasterySnapshotDto>> GetMasteryAsync(CancellationToken ct = default);

    // ---- quests ----
    Task<IReadOnlyList<QuestDto>> GetTodayQuestsAsync(CancellationToken ct = default);
    Task<IReadOnlyList<QuestDto>> GetQuestsAsync(string? status = null, int limit = 30, CancellationToken ct = default);
    Task<QuestDto> StartQuestAsync(long questId, CancellationToken ct = default);
    Task<QuestDto> CompleteQuestAsync(long questId, CancellationToken ct = default);
    Task<QuestDto> SkipQuestAsync(long questId, CancellationToken ct = default);

    // ---- course pack ----
    Task<CoursePackDto> GetCoursePackAsync(CancellationToken ct = default);
    Task<IReadOnlyList<ChapterDto>> GetChaptersAsync(CancellationToken ct = default);
    Task<IReadOnlyList<KcDto>> GetKcsAsync(long? chapterId = null, CancellationToken ct = default);
    Task<IReadOnlyList<PageDto>> GetPagesAsync(string kcCode, CancellationToken ct = default);
    Task<IReadOnlyList<ProblemDto>> GetProblemsAsync(string? kcCode = null, CancellationToken ct = default);
    Task<IReadOnlyList<CourseVideoDto>> GetVideosAsync(int? chapterId = null, CancellationToken ct = default);
    Task<byte[]> GetPageImageAsync(long pageId, CancellationToken ct = default);
    /// <summary>Absolute URL for a courseware page image (public endpoint, bindable in XAML).</summary>
    Uri GetPageImageUri(long pageId);
    /// <summary>Absolute URL for a course video stream (public, Range-enabled).</summary>
    Uri GetVideoUri(long videoId);

    // ---- problems ----
    Task<PublicProblemDto> GetPublicProblemAsync(long problemId, CancellationToken ct = default);

    // ---- submissions ----
    Task<SubmissionDto> CreateSubmissionAsync(SubmissionCreateRequest request, CancellationToken ct = default);
    Task<SubmissionDetailDto> GetSubmissionAsync(long submissionId, CancellationToken ct = default);
    /// <summary>
    /// Creates a TRAINING submission then polls GET /api/submissions/{id} until the status
    /// reaches PASSED / FAILED / ERROR (or the call is cancelled / times out).
    /// </summary>
    Task<SubmissionDetailDto> SubmitAndPollAsync(long problemId, string code, IProgress<string>? progress = null, CancellationToken ct = default);
    /// <summary>
    /// Polls GET /api/submissions/{id} until the status reaches PASSED / FAILED / ERROR. Used to
    /// follow up submissions created out-of-band (e.g. Bug 矿洞 retry / variant submits).
    /// </summary>
    Task<SubmissionDetailDto> PollSubmissionAsync(long submissionId, IProgress<string>? progress = null, CancellationToken ct = default);

    // ---- bug mine (错题矿洞 / 遗忘回声) ----
    Task<IReadOnlyList<BugMineErrorBookItemDto>> GetBugMineErrorBookAsync(CancellationToken ct = default);
    Task<IReadOnlyList<BugMineEchoCandidateDto>> GetBugMineEchoesAsync(CancellationToken ct = default);
    Task<BugMineSessionDto> OpenBugMineEchoAsync(string kcCode, CancellationToken ct = default);
    Task<BugMineSessionDto> OpenBugMineSessionAsync(long submissionId, CancellationToken ct = default);
    Task<BugMineSessionDto> GetBugMineSessionAsync(long sessionId, CancellationToken ct = default);
    Task<BugMineCardDto> RevealBugMineCardAsync(long sessionId, long cardId, CancellationToken ct = default);
    Task<SubmissionDto> BugMineRetrySubmitAsync(long sessionId, string code, CancellationToken ct = default);
    Task<SubmissionDto> BugMineVariantSubmitAsync(long sessionId, string code, CancellationToken ct = default);
    /// <summary><paramref name="status"/> is RESOLVED or ABANDONED.</summary>
    Task<BugMineSessionDto> CloseBugMineSessionAsync(long sessionId, string status, CancellationToken ct = default);

    // ---- intent router / resource workshop (百晓工坊) ----
    Task<IntentRouteResponse> RouteIntentAsync(IntentRouteRequest request, CancellationToken ct = default);
    Task<ResourceCreateResponse> CreateResourceAsync(ResourceCreateRequest request, CancellationToken ct = default);
    Task<IReadOnlyList<ResourceListItemDto>> GetResourcesAsync(string? type = null, string? kcCode = null, int limit = 20, int offset = 0, CancellationToken ct = default);
    Task<LearningResourceDto> GetResourceAsync(long resourceId, CancellationToken ct = default);
    /// <summary>Streams resource generation (POST /api/workshop/resources/stream) as <c>plan</c>/<c>trace</c>/<c>result</c> SSE frames.</summary>
    IAsyncEnumerable<SseEvent> StreamResourceAsync(ResourceCreateRequest request, CancellationToken ct = default);
    /// <summary>Streams the 学习总管 (POST /api/orchestrator/turn/stream) as <c>trace</c>/<c>token</c>/<c>result</c> SSE frames.</summary>
    IAsyncEnumerable<SseEvent> StreamOrchestratorTurnAsync(OrchestratorTurnRequest request, CancellationToken ct = default);

    // ---- classrooms ----
    Task<IReadOnlyList<ClassroomDto>> GetMyClassroomsAsync(CancellationToken ct = default);
    Task<ClassroomDto> JoinClassroomAsync(string joinCode, CancellationToken ct = default);
    Task<IReadOnlyList<ClassroomQuestDto>> GetMyClassroomQuestsAsync(CancellationToken ct = default);

    // ---- tutor (SSE) ----
    /// <summary>
    /// Streams the tutor answer as Server-Sent Events. Each yielded string is the
    /// <c>data:</c> payload of a <c>plan</c>/<c>trace</c>/<c>token</c>/<c>result</c> event,
    /// in arrival order; an <c>error</c> event throws an <see cref="ApiException"/>.
    /// </summary>
    IAsyncEnumerable<string> AskTutorStreamAsync(TutorAskRequest request, CancellationToken ct = default);
}
