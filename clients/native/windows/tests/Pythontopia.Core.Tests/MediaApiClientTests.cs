using System.Net;
using Pythontopia.Core.Api;
using Pythontopia.Core.Config;
using Pythontopia.Core.Dto;
using Xunit;

namespace Pythontopia.Core.Tests;

public class MediaApiClientTests
{
    private static AppConfig TestConfig => new()
    {
        BackendBaseUrl = "http://test.local",
        RequestTimeout = TimeSpan.FromSeconds(5),
    };

    private static ApiClient ClientReturning(Func<HttpRequestMessage, HttpResponseMessage> responder) =>
        new(new HttpClient(new StubHttpMessageHandler(responder)), TestConfig);

    [Fact]
    public async Task ComposeStoryboardVideo_PostsToEndpoint_ReturnsAsset()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """
            {"id":7,"kind":"VIDEO","url":"/api/media/assets/7","mimeType":"video/mp4",
             "degraded":false,"source":"ffmpeg","prompt":null,"kcCodes":[]}
            """);
        });

        var scenes = new[]
        {
            new StoryboardSceneSpec("像素风：变量是盒子", "变量就像贴了标签的盒子"),
        };
        var asset = await api.ComposeStoryboardVideoAsync(scenes);

        Assert.Equal(7, asset.Id);
        Assert.Equal("VIDEO", asset.Kind);
        Assert.Equal("/api/media/assets/7", asset.Url);
        Assert.False(asset.Degraded);
        Assert.Equal(HttpMethod.Post, captured!.Method);
        Assert.Equal("http://test.local/api/media/storyboard/video", captured.RequestUri!.ToString());
    }

    [Fact]
    public async Task ComposeStoryboardVideo_Degraded_IsSurfaced()
    {
        var api = ClientReturning(_ => StubHttpMessageHandler.Json(HttpStatusCode.OK, """
        {"id":1,"kind":"VIDEO","url":"/api/media/assets/1","mimeType":"video/mp4",
         "degraded":true,"source":"placeholder","prompt":null,"kcCodes":[]}
        """));

        var asset = await api.ComposeStoryboardVideoAsync(new[] { new StoryboardSceneSpec("p", "n") });

        Assert.True(asset.Degraded);
    }

    [Fact]
    public async Task CreateStoryboardVideoJob_PostsToJobsEndpoint_ParsesCached()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.Accepted, """
            {"jobId":42,"status":"SUCCESS","cached":true,"assetId":9}
            """);
        });

        var job = await api.CreateStoryboardVideoJobAsync(new[] { new StoryboardSceneSpec("p", "n") });

        Assert.Equal(42, job.JobId);
        Assert.True(job.Cached);
        Assert.Equal(9, job.AssetId);
        Assert.Equal(HttpMethod.Post, captured!.Method);
        Assert.Equal("http://test.local/api/media/storyboard/video/jobs", captured.RequestUri!.ToString());
    }

    [Fact]
    public async Task GetMediaJob_ParsesStatusAndProgress()
    {
        var api = ClientReturning(_ => StubHttpMessageHandler.Json(HttpStatusCode.OK, """
        {"jobId":42,"status":"RUNNING","progress":{"phase":"render","sceneIndex":1,"sceneTotal":3,"percent":33,"message":"渲染中"},
         "assetId":null,"url":null,"errorCode":null}
        """));

        var job = await api.GetMediaJobAsync(42);

        Assert.Equal("RUNNING", job.Status);
        Assert.Equal(33, job.Progress!.Percent);
        Assert.Equal("render", job.Progress.Phase);
    }

    [Fact]
    public async Task TranscribeAudio_PostsMultipart_ReturnsText()
    {
        HttpRequestMessage? captured = null;
        var api = ClientReturning(req =>
        {
            captured = req;
            return StubHttpMessageHandler.Json(HttpStatusCode.OK, """{"text":"你好世界","degraded":false}""");
        });

        var result = await api.TranscribeAudioAsync(new byte[] { 1, 2, 3 }, "speech.webm", "audio/webm");

        Assert.Equal("你好世界", result.Text);
        Assert.False(result.Degraded);
        Assert.Equal(HttpMethod.Post, captured!.Method);
        Assert.Equal("http://test.local/api/media/asr", captured.RequestUri!.ToString());
        Assert.IsType<MultipartFormDataContent>(captured.Content);
    }
}
