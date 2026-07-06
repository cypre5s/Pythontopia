using System.Collections.ObjectModel;
using System.Net;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the 学习路径 page: shows the active personalized path (GET /api/learner/path/active)
/// and (re)plans it (POST /api/learner/path/plan). A 404 means no path has been generated yet.
/// </summary>
public partial class LearningPathViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    [ObservableProperty]
    private string _goal = "尚未生成学习路径，点「规划路径」生成。";

    [ObservableProperty]
    private string _meta = string.Empty;

    public ObservableCollection<PathNodeVm> Nodes { get; } = new();

    public LearningPathViewModel(IApiClient api) => _api = api;

    [RelayCommand]
    private Task Refresh() => LoadAsync();

    public async Task LoadAsync()
    {
        ClearError();
        IsBusy = true;
        try
        {
            ApplyPath(await _api.GetActiveLearningPathAsync());
        }
        catch (ApiException ex) when (ex.StatusCode == HttpStatusCode.NotFound)
        {
            Goal = "尚未生成学习路径，点「规划路径」生成。";
            Meta = string.Empty;
            Nodes.Clear();
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
    private async Task Plan()
    {
        ClearError();
        IsBusy = true;
        try
        {
            var response = await _api.PlanLearningPathAsync();
            if (response.Path is { } path)
            {
                ApplyPath(path);
            }
            Meta += $" · 生成任务 {response.GeneratedQuestCount} · {response.RunStatus}";
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

    private void ApplyPath(LearningPathDto path)
    {
        Goal = string.IsNullOrWhiteSpace(path.GoalSummary) ? "(无目标摘要)" : path.GoalSummary!;
        Meta = $"v{path.Version} · {path.Status} · 生成于 {path.GeneratedAt:yyyy-MM-dd}" +
               (path.NextReviewAt is { } next ? $" · 下次复习 {next:yyyy-MM-dd}" : string.Empty);
        Nodes.Clear();
        foreach (var node in path.Nodes.OrderBy(n => n.DayIndex).ThenBy(n => n.OrderInDay))
        {
            Nodes.Add(PathNodeVm.From(node));
        }
    }
}

public sealed record PathNodeVm(string Header, string Detail)
{
    public static PathNodeVm From(LearningPathNodeDto node)
    {
        var header = $"Day {node.DayIndex + 1}.{node.OrderInDay + 1} · [{node.NodeType}] {node.KcCode}";
        var detail = node.ReasonSummary ?? string.Empty;
        if (!string.IsNullOrWhiteSpace(node.SuggestedResourceType))
        {
            detail += $" · 建议 {node.SuggestedResourceType}";
        }
        if (!string.IsNullOrWhiteSpace(node.EvidenceSummary))
        {
            detail += $" · 依据 {node.EvidenceSummary}";
        }
        return new PathNodeVm(header, detail);
    }
}
