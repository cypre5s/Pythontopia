namespace Pythontopia.Core.Config;

/// <summary>
/// Runtime configuration for the desktop client. The backend base URL defaults
/// to <c>http://localhost:18080</c> and can be overridden with the
/// <c>PYT_BACKEND_URL</c> environment variable.
/// </summary>
public sealed class AppConfig
{
    public const string DefaultBackendBaseUrl = "http://localhost:18080";
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
