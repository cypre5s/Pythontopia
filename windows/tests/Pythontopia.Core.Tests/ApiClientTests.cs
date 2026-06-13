using System.Net;
using Pythontopia.Core.Api;
using Pythontopia.Core.Config;
using Pythontopia.Core.Dto;
using Xunit;

namespace Pythontopia.Core.Tests;

public class ApiClientTests
{
    private static AppConfig TestConfig => new()
    {
        BackendBaseUrl = "http://test.local",
        RequestTimeout = TimeSpan.FromSeconds(5),
    };

    private static ApiClient ClientReturning(
        Func<HttpRequestMessage, HttpResponseMessage> responder, out StubHttpMessageHandler handler)
    {
        handler = new StubHttpMessageHandler(responder);
        return new ApiClient(new HttpClient(handler), TestConfig);
    }

    [Fact]
    public async Task Login_PostsToAuthEndpoint_AndDeserializes()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            {"token":"jwt-123","expiresAt":"2030-01-01T00:00:00Z",
             "user":{"id":1,"username":"alice","displayName":"Alice","role":"STUDENT"}}
            """);
        }, out _);

        var response = await api.LoginAsync(new LoginRequest("alice", "secret"));

        Assert.Equal("jwt-123", response.Token);
        Assert.Equal("alice", response.User.Username);
        Assert.Equal(HttpMethod.Post, captured!.Method);
        Assert.Equal("http://test.local/api/auth/login", captured.RequestUri!.ToString());
    }

    [Fact]
    public async Task AuthToken_IsAttachedAsBearer()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, "[]");
        }, out _);
        api.AuthToken = "tok-42";

        await api.GetTodayQuestsAsync();

        Assert.Equal("Bearer", captured!.Headers.Authorization!.Scheme);
        Assert.Equal("tok-42", captured.Headers.Authorization.Parameter);
    }

    [Fact]
    public async Task NoToken_OmitsAuthorizationHeader()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, "[]");
        }, out _);

        await api.GetTodayQuestsAsync();

        Assert.Null(captured!.Headers.Authorization);
    }

    [Fact]
    public async Task NonSuccess_ThrowsApiException_FromEnvelope()
    {
        var api = ClientReturning(
            _ => StubHttpMessageHandler.Json(HttpStatusCode.Unauthorized,
                """{"code":"UNAUTHORIZED","message":"令牌失效"}"""),
            out _);

        var ex = await Assert.ThrowsAsync<ApiException>(() => api.GetMasteryAsync());

        Assert.True(ex.IsUnauthorized);
        Assert.Equal("令牌失效", ex.Message);
    }

    [Fact]
    public async Task GetProfile_NoContent_ReturnsNull()
    {
        var api = ClientReturning(_ => new HttpResponseMessage(HttpStatusCode.NoContent), out _);

        Assert.Null(await api.GetProfileAsync());
    }

    [Fact]
    public async Task NetworkFailure_ThrowsApiException_NetworkError()
    {
        var api = ClientReturning(_ => throw new HttpRequestException("dns failure"), out _);

        var ex = await Assert.ThrowsAsync<ApiException>(() => api.GetTodayQuestsAsync());

        Assert.Equal("NETWORK_ERROR", ex.Code);
        Assert.Equal(HttpStatusCode.ServiceUnavailable, ex.StatusCode);
    }

    [Fact]
    public async Task GetQuests_BuildsQueryString_SkippingNulls()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, "[]");
        }, out _);

        await api.GetQuestsAsync(status: "ACTIVE", limit: 5);

        Assert.Equal("http://test.local/api/quests?status=ACTIVE&limit=5", captured!.RequestUri!.ToString());
    }

    [Fact]
    public async Task GetMainlineOverview_DeserializesChaptersAndStages()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            {"totalStars":5,"chapters":[
              {"chapter":1,"title":"入门","unlocked":true,"stars":3,"bestTestScore":88,
               "stages":[{"id":10,"index":0,"title":"变量","videoWatchedSec":60,"requiredVideoSec":120,
                          "pagesRead":2,"requiredPages":3,"examplesDone":1,"requiredExamples":2,"completed":false}],
               "testStatus":"PASSED","starBreakdown":{"video":1},
               "recovery":{"errorbookDone":true,"endlessDone":false}}
            ]}
            """);
        }, out _);

        var overview = await api.GetMainlineOverviewAsync();

        Assert.Equal("http://test.local/api/mainline/overview", captured!.RequestUri!.ToString());
        Assert.Equal(5, overview.TotalStars);
        var chapter = Assert.Single(overview.Chapters);
        Assert.Equal("入门", chapter.Title);
        Assert.True(chapter.Unlocked);
        Assert.Equal(88, chapter.BestTestScore);
        var stage = Assert.Single(chapter.Stages);
        Assert.Equal("变量", stage.Title);
        Assert.Equal(120, stage.RequiredVideoSec);
        Assert.False(stage.Completed);
        Assert.NotNull(chapter.Recovery);
        Assert.True(chapter.Recovery!.ErrorbookDone);
    }

    [Fact]
    public async Task GetWeeklyReport_DeserializesBriefAndLists()
    {
        var api = ClientReturning(_ => StubHttpMessageHandler.Json(HttpStatusCode.OK, """
        {"periodStart":"2026-06-01","periodEnd":"2026-06-07",
         "memoryBrief":{"totalEvents":12,"failedPractices":3,"succeededPractices":7,
                        "topKcCodes":["PY43-KC-001"],"highlights":["完成 for 循环"]},
         "insights":[{"id":1,"text":"循环边界易错","dimensionKey":"error_pattern","tags":["loop"],"createdAt":"2026-06-05"}],
         "masteryDeltas":[{"kcCode":"PY43-KC-001","currentScore":0.8,"confidence":0.6,"recentEventReasons":["AC"]}],
         "resourceRuns":[{"agentName":"ResourceDesignerAgent","scenario":"handout","status":"SUCCEEDED","evidencePageCount":3,"createdAt":"2026-06-04"}]}
        """), out _);

        var report = await api.GetWeeklyReportAsync();

        Assert.Equal("2026-06-01", report.PeriodStart);
        Assert.Equal(12, report.MemoryBrief!.TotalEvents);
        Assert.Single(report.Insights);
        Assert.Equal("PY43-KC-001", Assert.Single(report.MasteryDeltas).KcCode);
        Assert.Equal("ResourceDesignerAgent", Assert.Single(report.ResourceRuns).AgentName);
    }

    [Fact]
    public async Task GetRiskPredictions_DeserializesList()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            [{"kcCode":"PY43-KC-016","risk":0.72,"evidenceEventIds":[101,102],
              "reason":"前置薄弱","dependencyPath":["PY43-KC-001","PY43-KC-016"]}]
            """);
        }, out _);

        var risks = await api.GetRiskPredictionsAsync();

        Assert.Equal("http://test.local/api/learner/oracle/risk", captured!.RequestUri!.ToString());
        var risk = Assert.Single(risks);
        Assert.Equal("PY43-KC-016", risk.KcCode);
        Assert.Equal(0.72, risk.Risk, 3);
        Assert.Equal(2, risk.DependencyPath!.Count);
    }

    [Fact]
    public async Task GetLatestChapterTest_BuildsPathAndDeserializes()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            {"testId":7,"chapter":2,"attemptNo":1,"status":"SUBMITTED","score":85,"maxScore":100,
             "passed":true,"stars":2,"overtime":false,"submittedAt":"2026-06-06T10:00:00Z",
             "items":[{"qid":"q1","type":"CHOICE","correct":true,"awardedScore":10,"maxScore":10,
                       "yourAnswer":"A","correctAnswer":"A","explain":"","passedCases":null,"totalCases":null}],
             "integrity":{"pasteBlocked":0,"blurEvents":1,"overtime":false}}
            """);
        }, out _);

        var result = await api.GetLatestChapterTestAsync(2);

        Assert.Equal("http://test.local/api/mainline/chapters/2/test/latest", captured!.RequestUri!.ToString());
        Assert.Equal(85, result.Score);
        Assert.True(result.Passed);
        Assert.Equal("CHOICE", Assert.Single(result.Items).Type);
        Assert.Equal(1, result.Integrity!.BlurEvents);
    }

    [Fact]
    public async Task GetStageDetail_DeserializesNestedLists()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            {"id":10,"chapter":1,"chapterTitle":"入门","index":0,"title":"变量",
             "kcCodes":["PY43-KC-001"],"videoKeyword":"变量","coursewarePageIds":[1,2],"exampleProblemIds":[5],
             "requiredVideoSec":120,"requiredPages":3,"requiredExamples":2,"requiredQuestions":2,
             "videos":[{"id":1,"title":"认识变量","durationSec":90}],
             "examples":[{"id":5,"displayNo":"例1","displayName":"赋值","kcCode":"PY43-KC-001"}],
             "questions":[{"id":7,"kcCode":"PY43-KC-001","type":"CHOICE","ordinal":1,"stem":"变量?","options":["A","B"],"blankCount":0}],
             "videoWatchedSec":30,"pagesReadIds":[1],"examplesDoneIds":[],"questionsCorrectIds":[],"completed":false}
            """);
        }, out _);

        var detail = await api.GetStageDetailAsync(10);

        Assert.Equal("http://test.local/api/mainline/stages/10", captured!.RequestUri!.ToString());
        Assert.Equal("变量", detail.Title);
        Assert.Equal("PY43-KC-001", Assert.Single(detail.KcCodes!));
        Assert.Equal("认识变量", Assert.Single(detail.Videos).Title);
        Assert.Equal("CHOICE", Assert.Single(detail.Questions).Type);
        Assert.False(detail.Completed);
    }

    [Fact]
    public async Task GetExamples_BuildsKcQuery_AndDeserializes()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            [{"id":5,"packId":43,"kcCode":"PY43-KC-001","title":"赋值示例","code":"x=1","explanationMarkdown":"赋值","pageRefs":[2,3]}]
            """);
        }, out _);

        var examples = await api.GetExamplesAsync(kcCode: "PY43-KC-001");

        Assert.Equal("http://test.local/api/course/examples?kcCode=PY43-KC-001", captured!.RequestUri!.ToString());
        var example = Assert.Single(examples);
        Assert.Equal("赋值示例", example.Title);
        Assert.Equal("x=1", example.Code);
    }

    [Fact]
    public async Task GetCalendarToday_Deserializes()
    {
        var api = ClientReturning(_ => StubHttpMessageHandler.Json(HttpStatusCode.OK, """
        {"dayIndex":3,"monthIndex":1,"weekday":"周三","season":"春","timeOfDay":"上午","weather":"晴","inGameMinutes":540}
        """), out _);

        var today = await api.GetCalendarTodayAsync();

        Assert.Equal(3, today.DayIndex);
        Assert.Equal("春", today.Season);
        Assert.Equal("晴", today.Weather);
        Assert.Equal(540, today.InGameMinutes);
    }

    [Fact]
    public async Task GetInsights_BuildsLimitQuery_AndDeserializes()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            [{"id":1,"text":"循环边界易错","dimensionKey":"error_pattern","tags":["loop"],
              "evidenceEventIds":[10,11],"confidence":0.7,"source":"reflection","createdAt":"2026-06-05"}]
            """);
        }, out _);

        var insights = await api.GetInsightsAsync(limit: 5);

        Assert.Equal("http://test.local/api/learner/insights?limit=5", captured!.RequestUri!.ToString());
        var insight = Assert.Single(insights);
        Assert.Equal("循环边界易错", insight.Text);
        Assert.Equal(2, insight.EvidenceEventIds!.Count);
    }

    [Fact]
    public async Task GetMemory_BuildsQuery_AndDeserializes()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            [{"eventId":42,"eventType":"PRACTICE_FAIL","summaryZh":"for 循环失败","kcCodes":["PY43-KC-016"],
              "createdAt":"2026-06-06","importance":3,"recencyScore":0.9,"importanceScore":0.6,
              "relevanceScore":0.5,"totalScore":0.7}]
            """);
        }, out _);

        var memory = await api.GetMemoryAsync(focusKc: "PY43-KC-016", limit: 8);

        Assert.Equal("http://test.local/api/learner/memory?focusKc=PY43-KC-016&limit=8", captured!.RequestUri!.ToString());
        var item = Assert.Single(memory);
        Assert.Equal("for 循环失败", item.SummaryZh);
        Assert.Equal(0.7, item.TotalScore, 3);
    }

    [Fact]
    public async Task SearchBilibili_BuildsKeywordQuery_AndDeserializes()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            [{"bvid":"BV1xx","title":"Python for 循环","author":"UP主","description":"讲解",
              "aiSummary":"摘要","coverUrl":"http://c","durationSec":600,"play":1000,"danmaku":20,
              "pubdate":1700000000,"url":"http://b/BV1xx"}]
            """);
        }, out _);

        var videos = await api.SearchBilibiliVideosAsync("for 循环", limit: 8);

        Assert.StartsWith("http://test.local/api/videos/bilibili?keyword=", captured!.RequestUri!.ToString());
        var video = Assert.Single(videos);
        Assert.Equal("BV1xx", video.Bvid);
        Assert.Equal("Python for 循环", video.Title);
        Assert.Equal(600, video.DurationSec);
    }

    [Fact]
    public async Task GetSaveSlots_Deserializes()
    {
        var api = ClientReturning(_ => StubHttpMessageHandler.Json(HttpStatusCode.OK, """
        [{"slotIndex":0,"displayName":"存档1","isActive":true,"createdAt":"2026-06-01T00:00:00Z",
          "lastPlayedAt":"2026-06-06T00:00:00Z","snapshot":null,"sceneName":"town_hub",
          "unlockedBuildings":["workshop"],"accumulatedPlaySeconds":3600,"inventoryItems":null}]
        """), out _);

        var saves = await api.GetSaveSlotsAsync();

        var slot = Assert.Single(saves);
        Assert.Equal(0, slot.SlotIndex);
        Assert.True(slot.IsActive);
        Assert.Equal("town_hub", slot.SceneName);
        Assert.Equal(3600, slot.AccumulatedPlaySeconds);
    }

    [Fact]
    public async Task ReportVideoProgress_PostsDeltaSec_AndDeserializesProgress()
    {
        HttpRequestMessage? captured = null;
        string? body = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            body = req.Content?.ReadAsStringAsync().GetAwaiter().GetResult();
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            {"stageId":10,"chapter":1,"stageIndex":0,"videoWatchedSec":90,"requiredVideoSec":120,"videoComplete":false,
             "pagesRead":1,"requiredPages":3,"pagesComplete":false,"examplesDone":0,"requiredExamples":2,"examplesComplete":false,"completed":false}
            """);
        }, out _);

        var progress = await api.ReportVideoProgressAsync(10, 60);

        Assert.Equal(HttpMethod.Post, captured!.Method);
        Assert.Equal("http://test.local/api/mainline/stages/10/video-progress", captured.RequestUri!.ToString());
        Assert.Contains("\"deltaSec\":60", body);
        Assert.Equal(90, progress.VideoWatchedSec);
        Assert.False(progress.Completed);
    }

    [Fact]
    public async Task ReportPagesRead_PostsPageIds_AndDeserializesProgress()
    {
        HttpRequestMessage? captured = null;
        string? body = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            body = req.Content?.ReadAsStringAsync().GetAwaiter().GetResult();
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            {"stageId":10,"chapter":1,"stageIndex":0,"videoWatchedSec":0,"requiredVideoSec":120,"videoComplete":false,
             "pagesRead":3,"requiredPages":3,"pagesComplete":true,"examplesDone":0,"requiredExamples":2,"examplesComplete":false,"completed":false}
            """);
        }, out _);

        var progress = await api.ReportPagesReadAsync(10, new long[] { 1, 2, 3 });

        Assert.Equal("http://test.local/api/mainline/stages/10/pages-read", captured!.RequestUri!.ToString());
        Assert.Contains("\"pageIds\":[1,2,3]", body);
        Assert.True(progress.PagesComplete);
    }

    [Fact]
    public async Task StartFeynman_PostsKcCode_AndDeserializes()
    {
        HttpRequestMessage? captured = null;
        string? body = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            body = req.Content?.ReadAsStringAsync().GetAwaiter().GetResult();
            return StubHttpMessageHandler.Json(HttpStatusCode.OK,
                """{"id":3,"userId":7,"kcCode":"PY43-KC-001","status":"ACTIVE","tuteeState":{"mood":"curious"}}""");
        }, out _);

        var session = await api.StartFeynmanAsync("PY43-KC-001");

        Assert.Equal("http://test.local/api/feynman/sessions", captured!.RequestUri!.ToString());
        Assert.Contains("\"kcCode\":\"PY43-KC-001\"", body);
        Assert.Equal(3, session.Id);
        Assert.Equal("ACTIVE", session.Status);
    }

    [Fact]
    public async Task FeynmanTeach_PostsExplanation_AndDeserializesTurn()
    {
        HttpRequestMessage? captured = null;
        string? body = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            body = req.Content?.ReadAsStringAsync().GetAwaiter().GetResult();
            return StubHttpMessageHandler.Json(HttpStatusCode.OK,
                """{"id":11,"role":"tutee","content":"我有点懂了","taughtFix":"注意缩进"}""");
        }, out _);

        var turn = await api.FeynmanTeachAsync(3, "for 循环会重复执行");

        Assert.Equal("http://test.local/api/feynman/sessions/3/teach", captured!.RequestUri!.ToString());
        Assert.Contains("\"explanation\":", body);
        Assert.Equal("tutee", turn.Role);
        Assert.Equal("注意缩进", turn.TaughtFix);
    }

    [Fact]
    public async Task FeynmanJudge_PostsNoBody_AndDeserializesVerdict()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK,
                """{"sessionId":3,"passed":true,"message":"讲清楚了"}""");
        }, out _);

        var verdict = await api.FeynmanJudgeAsync(3);

        Assert.Equal(HttpMethod.Post, captured!.Method);
        Assert.Equal("http://test.local/api/feynman/sessions/3/judge", captured.RequestUri!.ToString());
        Assert.True(verdict.Passed);
        Assert.Equal("讲清楚了", verdict.Message);
    }

    [Fact]
    public async Task RequestBossProblem_PostsStage_AndDeserializes()
    {
        HttpRequestMessage? captured = null;
        string? body = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            body = req.Content?.ReadAsStringAsync().GetAwaiter().GetResult();
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            {"problemId":"AI-1","chapter":2,"stage":1,"difficulty":3,"kcCode":"PY43-KC-016","statement":"求和",
             "hints":["用循环"],"sampleInput":"3","sampleOutput":"6","totalTestCases":5,"reviewed":true,"aiGenerated":true}
            """);
        }, out _);

        var problem = await api.RequestBossProblemAsync(2, 1);

        Assert.Equal("http://test.local/api/world/boss/2/problem", captured!.RequestUri!.ToString());
        Assert.Contains("\"stage\":1", body);
        Assert.Equal("AI-1", problem.ProblemId);
        Assert.Equal(5, problem.TotalTestCases);
    }

    [Fact]
    public async Task SubmitBossAttempt_PostsCode_AndDeserializesResult()
    {
        HttpRequestMessage? captured = null;
        string? body = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            body = req.Content?.ReadAsStringAsync().GetAwaiter().GetResult();
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            {"problemId":"AI-1","passed":true,"passedTestCases":5,"totalTestCases":5,
             "failureFirstHint":null,"score":100,"feedback":"漂亮"}
            """);
        }, out _);

        var result = await api.SubmitBossAttemptAsync(2, 1, "AI-1", "print(sum(range(4)))", 42);

        Assert.Equal("http://test.local/api/world/boss/2/attempt", captured!.RequestUri!.ToString());
        Assert.Contains("\"problemId\":\"AI-1\"", body);
        Assert.Contains("\"elapsedSec\":42", body);
        Assert.True(result.Passed);
        Assert.Equal(100, result.Score);
    }

    [Fact]
    public async Task PrepareChapterTest_DeserializesStatus()
    {
        var api = ClientReturning(_ => StubHttpMessageHandler.Json(HttpStatusCode.OK, """{"status":"READY"}"""), out _);

        var prep = await api.PrepareChapterTestAsync(1);

        Assert.Equal("READY", prep.Status);
    }

    [Fact]
    public async Task StartChapterTest_DeserializesPaper()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            {"testId":55,"chapter":1,"attemptNo":1,"status":"IN_PROGRESS","timeLimitSec":1800,
             "deadlineAt":"2026-06-13T12:00:00Z",
             "items":[{"qid":"q1","type":"CHOICE","score":10,"stem":"选对的","title":"",
                       "statementMarkdown":"1+1=?","options":["1","2"],"samples":[]}]}
            """);
        }, out _);

        var paper = await api.StartChapterTestAsync(1);

        Assert.Equal(HttpMethod.Post, captured!.Method);
        Assert.Equal("http://test.local/api/mainline/chapters/1/test/start", captured.RequestUri!.ToString());
        Assert.Equal(55, paper.TestId);
        Assert.Equal("q1", Assert.Single(paper.Items).Qid);
    }

    [Fact]
    public async Task SubmitChapterTest_PostsAnswersMap_AndDeserializesResult()
    {
        HttpRequestMessage? captured = null;
        string? body = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            body = req.Content?.ReadAsStringAsync().GetAwaiter().GetResult();
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            {"testId":55,"chapter":1,"attemptNo":1,"status":"SUBMITTED","score":10,"maxScore":10,
             "passed":true,"stars":1,"overtime":false,"submittedAt":"2026-06-13T11:00:00Z",
             "items":[],"integrity":{"pasteBlocked":0,"blurEvents":0,"overtime":false}}
            """);
        }, out _);

        var result = await api.SubmitChapterTestAsync(1, 55, new Dictionary<string, string> { ["q1"] = "2" });

        Assert.Equal("http://test.local/api/mainline/chapters/1/test/55/submit", captured!.RequestUri!.ToString());
        Assert.Contains("\"answers\":", body);
        Assert.Contains("\"q1\":\"2\"", body);
        Assert.True(result.Passed);
    }

    [Fact]
    public async Task SubmitEndlessSummary_PostsResult_AndDeserializes()
    {
        HttpRequestMessage? captured = null;
        string? body = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            body = req.Content?.ReadAsStringAsync().GetAwaiter().GetResult();
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """{"starAwarded":true}""");
        }, out _);

        var result = await api.SubmitEndlessSummaryAsync(2, 8, 10, 5);

        Assert.Equal("http://test.local/api/mainline/endless/summary", captured!.RequestUri!.ToString());
        Assert.Contains("\"chapter\":2", body);
        Assert.Contains("\"correct\":8", body);
        Assert.True(result.StarAwarded);
    }

    [Fact]
    public async Task GetSkills_DeserializesNodesAndUnlocked()
    {
        var api = ClientReturning(_ => StubHttpMessageHandler.Json(HttpStatusCode.OK, """
        {"nodes":[{"skillKey":"loop_master","displayName":"循环大师","description":"精通循环",
                   "kcCodes":["PY43-KC-016"],"unlockCondition":{"type":"KC_MASTERY","kcCode":"PY43-KC-016","min":0.6},
                   "iconUrl":null,"tier":1}],
         "unlocked":[{"skillKey":"loop_master","unlockedAt":"2026-06-06T00:00:00Z","proficiency":2}]}
        """), out _);

        var skills = await api.GetSkillsAsync();

        Assert.Equal("loop_master", Assert.Single(skills.Nodes).SkillKey);
        Assert.Equal(2, Assert.Single(skills.Unlocked).Proficiency);
    }

    [Fact]
    public async Task UnlockSkill_PostsToUnlock_AndDeserializes()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK,
                """{"skillKey":"loop_master","unlockedAt":"2026-06-06T00:00:00Z","proficiency":0}""");
        }, out _);

        var skill = await api.UnlockSkillAsync("loop_master");

        Assert.Equal(HttpMethod.Post, captured!.Method);
        Assert.Equal("http://test.local/api/world/skills/loop_master/unlock", captured.RequestUri!.ToString());
        Assert.Equal("loop_master", skill.SkillKey);
    }

    [Fact]
    public async Task GetNpcGreeting_BuildsPath_AndDeserializes()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK,
                """{"greeting":"欢迎来到小镇","available":true,"grounded":true,"citedEventIds":[1],"degraded":false,"cached":false}""");
        }, out _);

        var greeting = await api.GetNpcGreetingAsync("mayor_py");

        Assert.Equal("http://test.local/api/npc/mayor_py/greeting", captured!.RequestUri!.ToString());
        Assert.Equal("欢迎来到小镇", greeting.Greeting);
        Assert.True(greeting.Available);
    }

    [Fact]
    public async Task TalkToNpc_PostsMessage_AndDeserializes()
    {
        HttpRequestMessage? captured = null;
        string? body = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            body = req.Content?.ReadAsStringAsync().GetAwaiter().GetResult();
            return StubHttpMessageHandler.Json(HttpStatusCode.OK,
                """{"replyMd":"好问题！","citedEventIds":[],"grounded":true}""");
        }, out _);

        var reply = await api.TalkToNpcAsync("mayor_py", "for 循环怎么用");

        Assert.Equal("http://test.local/api/npc/mayor_py/dialog", captured!.RequestUri!.ToString());
        Assert.Contains("\"message\":", body);
        Assert.Equal("好问题！", reply.ReplyMd);
    }

    [Fact]
    public async Task GetCourseDocumentPdf_ReturnsBytes()
    {
        var pdfBytes = new byte[] { 0x25, 0x50, 0x44, 0x46 };
        var api = ClientReturning(
            _ => new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(pdfBytes) }, out _);

        var bytes = await api.GetCourseDocumentPdfAsync(186);

        Assert.Equal(pdfBytes, bytes);
    }

    [Fact]
    public async Task GetActiveLearningPath_DeserializesNodes()
    {
        var api = ClientReturning(_ => StubHttpMessageHandler.Json(HttpStatusCode.OK, """
        {"id":1,"userId":7,"packId":43,"version":2,"status":"ACTIVE","goalSummary":"7天掌握循环",
         "generatedAt":"2026-06-06T00:00:00Z","nextReviewAt":"2026-06-08T00:00:00Z","sourceRunId":99,
         "nodes":[{"id":10,"pathId":1,"dayIndex":0,"orderInDay":0,"kcCode":"PY43-KC-016","nodeType":"LEARN",
                   "reasonSummary":"薄弱","evidenceSummary":"两次失败","suggestedResourceType":"HANDOUT",
                   "suggestedProblemId":null,"sourceClassroomQuestId":null}]}
        """), out _);

        var path = await api.GetActiveLearningPathAsync();

        Assert.Equal("ACTIVE", path.Status);
        Assert.Equal("7天掌握循环", path.GoalSummary);
        var node = Assert.Single(path.Nodes);
        Assert.Equal("PY43-KC-016", node.KcCode);
        Assert.Equal("LEARN", node.NodeType);
    }

    [Fact]
    public async Task PlanLearningPath_PostsAndDeserializes()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            {"path":{"id":2,"userId":7,"packId":43,"version":3,"status":"ACTIVE","goalSummary":"新计划",
                     "generatedAt":"2026-06-07T00:00:00Z","nextReviewAt":null,"sourceRunId":100,"nodes":[]},
             "archivedPathIds":[1],"generatedQuestCount":5,"runStatus":"SUCCEEDED","groundingMessage":"ok"}
            """);
        }, out _);

        var response = await api.PlanLearningPathAsync();

        Assert.Equal(HttpMethod.Post, captured!.Method);
        Assert.Equal("http://test.local/api/learner/path/plan", captured.RequestUri!.ToString());
        Assert.Equal(5, response.GeneratedQuestCount);
        Assert.Equal("新计划", response.Path!.GoalSummary);
    }

    [Fact]
    public async Task GenerateImage_PostsPrompt_AndDeserializesAsset()
    {
        HttpRequestMessage? captured = null;
        string? body = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            body = req.Content?.ReadAsStringAsync().GetAwaiter().GetResult();
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            {"id":7,"kind":"IMAGE","url":"/api/media/assets/7","mimeType":"image/png",
             "degraded":true,"source":"local","prompt":"小镇","kcCodes":[]}
            """);
        }, out _);

        var asset = await api.GenerateImageAsync("像素小镇");

        Assert.Equal("http://test.local/api/media/image", captured!.RequestUri!.ToString());
        Assert.Contains("\"prompt\":", body);
        Assert.Equal(7, asset.Id);
        Assert.True(asset.Degraded);
        Assert.Equal("/api/media/assets/7", asset.Url);
    }

    [Fact]
    public void GetMediaAssetUri_BuildsAbsoluteUrl()
    {
        var api = ClientReturning(_ => StubHttpMessageHandler.Json(HttpStatusCode.OK, "{}"), out _);

        Assert.Equal("http://test.local/api/media/assets/7", api.GetMediaAssetUri("/api/media/assets/7").ToString());
    }

    [Fact]
    public async Task SubmitAndPoll_PollsUntilTerminalStatus()
    {
        var statuses = new Queue<string>(new[] { "QUEUED", "RUNNING", "PASSED" });
        var api = ClientReturning(req =>
        {
            if (req.Method == HttpMethod.Post)
            {
                return StubHttpMessageHandler.Json(HttpStatusCode.OK, """{"id":99,"status":"QUEUED"}""");
            }

            var status = statuses.Dequeue();
            return StubHttpMessageHandler.Json(HttpStatusCode.OK,
                "{\"submission\":{\"id\":99,\"status\":\"" + status + "\"}}");
        }, out var handler);

        var detail = await api.SubmitAndPollAsync(problemId: 1, code: "print(1)");

        Assert.Equal("PASSED", detail.Submission.Status);
        // 1 POST create + 3 GET polls.
        Assert.Equal(4, handler.CallCount);
    }
}
