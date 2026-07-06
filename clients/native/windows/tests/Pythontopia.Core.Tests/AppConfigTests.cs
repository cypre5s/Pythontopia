using Pythontopia.Core.Config;
using Xunit;

namespace Pythontopia.Core.Tests;

public class AppConfigTests
{
    [Fact]
    public void FromEnvironment_Unset_UsesDefaults()
    {
        WithBackendEnv(null, () =>
        {
            var config = AppConfig.FromEnvironment();
            Assert.Equal(AppConfig.DefaultBackendBaseUrl, config.BackendBaseUrl);
            Assert.Equal(TimeSpan.FromSeconds(30), config.RequestTimeout);
        });
    }

    [Fact]
    public void FromEnvironment_Override_TrimsTrailingSlash()
    {
        WithBackendEnv("http://192.168.1.10:18080/", () =>
        {
            Assert.Equal("http://192.168.1.10:18080", AppConfig.FromEnvironment().BackendBaseUrl);
        });
    }

    [Fact]
    public void FromEnvironment_BlankValue_FallsBackToDefault()
    {
        WithBackendEnv("   ", () =>
        {
            Assert.Equal(AppConfig.DefaultBackendBaseUrl, AppConfig.FromEnvironment().BackendBaseUrl);
        });
    }

    private static void WithBackendEnv(string? value, Action body)
    {
        var name = AppConfig.BackendUrlEnvironmentVariable;
        var original = Environment.GetEnvironmentVariable(name);
        try
        {
            Environment.SetEnvironmentVariable(name, value);
            body();
        }
        finally
        {
            Environment.SetEnvironmentVariable(name, original);
        }
    }
}
