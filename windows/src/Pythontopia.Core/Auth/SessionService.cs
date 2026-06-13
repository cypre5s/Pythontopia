using System.Text.Json;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;
using Pythontopia.Core.Json;

namespace Pythontopia.Core.Auth;

/// <summary>
/// Owns the authenticated session: login/register/logout, the current user and
/// JWT, and best-effort persistence to <c>%LOCALAPPDATA%/Pythontopia/session.json</c>.
/// When that path is unavailable the session is kept in memory only.
/// </summary>
public sealed class SessionService
{
    private readonly IApiClient _api;
    private readonly string? _sessionFilePath;
    private PersistedSession? _session;

    public SessionService(IApiClient api)
        : this(api, ResolveSessionFilePath())
    {
    }

    /// <summary>
    /// Test seam: targets an explicit session file (or <c>null</c> for memory-only). The public
    /// constructor resolves the real <c>%LOCALAPPDATA%</c> path.
    /// </summary>
    internal SessionService(IApiClient api, string? sessionFilePath)
    {
        _api = api ?? throw new ArgumentNullException(nameof(api));
        _sessionFilePath = sessionFilePath;
        TryLoad();
    }

    public UserDto? CurrentUser => _session?.User;
    public string? Token => _session?.Token;
    public DateTimeOffset? ExpiresAt => _session?.ExpiresAt;
    public bool IsAuthenticated => !string.IsNullOrEmpty(_session?.Token) && !IsExpired();
    public bool PersistsToDisk => _sessionFilePath is not null;

    /// <summary>Raised after any login / register / logout so the shell can re-route.</summary>
    public event EventHandler? SessionChanged;

    public async Task<UserDto> LoginAsync(string username, string password, CancellationToken ct = default)
    {
        var response = await _api.LoginAsync(new LoginRequest(username, password), ct).ConfigureAwait(false);
        Apply(response.Token, response.ExpiresAt, response.User);
        return response.User;
    }

    public async Task<UserDto> RegisterAsync(string username, string password, string displayName, CancellationToken ct = default)
    {
        await _api.RegisterAsync(new RegisterRequest(username, password, displayName), ct).ConfigureAwait(false);
        // The backend register endpoint returns the user only; obtain a token by logging in.
        return await LoginAsync(username, password, ct).ConfigureAwait(false);
    }

    public void Logout()
    {
        _session = null;
        _api.AuthToken = null;
        TryDelete();
        SessionChanged?.Invoke(this, EventArgs.Empty);
    }

    private bool IsExpired() => _session is not null && _session.ExpiresAt <= DateTimeOffset.UtcNow;

    private void Apply(string token, DateTimeOffset expiresAt, UserDto user)
    {
        _session = new PersistedSession(token, expiresAt, user);
        _api.AuthToken = token;
        TrySave();
        SessionChanged?.Invoke(this, EventArgs.Empty);
    }

    private void TryLoad()
    {
        if (_sessionFilePath is null || !File.Exists(_sessionFilePath))
        {
            return;
        }

        try
        {
            var json = File.ReadAllText(_sessionFilePath);
            var loaded = JsonSerializer.Deserialize<PersistedSession>(json, PytJson.Default);
            if (loaded is null || string.IsNullOrEmpty(loaded.Token))
            {
                return;
            }

            _session = loaded;
            if (!IsExpired())
            {
                _api.AuthToken = loaded.Token;
            }
        }
        catch
        {
            _session = null; // corrupt or unreadable cache is non-fatal
        }
    }

    private void TrySave()
    {
        if (_sessionFilePath is null || _session is null)
        {
            return;
        }

        try
        {
            var directory = Path.GetDirectoryName(_sessionFilePath);
            if (!string.IsNullOrEmpty(directory))
            {
                Directory.CreateDirectory(directory);
            }
            File.WriteAllText(_sessionFilePath, JsonSerializer.Serialize(_session, PytJson.Default));
        }
        catch
        {
            // Persistence is best-effort; the in-memory session remains valid.
        }
    }

    private void TryDelete()
    {
        if (_sessionFilePath is null)
        {
            return;
        }

        try
        {
            if (File.Exists(_sessionFilePath))
            {
                File.Delete(_sessionFilePath);
            }
        }
        catch
        {
            // ignore
        }
    }

    private static string? ResolveSessionFilePath()
    {
        try
        {
            var localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            return string.IsNullOrEmpty(localAppData)
                ? null
                : Path.Combine(localAppData, "Pythontopia", "session.json");
        }
        catch
        {
            return null;
        }
    }

    private sealed record PersistedSession(string Token, DateTimeOffset ExpiresAt, UserDto User);
}
