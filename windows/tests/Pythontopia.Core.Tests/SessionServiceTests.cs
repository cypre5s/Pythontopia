using System.Net;
using Pythontopia.Core.Api;
using Pythontopia.Core.Auth;
using Pythontopia.Core.Config;
using Xunit;

namespace Pythontopia.Core.Tests;

public class SessionServiceTests : IDisposable
{
    private readonly string _sessionFile =
        Path.Combine(Path.GetTempPath(), $"pyt-session-{Guid.NewGuid():N}.json");

    private static ApiClient LoginApi(string token, DateTimeOffset expiresAt)
    {
        var json =
            "{\"token\":\"" + token + "\"," +
            "\"expiresAt\":\"" + expiresAt.ToString("O") + "\"," +
            "\"user\":{\"id\":7,\"username\":\"bob\",\"displayName\":\"Bob\",\"role\":\"STUDENT\"}}";
        var handler = new StubHttpMessageHandler(_ => StubHttpMessageHandler.Json(HttpStatusCode.OK, json));
        return new ApiClient(new HttpClient(handler), new AppConfig { BackendBaseUrl = "http://test.local" });
    }

    [Fact]
    public async Task Login_AppliesTokenUser_AndRaisesEvent()
    {
        var api = LoginApi("jwt-1", DateTimeOffset.UtcNow.AddHours(12));
        var session = new SessionService(api, _sessionFile);
        var changes = 0;
        session.SessionChanged += (_, _) => changes++;

        var user = await session.LoginAsync("bob", "pw");

        Assert.Equal("bob", user.Username);
        Assert.True(session.IsAuthenticated);
        Assert.Equal("jwt-1", session.Token);
        Assert.Equal("jwt-1", api.AuthToken);
        Assert.Equal(1, changes);
        Assert.True(File.Exists(_sessionFile));
    }

    [Fact]
    public async Task Logout_ClearsSessionToken_AndFile()
    {
        var api = LoginApi("jwt-1", DateTimeOffset.UtcNow.AddHours(12));
        var session = new SessionService(api, _sessionFile);
        await session.LoginAsync("bob", "pw");

        session.Logout();

        Assert.False(session.IsAuthenticated);
        Assert.Null(session.Token);
        Assert.Null(api.AuthToken);
        Assert.False(File.Exists(_sessionFile));
    }

    [Fact]
    public async Task PersistedSession_IsReloaded_ByNewInstance()
    {
        var first = new SessionService(LoginApi("jwt-persist", DateTimeOffset.UtcNow.AddHours(12)), _sessionFile);
        await first.LoginAsync("bob", "pw");

        var api2 = LoginApi("ignored", DateTimeOffset.UtcNow.AddHours(12));
        var second = new SessionService(api2, _sessionFile);

        Assert.True(second.IsAuthenticated);
        Assert.Equal("jwt-persist", second.Token);
        Assert.Equal("jwt-persist", api2.AuthToken);
    }

    [Fact]
    public async Task ExpiredToken_IsNotAuthenticated()
    {
        var api = LoginApi("stale", DateTimeOffset.UtcNow.AddMinutes(-1));
        var session = new SessionService(api, _sessionFile);

        await session.LoginAsync("bob", "pw");

        Assert.False(session.IsAuthenticated);
    }

    [Fact]
    public void MemoryOnly_WhenNoFilePath_DoesNotPersist()
    {
        var session = new SessionService(LoginApi("jwt-1", DateTimeOffset.UtcNow.AddHours(12)), sessionFilePath: null);

        Assert.False(session.PersistsToDisk);
    }

    [Fact]
    public async Task PersistedSession_DoesNotStoreTokenInPlaintext()
    {
        var api = LoginApi("super-secret-jwt-xyz", DateTimeOffset.UtcNow.AddHours(12));
        var session = new SessionService(api, _sessionFile);

        await session.LoginAsync("bob", "pw");

        // On Windows the file is DPAPI-encrypted, so the raw token must not appear on disk.
        var raw = await File.ReadAllTextAsync(_sessionFile);
        Assert.DoesNotContain("super-secret-jwt-xyz", raw);
    }

    public void Dispose()
    {
        if (File.Exists(_sessionFile))
        {
            File.Delete(_sessionFile);
        }
    }
}
