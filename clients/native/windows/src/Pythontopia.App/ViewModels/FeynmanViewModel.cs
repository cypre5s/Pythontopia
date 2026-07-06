using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the 费曼学弟 (teach-back) page: start a session for a KC, explain it in your own
/// words (teach), and get a verdict (judge). Backed by POST /api/feynman/*.
/// </summary>
public partial class FeynmanViewModel : ViewModelBase
{
    private readonly IApiClient _api;
    private long _sessionId;

    [ObservableProperty]
    private string _kcCode = "PY43-KC-001";

    [ObservableProperty]
    private string _explanation = string.Empty;

    [ObservableProperty]
    private bool _hasSession;

    [ObservableProperty]
    private string _sessionStatus = "输入知识点编码，开始向「费曼学弟」讲解。";

    [ObservableProperty]
    private string _verdict = string.Empty;

    public ObservableCollection<FeynmanTurnVm> Transcript { get; } = new();

    public FeynmanViewModel(IApiClient api) => _api = api;

    [RelayCommand]
    private async Task Start()
    {
        if (string.IsNullOrWhiteSpace(KcCode))
        {
            ErrorMessage = "请输入知识点编码";
            return;
        }

        ClearError();
        IsBusy = true;
        Verdict = string.Empty;
        try
        {
            var session = await _api.StartFeynmanAsync(KcCode.Trim());
            _sessionId = session.Id;
            HasSession = true;
            Transcript.Clear();
            SessionStatus = $"会话 #{session.Id} · KC {session.KcCode} · {session.Status}";
        }
        catch (Exception ex)
        {
            SetError(ex);
        }
        finally
        {
            IsBusy = false;
        }
    }

    [RelayCommand]
    private async Task Teach()
    {
        if (!HasSession)
        {
            return;
        }
        if (string.IsNullOrWhiteSpace(Explanation))
        {
            ErrorMessage = "请输入你的讲解";
            return;
        }

        ClearError();
        IsBusy = true;
        try
        {
            var mine = Explanation.Trim();
            Transcript.Add(new FeynmanTurnVm("我", mine));
            var turn = await _api.FeynmanTeachAsync(_sessionId, mine);
            var content = turn.Content ?? string.Empty;
            if (!string.IsNullOrWhiteSpace(turn.TaughtFix))
            {
                content += $"\n（订正：{turn.TaughtFix}）";
            }
            Transcript.Add(new FeynmanTurnVm(turn.Role ?? "学弟", content));
            Explanation = string.Empty;
        }
        catch (Exception ex)
        {
            SetError(ex);
        }
        finally
        {
            IsBusy = false;
        }
    }

    [RelayCommand]
    private async Task Judge()
    {
        if (!HasSession)
        {
            return;
        }

        ClearError();
        IsBusy = true;
        try
        {
            var verdict = await _api.FeynmanJudgeAsync(_sessionId);
            Verdict = (verdict.Passed ? "通过：" : "未通过：") + verdict.Message;
        }
        catch (Exception ex)
        {
            SetError(ex);
        }
        finally
        {
            IsBusy = false;
        }
    }
}

public sealed record FeynmanTurnVm(string Speaker, string Content);
