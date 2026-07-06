using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the 学情洞察 page: reflective insights (GET /api/learner/insights) plus the
/// agent-notebook memory items (GET /api/learner/memory). Both student-only, read-only.
/// </summary>
public partial class InsightsViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    public ObservableCollection<InsightVm> Insights { get; } = new();
    public ObservableCollection<MemoryVm> Memory { get; } = new();

    public InsightsViewModel(IApiClient api) => _api = api;

    [RelayCommand]
    private Task Refresh() => LoadAsync();

    public async Task LoadAsync()
    {
        ClearError();
        IsBusy = true;
        try
        {
            var insights = await _api.GetInsightsAsync(limit: 20);
            Insights.Clear();
            foreach (var insight in insights)
            {
                Insights.Add(InsightVm.From(insight));
            }

            var memory = await _api.GetMemoryAsync(limit: 12);
            Memory.Clear();
            foreach (var item in memory)
            {
                Memory.Add(MemoryVm.From(item));
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
}

public sealed record InsightVm(string Text, string Meta)
{
    public static InsightVm From(LearnerInsightDto d)
    {
        var meta = $"{d.DimensionKey} · 置信 {d.Confidence:P0} · {d.Source} · {d.CreatedAt}";
        if (d.Tags is { Count: > 0 } tags)
        {
            meta += " · " + string.Join(", ", tags);
        }
        return new InsightVm(d.Text ?? string.Empty, meta);
    }
}

public sealed record MemoryVm(string Summary, string Meta)
{
    public static MemoryVm From(MemoryItemDto d)
    {
        var meta = $"{d.EventType} · 重要度 {d.Importance} · 综合 {d.TotalScore:P0} · {d.CreatedAt}";
        if (d.KcCodes is { Count: > 0 } kcs)
        {
            meta += " · KC " + string.Join(", ", kcs);
        }
        return new MemoryVm(d.SummaryZh ?? string.Empty, meta);
    }
}
