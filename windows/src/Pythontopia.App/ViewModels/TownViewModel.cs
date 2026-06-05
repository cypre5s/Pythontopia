using System.Diagnostics;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Auth;
using Pythontopia.Core.Config;

namespace Pythontopia.App.ViewModels;

public partial class TownViewModel : ViewModelBase
{
    private const string GameExecutableName = "Pythontopia.Game.exe";

    private readonly AppConfig _config;
    private readonly SessionService _session;

    [ObservableProperty]
    private string _status = "点击下方按钮在独立窗口中打开像素小镇（town_hub）。";

    public TownViewModel(AppConfig config, SessionService session)
    {
        _config = config;
        _session = session;
    }

    public string BackendUrl => _config.BackendBaseUrl;

    [RelayCommand]
    private void LaunchTown()
    {
        ClearError();
        var executable = ResolveGameExecutable();
        if (executable is null)
        {
            Status = $"未找到 {GameExecutableName}。请先构建 Game 项目（见 README），或将其放到本程序所在目录。";
            return;
        }

        try
        {
            var startInfo = new ProcessStartInfo(executable)
            {
                UseShellExecute = false,
                WorkingDirectory = Path.GetDirectoryName(executable)!,
            };
            startInfo.Environment["PYT_BACKEND_URL"] = _config.BackendBaseUrl;
            if (_session.Token is { } token)
            {
                startInfo.Environment["PYT_TOKEN"] = token;
            }

            Process.Start(startInfo);
            Status = $"已启动像素小镇（{Path.GetFileName(executable)}）。窗口为独立进程，可与本面板并排使用。";
        }
        catch (Exception ex)
        {
            SetError(ex);
            Status = "启动失败。";
        }
    }

    private static string? ResolveGameExecutable()
    {
        var baseDir = AppContext.BaseDirectory;
        var candidates = new List<string>
        {
            Path.Combine(baseDir, GameExecutableName),
            Path.Combine(baseDir, "game", GameExecutableName),
        };

        // Dev layout: .../src/Pythontopia.App/bin/<cfg>/<tfm>/ -> .../src/Pythontopia.Game/bin/<cfg>/net8.0/
        var current = new DirectoryInfo(baseDir);
        var configuration = current.Parent?.Name; // Debug / Release
        var srcRoot = current.Parent?.Parent?.Parent?.Parent?.FullName;
        if (configuration is not null && srcRoot is not null)
        {
            candidates.Add(Path.Combine(srcRoot, "Pythontopia.Game", "bin", configuration, "net8.0", GameExecutableName));
        }

        return candidates.FirstOrDefault(File.Exists);
    }
}
