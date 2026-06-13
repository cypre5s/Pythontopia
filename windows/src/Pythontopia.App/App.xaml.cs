using System.Net.Http;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml;
using Pythontopia.App.ViewModels;
using Pythontopia.Core.Api;
using Pythontopia.Core.Auth;
using Pythontopia.Core.Config;

namespace Pythontopia.App;

public partial class App : Application
{
    private Window? _window;

    /// <summary>Process-wide service container (simple service-locator for page construction).</summary>
    public static IServiceProvider Services { get; private set; } = default!;

    public App()
    {
        InitializeComponent();
        Services = ConfigureServices();
    }

    public static T GetService<T>() where T : class => Services.GetRequiredService<T>();

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        _window = new MainWindow();
        _window.Activate();
    }

    private static IServiceProvider ConfigureServices()
    {
        var services = new ServiceCollection();

        services.AddSingleton(AppConfig.FromEnvironment());
        services.AddSingleton<HttpClient>();
        services.AddSingleton<IApiClient, ApiClient>();
        services.AddSingleton<SessionService>();

        services.AddTransient<LoginViewModel>();
        services.AddTransient<MainlineViewModel>();
        services.AddTransient<ReviewViewModel>();
        services.AddTransient<ChapterTestViewModel>();
        services.AddTransient<ExamplesViewModel>();
        services.AddTransient<CalendarViewModel>();
        services.AddTransient<InsightsViewModel>();
        services.AddTransient<BilibiliViewModel>();
        services.AddTransient<SavesViewModel>();
        services.AddTransient<FeynmanViewModel>();
        services.AddTransient<BossViewModel>();
        services.AddTransient<EndlessViewModel>();
        services.AddTransient<SkillsViewModel>();
        services.AddTransient<NpcViewModel>();
        services.AddTransient<LibraryViewModel>();
        services.AddTransient<LearningPathViewModel>();
        services.AddTransient<MediaViewModel>();
        services.AddTransient<ProfileViewModel>();
        services.AddTransient<QuestsViewModel>();
        services.AddTransient<CourseViewModel>();
        services.AddTransient<PracticeViewModel>();
        services.AddTransient<WorkshopViewModel>();
        services.AddTransient<BugMineViewModel>();
        services.AddTransient<ClassroomViewModel>();
        services.AddTransient<TownViewModel>();

        return services.BuildServiceProvider();
    }
}
