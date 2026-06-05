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
