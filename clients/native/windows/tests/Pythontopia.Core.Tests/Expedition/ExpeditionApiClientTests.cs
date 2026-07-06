using System.Net;
using System.Text.Json;
using Pythontopia.Core.Api;
using Pythontopia.Core.Config;
using Pythontopia.Core.Dto;
using Pythontopia.Core.Expedition;
using Pythontopia.Core.Json;
using Xunit;

namespace Pythontopia.Core.Tests.Expedition;

public class ExpeditionApiClientTests
{
    private static AppConfig TestConfig => new()
    {
        BackendBaseUrl = "http://test.local",
        RequestTimeout = TimeSpan.FromSeconds(5),
    };

    private static ApiClient ClientReturning(Func<HttpRequestMessage, HttpResponseMessage> responder) =>
        new(new HttpClient(new StubHttpMessageHandler(responder)), TestConfig);

    [Fact]
    public async Task SelectFrontier_PostsToEndpoint_ReturnsPicks()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            {"picks":[{"kcCode":"PY43-KC-010","chapter":2,"difficulty":3,"reason":"zpd"}]}
            """);
        });

        var picks = await api.SelectFrontierAsync(new FrontierSelectRequest(2, Array.Empty<string>(), Array.Empty<string>(), 28));

        var pick = Assert.Single(picks);
        Assert.Equal("PY43-KC-010", pick.KcCode);
        Assert.Equal(3, pick.Difficulty);
        Assert.Equal(HttpMethod.Post, captured!.Method);
        Assert.Equal("http://test.local/api/world/boss/select-frontier", captured.RequestUri!.ToString());
    }

    [Fact]
    public async Task SelectFrontier_NullPicks_ReturnsEmpty()
    {
        var api = ClientReturning(_ => StubHttpMessageHandler.Json(HttpStatusCode.OK, "{}"));
        var picks = await api.SelectFrontierAsync(new FrontierSelectRequest(null, Array.Empty<string>(), Array.Empty<string>(), 1));
        Assert.Empty(picks);
    }

    [Fact]
    public async Task GetExpeditionRun_NoContent_ReturnsNull()
    {
        var api = ClientReturning(_ => new HttpResponseMessage(HttpStatusCode.NoContent));
        Assert.Null(await api.GetExpeditionRunAsync());
    }

    [Fact]
    public async Task GetExpeditionRun_EmptyOrNullBody_ReturnsNull()
    {
        var empty = ClientReturning(_ => StubHttpMessageHandler.Json(HttpStatusCode.OK, ""));
        Assert.Null(await empty.GetExpeditionRunAsync());

        var literalNull = ClientReturning(_ => StubHttpMessageHandler.Json(HttpStatusCode.OK, "null"));
        Assert.Null(await literalNull.GetExpeditionRunAsync());
    }

    [Fact]
    public async Task GetExpeditionRun_Snapshot_IsParsed()
    {
        var api = ClientReturning(_ => StubHttpMessageHandler.Json(HttpStatusCode.OK, """
        {"phase":"map","focusChapter":1,"layers":2,"hp":5,"maxHp":5,"momentum":0,
         "bestMomentum":0,"score":0,"isDaily":false,"relics":[],"history":[],
         "nodes":[{"id":"L0-0","layer":0,"col":0,"kind":"battle","kcCode":"PY43-KC-001",
                   "challengeKind":"dual_track","difficulty":2,"next":["L1-0"],"state":"available"}]}
        """));

        var snap = await api.GetExpeditionRunAsync();

        Assert.NotNull(snap);
        Assert.Equal("map", snap!.Phase);
        Assert.Equal(1, snap.FocusChapter);
        var node = Assert.Single(snap.Nodes);
        Assert.Equal("L0-0", node.Id);
        Assert.Equal(RogueNodeKind.Battle, node.Kind);
        Assert.Equal(RogueNodeState.Available, node.State);
    }

    [Fact]
    public async Task SaveExpeditionRun_PutsToEndpoint()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, "{}");
        });

        await api.SaveExpeditionRunAsync(new ExpeditionRunSnapshot { Phase = RogueRunPhase.Map });

        Assert.Equal(HttpMethod.Put, captured!.Method);
        Assert.Equal("http://test.local/api/world/expedition/run", captured.RequestUri!.ToString());
    }

    [Fact]
    public async Task DeleteExpeditionRun_DeletesEndpoint()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return new HttpResponseMessage(HttpStatusCode.NoContent);
        });

        await api.DeleteExpeditionRunAsync();

        Assert.Equal(HttpMethod.Delete, captured!.Method);
        Assert.Equal("http://test.local/api/world/expedition/run", captured.RequestUri!.ToString());
    }

    [Fact]
    public void Snapshot_JsonRoundTrips_WithCamelCaseAndLowercaseEnums()
    {
        var snapshot = new ExpeditionRunSnapshot
        {
            Phase = RogueRunPhase.Map,
            FocusChapter = 3,
            Layers = 2,
            Hp = 4,
            MaxHp = 5,
            Relics = new List<string> { RogueRelics.CalmMind },
            Nodes = new List<RogueNode>
            {
                new()
                {
                    Id = "L0-0", Layer = 0, Col = 0, Kind = RogueNodeKind.Boss,
                    KcCode = "PY43-KC-065", ChallengeKind = "multi_round", Difficulty = 4,
                    Next = new List<string> { "L1-0" }, State = RogueNodeState.Available,
                },
            },
        };

        var json = JsonSerializer.Serialize(snapshot, PytJson.Default);
        Assert.Contains("\"phase\":\"map\"", json);
        Assert.Contains("\"kind\":\"boss\"", json);
        Assert.Contains("\"focusChapter\":3", json);

        var restored = JsonSerializer.Deserialize<ExpeditionRunSnapshot>(json, PytJson.Default);
        Assert.NotNull(restored);
        Assert.Equal(RogueRunPhase.Map, restored!.Phase);
        Assert.Equal(3, restored.FocusChapter);
        var node = Assert.Single(restored.Nodes);
        Assert.Equal(RogueNodeKind.Boss, node.Kind);
        Assert.Equal(RogueNodeState.Available, node.State);
        Assert.Equal("PY43-KC-065", node.KcCode);
    }
}
