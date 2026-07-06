namespace Pythontopia.Core.Config;

/// <summary>
/// Runtime configuration for the desktop client. The backend base URL defaults to the
/// public Pythontopia server and can be overridden with the <c>PYT_BACKEND_URL</c>
/// environment variable (e.g. <c>http://localhost:18080</c> for local development).
/// </summary>
public sealed class AppConfig
{
    /// <summary>Public Pythontopia backend via ECS Nginx (port 80, /api prefix). Override locally via env var.</summary>
    public const string DefaultBackendBaseUrl = "http://47.120.29.62";
    public const string BackendUrlEnvironmentVariable = "PYT_BACKEND_URL";

    /// <summary>Base URL without a trailing slash, e.g. <c>http://localhost:18080</c>.</summary>
    public string BackendBaseUrl { get; init; } = DefaultBackendBaseUrl;

    /// <summary>How long to wait for a single HTTP request before timing out.</summary>
    public TimeSpan RequestTimeout { get; init; } = TimeSpan.FromSeconds(30);

    public static AppConfig FromEnvironment()
    {
        var raw = Environment.GetEnvironmentVariable(BackendUrlEnvironmentVariable);
        var baseUrl = string.IsNullOrWhiteSpace(raw)
            ? DefaultBackendBaseUrl
            : raw.Trim().TrimEnd('/');
        return new AppConfig { BackendBaseUrl = baseUrl };
    }
}
