using System.Net;
using Pythontopia.Core.Api;
using Pythontopia.Core.Config;
using Pythontopia.Core.Dto;
using Xunit;

namespace Pythontopia.Core.Tests;

public class InterviewApiClientTests
{
    private static AppConfig TestConfig => new()
    {
        BackendBaseUrl = "http://test.local",
        RequestTimeout = TimeSpan.FromSeconds(5),
    };

    private static ApiClient ClientReturning(Func<HttpRequestMessage, HttpResponseMessage> responder) =>
        new(new HttpClient(new StubHttpMessageHandler(responder)), TestConfig);

    [Fact]
    public async Task OpenProfileInterview_PostsAndParsesHistory()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            {"interviewSessionId":"sess-1","status":"OPEN",
             "history":[{"role":"assistant","content":"先聊聊你的专业背景吧"}],"draft":null}
            """);
        });

        var res = await api.OpenProfileInterviewAsync();

        Assert.Equal("sess-1", res.InterviewSessionId);
        Assert.Equal("OPEN", res.Status);
        Assert.Equal("assistant", Assert.Single(res.History!).Role);
        Assert.Equal(HttpMethod.Post, captured!.Method);
        Assert.Equal("http://test.local/api/learner/profile/interview", captured.RequestUri!.ToString());
    }

    [Fact]
    public async Task SendProfileTurn_PostsToTurnEndpoint_ParsesNextQuestion()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            {"nextQuestion":"你平时怎么调试代码？","profileDraft":null,"done":false,
             "dimensionsFilled":["practical_ability"],"evidenceAnchors":[],
             "safetyResult":{"passed":true,"reasons":[]},"groundingResult":{"passed":true,"reasons":[]}}
            """);
        });

        var res = await api.SendProfileTurnAsync("sess-1", new ProfileTurnRequest("我是计科大一", null));

        Assert.Equal("你平时怎么调试代码？", res.NextQuestion);
        Assert.False(res.Done);
        Assert.Equal("practical_ability", Assert.Single(res.DimensionsFilled!));
        Assert.Equal("http://test.local/api/learner/profile/interview/sess-1/turn", captured!.RequestUri!.ToString());
    }

    [Fact]
    public async Task FinishProfileInterview_PostsFinish()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return new HttpResponseMessage(HttpStatusCode.NoContent);
        });

        await api.FinishProfileInterviewAsync("sess-1");

        Assert.Equal(HttpMethod.Post, captured!.Method);
        Assert.Equal("http://test.local/api/learner/profile/interview/sess-1/finish", captured.RequestUri!.ToString());
    }
}
