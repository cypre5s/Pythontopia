using System.Collections.ObjectModel;
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the 学习档案室 (profile room): a read-only hub aggregating the learner's narrative profile,
/// the 8 profile dimensions, the digital-twin weekly brief, and the agent memory notebook — the
/// graduation page's "回顾我的学习档案" target. Reuses existing endpoints (no new backend calls).
/// </summary>
public sealed partial class ProfileRoomViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    [ObservableProperty] private bool _hasProfile;
    [ObservableProperty] private string _narrative = string.Empty;
    [ObservableProperty] private string _major = string.Empty;
    [ObservableProperty] private string _noProfileText = string.Empty;
    [ObservableProperty] private string _weeklySummary = string.Empty;
    [ObservableProperty] private string _weekRange = string.Empty;
    [ObservableProperty] private bool _hasWeekly;
    [ObservableProperty] private bool _hasMemory;

    public ObservableCollection<ProfileDimensionVm> Dimensions { get; } = new();
    public ObservableCollection<string> Highlights { get; } = new();
    public ObservableCollection<ProfileMemoryVm> Memory { get; } = new();

    // ── ProfileAgent 画像访谈 ──
    public ObservableCollection<InterviewMessageVm> InterviewMessages { get; } = new();

    [ObservableProperty] private string _interviewInput = string.Empty;
    [ObservableProperty] private bool _interviewActive;
    [ObservableProperty] private bool _interviewDone;
    [ObservableProperty] private bool _interviewBusy;
    [ObservableProperty] private string _interviewStatus = "「画像访谈」由 ProfileAgent 通过几轮对话生成你的 8 维画像。";

    private string? _sessionId;

    public ProfileRoomViewModel(IApiClient api) => _api = api;

    [RelayCommand]
    private async Task StartInterview()
    {
        if (InterviewBusy)
        {
            return;
        }
        ClearError();
        InterviewBusy = true;
        try
        {
            var res = await _api.OpenProfileInterviewAsync();
            _sessionId = res.InterviewSessionId;
            InterviewMessages.Clear();
            foreach (var m in res.History ?? Array.Empty<ProfileMessageDto>())
            {
                InterviewMessages.Add(new InterviewMessageVm(m.Role, m.Content));
            }
            if (InterviewMessages.Count == 0)
            {
                InterviewMessages.Add(new InterviewMessageVm("assistant", "你好！我是画像助手。先聊聊你的专业背景和这门课的学习目标吧。"));
            }
            var open = string.Equals(res.Status, "OPEN", StringComparison.OrdinalIgnoreCase);
            InterviewActive = open;
            InterviewDone = !open;
            InterviewStatus = open ? "访谈进行中：逐题回答，AI 会据此构建你的画像。" : "访谈已完成。";
        }
        catch (Exception ex)
        {
            SetError(ex);
        }
        finally
        {
            InterviewBusy = false;
        }
    }

    [RelayCommand]
    private async Task SendInterview()
    {
        if (InterviewBusy || _sessionId is null || string.IsNullOrWhiteSpace(InterviewInput))
        {
            return;
        }
        var text = InterviewInput.Trim();
        var priorHistory = InterviewMessages.Select(m => new ProfileMessageDto(m.Role, m.Content)).ToList();
        InterviewMessages.Add(new InterviewMessageVm("user", text));
        InterviewInput = string.Empty;

        ClearError();
        InterviewBusy = true;
        try
        {
            var res = await _api.SendProfileTurnAsync(_sessionId, new ProfileTurnRequest(text, priorHistory));
            if (!string.IsNullOrWhiteSpace(res.NextQuestion))
            {
                InterviewMessages.Add(new InterviewMessageVm("assistant", res.NextQuestion!));
            }
            if (res.Done)
            {
                InterviewActive = false;
                InterviewDone = true;
                InterviewStatus = "访谈完成，正在保存画像并刷新档案…";
                try
                {
                    await _api.FinishProfileInterviewAsync(_sessionId);
                }
                catch
                {
                    // finalisation is best-effort; the turn already persisted the draft
                }
                await LoadAsync();
                InterviewStatus = "访谈完成，画像已更新。";
            }
        }
        catch (Exception ex)
        {
            SetError(ex);
        }
        finally
        {
            InterviewBusy = false;
        }
    }

    public async Task LoadAsync()
    {
        ClearError();
        IsBusy = true;
        Dimensions.Clear();
        Highlights.Clear();
        Memory.Clear();
        try
        {
            var profile = await _api.GetProfileAsync();
            HasProfile = profile is not null;
            Narrative = profile?.NarrativeSummary ?? string.Empty;
            Major = string.IsNullOrWhiteSpace(profile?.Major) ? string.Empty : $"专业：{profile!.Major}";
            NoProfileText = HasProfile
                ? string.Empty
                : "尚未生成学习画像。多在小镇做题、对话、完成任务，AI 会被动构建你的 8 维画像。";
        }
        catch (Exception ex)
        {
            SetError(ex);
        }

        await LoadSectionAsync(async () =>
        {
            var dims = await _api.GetProfileDimensionsAsync();
            foreach (var d in dims)
            {
                Dimensions.Add(new ProfileDimensionVm(
                    d.Label,
                    (int)Math.Round(d.Score * 100),
                    $"置信 {(int)Math.Round(d.Confidence * 100)}% · {d.EvidenceCount} 条证据"));
            }
        });

        await LoadSectionAsync(async () =>
        {
            var weekly = await _api.GetWeeklyReportAsync();
            var brief = weekly.MemoryBrief;
            if (brief is not null)
            {
                WeeklySummary = $"本周事件 {brief.TotalEvents} · 通过练习 {brief.SucceededPractices} · 失败 {brief.FailedPractices}";
                foreach (var h in brief.Highlights ?? Array.Empty<string>())
                {
                    Highlights.Add(h);
                }
            }
            WeekRange = (weekly.PeriodStart is not null || weekly.PeriodEnd is not null)
                ? $"{weekly.PeriodStart} ~ {weekly.PeriodEnd}"
                : string.Empty;
            HasWeekly = WeeklySummary.Length > 0 || Highlights.Count > 0;
        });

        await LoadSectionAsync(async () =>
        {
            var memory = await _api.GetMemoryAsync(limit: 8);
            foreach (var m in memory)
            {
                Memory.Add(new ProfileMemoryVm(
                    string.IsNullOrWhiteSpace(m.SummaryZh) ? (m.EventType ?? "记忆") : m.SummaryZh!,
                    m.CreatedAt ?? string.Empty,
                    string.Join(" / ", m.KcCodes ?? Array.Empty<string>())));
            }
            HasMemory = Memory.Count > 0;
        });

        IsBusy = false;
    }

    /// <summary>Runs an optional section loader; a failure there must not blank the whole room.</summary>
    private static async Task LoadSectionAsync(Func<Task> load)
    {
        try
        {
            await load();
        }
        catch
        {
            // section is optional (e.g. teacher accounts / empty data); ignore and keep the rest
        }
    }
}

public sealed record ProfileDimensionVm(string Label, int Percent, string Meta);

public sealed record ProfileMemoryVm(string Summary, string When, string Kcs)
{
    public bool HasKcs => Kcs.Length > 0;
}

public sealed record InterviewMessageVm(string Role, string Content)
{
    public bool IsUser => string.Equals(Role, "user", StringComparison.OrdinalIgnoreCase);
    public string RoleLabel => IsUser ? "你" : "画像助手";
}
