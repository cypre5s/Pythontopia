using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the 复盘大厅 (review hall) page: aggregates the数字孪生周报 (weekly report) and
/// the per-KC risk predictions into display-ready rows. Risk is best-effort and never fails the page.
/// </summary>
public partial class ReviewViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    [ObservableProperty]
    private string _periodText = string.Empty;

    [ObservableProperty]
    private string _memoryText = string.Empty;

    public ObservableCollection<string> Highlights { get; } = new();
    public ObservableCollection<WeeklyInsightVm> Insights { get; } = new();
    public ObservableCollection<WeeklyMasteryDeltaVm> MasteryDeltas { get; } = new();
    public ObservableCollection<WeeklyResourceRunVm> ResourceRuns { get; } = new();
    public ObservableCollection<RiskVm> Risks { get; } = new();

    public ReviewViewModel(IApiClient api) => _api = api;

    [RelayCommand]
    private Task Refresh() => LoadAsync();

    public async Task LoadAsync()
    {
        ClearError();
        IsBusy = true;
        try
        {
            var report = await _api.GetWeeklyReportAsync();
            PeriodText = $"周期 {report.PeriodStart} ~ {report.PeriodEnd}";

            var brief = report.MemoryBrief;
            MemoryText = brief is null
                ? "本周暂无记忆流数据。"
                : $"事件 {brief.TotalEvents} · 通过 {brief.SucceededPractices} · 失败 {brief.FailedPractices}"
                  + (brief.TopKcCodes is { Count: > 0 } kcs ? $" · 高频 KC {string.Join(", ", kcs)}" : string.Empty);

            Replace(Highlights, brief?.Highlights);
            Replace(Insights, report.Insights, WeeklyInsightVm.From);
            Replace(MasteryDeltas, report.MasteryDeltas, WeeklyMasteryDeltaVm.From);
            Replace(ResourceRuns, report.ResourceRuns, WeeklyResourceRunVm.From);

            // Risk prediction is supplementary; a failure here must not blank out the report.
            try
            {
                var risks = await _api.GetRiskPredictionsAsync();
                Replace(Risks, risks, RiskVm.From);
            }
            catch (ApiException)
            {
                Risks.Clear();
            }
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

    private static void Replace(ObservableCollection<string> target, IReadOnlyList<string>? source)
    {
        target.Clear();
        foreach (var item in source ?? Array.Empty<string>())
        {
            target.Add(item);
        }
    }

    private static void Replace<TSrc, TDst>(
        ObservableCollection<TDst> target, IReadOnlyList<TSrc> source, Func<TSrc, TDst> map)
    {
        target.Clear();
        foreach (var item in source)
        {
            target.Add(map(item));
        }
    }
}

public sealed record WeeklyInsightVm(string Text, string Meta)
{
    public static WeeklyInsightVm From(WeeklyInsightDto d)
    {
        var meta = $"{d.DimensionKey} · {d.CreatedAt}";
        if (d.Tags is { Count: > 0 } tags)
        {
            meta += " · " + string.Join(", ", tags);
        }
        return new WeeklyInsightVm(d.Text ?? string.Empty, meta);
    }
}

public sealed record WeeklyMasteryDeltaVm(string KcCode, string Detail)
{
    public static WeeklyMasteryDeltaVm From(WeeklyMasteryDeltaDto d)
    {
        var detail = $"掌握 {d.CurrentScore:P0} · 置信 {d.Confidence:P0}";
        if (d.RecentEventReasons is { Count: > 0 } reasons)
        {
            detail += " · " + string.Join(", ", reasons);
        }
        return new WeeklyMasteryDeltaVm(d.KcCode, detail);
    }
}

public sealed record WeeklyResourceRunVm(string Title, string Meta)
{
    public static WeeklyResourceRunVm From(WeeklyResourceRunDto d) =>
        new($"{d.AgentName} · {d.Scenario}", $"{d.Status} · 证据 {d.EvidencePageCount} 页 · {d.CreatedAt}");
}

public sealed record RiskVm(string KcCode, string Detail, string RiskText)
{
    public static RiskVm From(RiskPredictionDto d)
    {
        var detail = d.Reason ?? string.Empty;
        if (d.DependencyPath is { Count: > 0 } path)
        {
            detail += (detail.Length > 0 ? " · " : string.Empty) + "路径 " + string.Join(" → ", path);
        }
        return new RiskVm(d.KcCode, detail, $"风险 {d.Risk:P0}");
    }
}
