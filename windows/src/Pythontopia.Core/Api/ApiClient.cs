using System.Net;
using System.Net.Http.Headers;
using System.Runtime.CompilerServices;
using System.Text;
using System.Text.Json;
using Pythontopia.Core.Config;
using Pythontopia.Core.Dto;
using Pythontopia.Core.Json;

namespace Pythontopia.Core.Api;

/// <summary>HttpClient-backed <see cref="IApiClient"/>. Safe to register as a singleton.</summary>
public sealed class ApiClient : IApiClient
{
    private readonly HttpClient _http;
    private readonly string _baseUrl;
    private readonly TimeSpan _requestTimeout;

    public string? AuthToken { get; set; }

    public ApiClient(HttpClient http, AppConfig config)
    {
        _http = http ?? throw new ArgumentNullException(nameof(http));
        ArgumentNullException.ThrowIfNull(config);
        _baseUrl = config.BackendBaseUrl.TrimEnd('/');
        _requestTimeout = config.RequestTimeout;
        // Per-request timeouts are managed with a linked CancelAfter token so that
        // long-lived SSE streams are never aborted by HttpClient.Timeout.
        _http.Timeout = System.Threading.Timeout.InfiniteTimeSpan;
    }

    /// <summary>Convenience factory that owns its own <see cref="HttpClient"/>.</summary>
    public static ApiClient Create(AppConfig config) => new(new HttpClient(), config);

    // ---------------------------------------------------------------- auth ----

    public Task<LoginResponse> LoginAsync(LoginRequest request, CancellationToken ct = default) =>
        SendJsonAsync<LoginResponse>(HttpMethod.Post, "/api/auth/login", request, ct);

    public Task<UserDto> RegisterAsync(RegisterRequest request, CancellationToken ct = default) =>
        SendJsonAsync<UserDto>(HttpMethod.Post, "/api/auth/register", request, ct);

    // ---------------------------------------------------------------- game ----

    public Task<GameBootstrapResponse> BootstrapGameAsync(CancellationToken ct = default) =>
        SendJsonAsync<GameBootstrapResponse>(HttpMethod.Post, "/api/game/bootstrap", null, ct);

    // ----------------------------------------------------- profile / mastery ----

    public async Task<LearnerProfileDto?> GetProfileAsync(CancellationToken ct = default)
    {
        using var response = await SendAsync(HttpMethod.Get, "/api/learner/profile", null, ct).ConfigureAwait(false);
        if (response.StatusCode == HttpStatusCode.NoContent)
        {
            return null;
        }
        return await ReadJsonAsync<LearnerProfileDto>(response, ct).ConfigureAwait(false);
    }

    public async Task<IReadOnlyList<ProfileDimensionDto>> GetProfileDimensionsAsync(CancellationToken ct = default) =>
        await SendJsonAsync<List<ProfileDimensionDto>>(HttpMethod.Get, "/api/learner/profile/dimensions", null, ct).ConfigureAwait(false);

    public async Task UpdateProfileDimensionAsync(string key, DimensionSelfUpdateRequest request, CancellationToken ct = default)
    {
        var path = $"/api/learner/profile/dimensions/{Uri.EscapeDataString(key)}";
        using var response = await SendAsync(HttpMethod.Put, path, request, ct).ConfigureAwait(false);
        await EnsureSuccessAsync(response, ct).ConfigureAwait(false);
    }

    public async Task<IReadOnlyList<MasterySnapshotDto>> GetMasteryAsync(CancellationToken ct = default) =>
        await SendJsonAsync<List<MasterySnapshotDto>>(HttpMethod.Get, "/api/learner/mastery", null, ct).ConfigureAwait(false);

    // -------------------------------------------------------------- quests ----

    public async Task<IReadOnlyList<QuestDto>> GetTodayQuestsAsync(CancellationToken ct = default) =>
        await SendJsonAsync<List<QuestDto>>(HttpMethod.Get, "/api/quests/today", null, ct).ConfigureAwait(false);

    public async Task<IReadOnlyList<QuestDto>> GetQuestsAsync(string? status = null, int limit = 30, CancellationToken ct = default)
    {
        var query = new QueryBuilder()
            .Add("status", status)
            .Add("limit", limit)
            .Build();
        return await SendJsonAsync<List<QuestDto>>(HttpMethod.Get, "/api/quests" + query, null, ct).ConfigureAwait(false);
    }

    public Task<QuestDto> StartQuestAsync(long questId, CancellationToken ct = default) =>
        SendJsonAsync<QuestDto>(HttpMethod.Post, $"/api/quests/{questId}/start", null, ct);

    public Task<QuestDto> CompleteQuestAsync(long questId, CancellationToken ct = default) =>
        SendJsonAsync<QuestDto>(HttpMethod.Post, $"/api/quests/{questId}/complete", null, ct);

    public Task<QuestDto> SkipQuestAsync(long questId, CancellationToken ct = default) =>
        SendJsonAsync<QuestDto>(HttpMethod.Post, $"/api/quests/{questId}/skip", null, ct);

    // --------------------------------------------------------------- course ----

    public Task<CoursePackDto> GetCoursePackAsync(CancellationToken ct = default) =>
        SendJsonAsync<CoursePackDto>(HttpMethod.Get, "/api/course/pack", null, ct);

    public async Task<IReadOnlyList<ChapterDto>> GetChaptersAsync(CancellationToken ct = default) =>
        await SendJsonAsync<List<ChapterDto>>(HttpMethod.Get, "/api/course/chapters", null, ct).ConfigureAwait(false);

    public async Task<IReadOnlyList<KcDto>> GetKcsAsync(long? chapterId = null, CancellationToken ct = default)
    {
        var query = new QueryBuilder().Add("chapterId", chapterId).Build();
        return await SendJsonAsync<List<KcDto>>(HttpMethod.Get, "/api/course/kcs" + query, null, ct).ConfigureAwait(false);
    }

    public async Task<IReadOnlyList<PageDto>> GetPagesAsync(string kcCode, CancellationToken ct = default)
    {
        var query = new QueryBuilder().Add("kcCode", kcCode).Build();
        return await SendJsonAsync<List<PageDto>>(HttpMethod.Get, "/api/course/pages" + query, null, ct).ConfigureAwait(false);
    }

    public async Task<IReadOnlyList<ProblemDto>> GetProblemsAsync(string? kcCode = null, CancellationToken ct = default)
    {
        var query = new QueryBuilder().Add("kcCode", kcCode).Build();
        return await SendJsonAsync<List<ProblemDto>>(HttpMethod.Get, "/api/course/problems" + query, null, ct).ConfigureAwait(false);
    }

    public async Task<IReadOnlyList<CourseVideoDto>> GetVideosAsync(int? chapterId = null, CancellationToken ct = default)
    {
        var query = new QueryBuilder().Add("chapterId", chapterId).Build();
        return await SendJsonAsync<List<CourseVideoDto>>(HttpMethod.Get, "/api/course/videos" + query, null, ct).ConfigureAwait(false);
    }

    public async Task<byte[]> GetPageImageAsync(long pageId, CancellationToken ct = default)
    {
        using var response = await SendAsync(HttpMethod.Get, $"/api/course/pages/{pageId}/image", null, ct).ConfigureAwait(false);
        await EnsureSuccessAsync(response, ct).ConfigureAwait(false);
        return await response.Content.ReadAsByteArrayAsync(ct).ConfigureAwait(false);
    }

    public Uri GetPageImageUri(long pageId) => new($"{_baseUrl}/api/course/pages/{pageId}/image");

    public Uri GetVideoUri(long videoId) => new($"{_baseUrl}/api/course/videos/{videoId}");

    // ------------------------------------------------------------- problems ----

    public Task<PublicProblemDto> GetPublicProblemAsync(long problemId, CancellationToken ct = default) =>
        SendJsonAsync<PublicProblemDto>(HttpMethod.Get, $"/api/problems/{problemId}/public", null, ct);

    // ---------------------------------------------------------- submissions ----

    public Task<SubmissionDto> CreateSubmissionAsync(SubmissionCreateRequest request, CancellationToken ct = default) =>
        SendJsonAsync<SubmissionDto>(HttpMethod.Post, "/api/submissions", request, ct);

    public Task<SubmissionDetailDto> GetSubmissionAsync(long submissionId, CancellationToken ct = default) =>
        SendJsonAsync<SubmissionDetailDto>(HttpMethod.Get, $"/api/submissions/{submissionId}", null, ct);

    public async Task<SubmissionDetailDto> SubmitAndPollAsync(
        long problemId, string code, IProgress<string>? progress = null, CancellationToken ct = default)
    {
        progress?.Report("提交中…");
        var created = await CreateSubmissionAsync(new SubmissionCreateRequest(problemId, code), ct).ConfigureAwait(false);

        var terminal = new HashSet<string>(StringComparer.OrdinalIgnoreCase) { "PASSED", "FAILED", "ERROR" };
        const int maxAttempts = 60;
        var delay = TimeSpan.FromMilliseconds(800);

        for (var attempt = 0; attempt < maxAttempts; attempt++)
        {
            ct.ThrowIfCancellationRequested();
            var detail = await GetSubmissionAsync(created.Id, ct).ConfigureAwait(false);
            var status = detail.Submission.Status;
            progress?.Report($"判题状态：{status ?? "?"}");
            if (status is not null && terminal.Contains(status))
            {
                return detail;
            }
            await Task.Delay(delay, ct).ConfigureAwait(false);
        }

        throw new ApiException(
            HttpStatusCode.RequestTimeout,
            $"提交 {created.Id} 在 {maxAttempts} 次轮询后仍未判完",
            code: "SUBMISSION_POLL_TIMEOUT");
    }

    // ----------------------------------------------------------- classrooms ----

    public async Task<IReadOnlyList<ClassroomDto>> GetMyClassroomsAsync(CancellationToken ct = default) =>
        await SendJsonAsync<List<ClassroomDto>>(HttpMethod.Get, "/api/classrooms/mine", null, ct).ConfigureAwait(false);

    public Task<ClassroomDto> JoinClassroomAsync(string joinCode, CancellationToken ct = default) =>
        SendJsonAsync<ClassroomDto>(HttpMethod.Post, "/api/classrooms/join", new JoinClassroomRequest(joinCode), ct);

    public async Task<IReadOnlyList<ClassroomQuestDto>> GetMyClassroomQuestsAsync(CancellationToken ct = default) =>
        await SendJsonAsync<List<ClassroomQuestDto>>(HttpMethod.Get, "/api/classroom/quests/mine", null, ct).ConfigureAwait(false);

    // ---------------------------------------------------------- tutor (SSE) ----

    public async IAsyncEnumerable<string> AskTutorStreamAsync(
        TutorAskRequest request, [EnumeratorCancellation] CancellationToken ct = default)
    {
        using var response = await SendAsync(HttpMethod.Post, "/api/tutor/ask", request, ct, acceptSse: true)
            .ConfigureAwait(false);
        await EnsureSuccessAsync(response, ct).ConfigureAwait(false);

        await using var stream = await response.Content.ReadAsStreamAsync(ct).ConfigureAwait(false);
        using var reader = new StreamReader(stream, Encoding.UTF8);

        string? currentEvent = null;
        var data = new StringBuilder();

        while (!reader.EndOfStream)
        {
            ct.ThrowIfCancellationRequested();
            var line = await reader.ReadLineAsync(ct).ConfigureAwait(false);
            if (line is null)
            {
                break;
            }

            if (line.Length == 0)
            {
                // Blank line terminates an SSE event.
                if (data.Length > 0 || currentEvent is not null)
                {
                    var payload = data.ToString();
                    var name = currentEvent ?? "message";
                    data.Clear();
                    currentEvent = null;

                    if (string.Equals(name, "error", StringComparison.OrdinalIgnoreCase))
                    {
                        throw new ApiException(
                            HttpStatusCode.BadGateway,
                            string.IsNullOrWhiteSpace(payload) ? "导师服务返回错误" : payload,
                            code: "TUTOR_STREAM_ERROR");
                    }

                    yield return payload;
                }
                continue;
            }

            if (line[0] == ':')
            {
                continue; // SSE comment / heartbeat
            }

            if (line.StartsWith("event:", StringComparison.Ordinal))
            {
                currentEvent = line[6..].Trim();
            }
            else if (line.StartsWith("data:", StringComparison.Ordinal))
            {
                if (data.Length > 0)
                {
                    data.Append('\n');
                }
                data.Append(line[5..].TrimStart());
            }
        }
    }

    // -------------------------------------------------------------- plumbing ----

    private async Task<T> SendJsonAsync<T>(HttpMethod method, string path, object? body, CancellationToken ct)
    {
        using var response = await SendAsync(method, path, body, ct).ConfigureAwait(false);
        return await ReadJsonAsync<T>(response, ct).ConfigureAwait(false);
    }

    private async Task<HttpResponseMessage> SendAsync(
        HttpMethod method, string path, object? body, CancellationToken ct, bool acceptSse = false)
    {
        using var request = new HttpRequestMessage(method, _baseUrl + path);

        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue(
            acceptSse ? "text/event-stream" : "application/json"));

        if (!string.IsNullOrEmpty(AuthToken))
        {
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", AuthToken);
        }

        if (body is not null)
        {
            var json = JsonSerializer.Serialize(body, body.GetType(), PytJson.Default);
            request.Content = new StringContent(json, Encoding.UTF8, "application/json");
        }

        var completion = acceptSse
            ? HttpCompletionOption.ResponseHeadersRead
            : HttpCompletionOption.ResponseContentRead;

        // SSE streams run until the server or the caller's token ends; every other
        // call layers a bounded timeout on top of the caller's cancellation token.
        CancellationTokenSource? timeoutCts = null;
        var effectiveToken = ct;
        if (!acceptSse)
        {
            timeoutCts = CancellationTokenSource.CreateLinkedTokenSource(ct);
            timeoutCts.CancelAfter(_requestTimeout);
            effectiveToken = timeoutCts.Token;
        }

        try
        {
            return await _http.SendAsync(request, completion, effectiveToken).ConfigureAwait(false);
        }
        catch (HttpRequestException ex)
        {
            throw new ApiException(
                HttpStatusCode.ServiceUnavailable,
                $"无法连接后端 {_baseUrl}（{ex.Message}）",
                code: "NETWORK_ERROR",
                innerException: ex);
        }
        catch (OperationCanceledException ex) when (!ct.IsCancellationRequested)
        {
            throw new ApiException(
                HttpStatusCode.RequestTimeout,
                $"请求 {path} 超时",
                code: "TIMEOUT",
                innerException: ex);
        }
        finally
        {
            timeoutCts?.Dispose();
        }
    }

    private static async Task<T> ReadJsonAsync<T>(HttpResponseMessage response, CancellationToken ct)
    {
        await EnsureSuccessAsync(response, ct).ConfigureAwait(false);
        await using var stream = await response.Content.ReadAsStreamAsync(ct).ConfigureAwait(false);
        var value = await JsonSerializer.DeserializeAsync<T>(stream, PytJson.Default, ct).ConfigureAwait(false);
        if (value is null)
        {
            throw new ApiException(response.StatusCode, "服务端返回空响应体", code: "EMPTY_BODY");
        }
        return value;
    }

    private static async Task EnsureSuccessAsync(HttpResponseMessage response, CancellationToken ct)
    {
        if (response.IsSuccessStatusCode)
        {
            return;
        }

        string? body = null;
        try
        {
            body = await response.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
        }
        catch
        {
            // ignore body read failures; status code still drives the exception
        }

        throw ApiException.FromResponse(response.StatusCode, body);
    }

    /// <summary>Minimal helper to build query strings, skipping null/empty values.</summary>
    private sealed class QueryBuilder
    {
        private readonly List<string> _parts = new();

        public QueryBuilder Add(string key, string? value)
        {
            if (!string.IsNullOrWhiteSpace(value))
            {
                _parts.Add($"{Uri.EscapeDataString(key)}={Uri.EscapeDataString(value)}");
            }
            return this;
        }

        public QueryBuilder Add(string key, long? value)
        {
            if (value.HasValue)
            {
                _parts.Add($"{Uri.EscapeDataString(key)}={value.Value}");
            }
            return this;
        }

        public QueryBuilder Add(string key, int value)
        {
            _parts.Add($"{Uri.EscapeDataString(key)}={value}");
            return this;
        }

        public string Build() => _parts.Count == 0 ? string.Empty : "?" + string.Join("&", _parts);
    }
}
