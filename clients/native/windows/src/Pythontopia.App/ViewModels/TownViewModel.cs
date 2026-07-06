using System.Diagnostics;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.UI.Dispatching;
using Pythontopia.Core.Auth;
using Pythontopia.Core.Config;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the Town page. The pixel town runs as a standalone MonoGame (DesktopGL) process;
/// this view-model owns its lifecycle so only one instance is launched at a time and the UI
/// reflects whether it is currently running.
/// </summary>
public partial class TownViewModel : ViewModelBase
{
    private const string GameExecutableName = "Pythontopia.Game.exe";

    private readonly AppConfig _config;
    private readonly SessionService _session;
    private readonly DispatcherQueue? _dispatcher = DispatcherQueue.GetForCurrentThread();
    private Process? _process;

    [ObservableProperty]
    private string _status = "点击下方按钮在独立窗口中打开像素小镇（town_hub）。";

    [ObservableProperty]
    private bool _isTownRunning;

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

        if (_process is { HasExited: false })
        {
            Status = "像素小镇已在运行（独立窗口）。请切换到该窗口，或关闭后再重新启动。";
            return;
        }

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

            var process = Process.Start(startInfo);
            if (process is null)
            {
                Status = "启动失败：操作系统未能创建进程。";
                return;
            }

            process.EnableRaisingEvents = true;
            process.Exited += OnTownExited;
            _process = process;
            IsTownRunning = true;
            Status = $"已启动像素小镇（{Path.GetFileName(executable)}）。窗口为独立进程，可与本面板并排使用。";
        }
        catch (Exception ex)
        {
            SetError(ex);
            Status = "启动失败。";
        }
    }

    /// <summary>Resets state when the town process exits, marshalling back to the UI thread.</summary>
    private void OnTownExited(object? sender, EventArgs e)
    {
        void Reset()
        {
            if (sender is Process p)
            {
                p.Exited -= OnTownExited;
            }
            _process = null;
            IsTownRunning = false;
            Status = "像素小镇已关闭，可再次启动。";
        }

        if (_dispatcher is null || _dispatcher.HasThreadAccess)
        {
            Reset();
        }
        else
        {
            _dispatcher.TryEnqueue(Reset);
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
