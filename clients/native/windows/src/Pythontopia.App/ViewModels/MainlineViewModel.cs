using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the 主线地图 (mainline map) page: loads the chapter/stage overview from
/// <c>GET /api/mainline/overview</c> and projects it into display-ready rows.
/// </summary>
public partial class MainlineViewModel : ViewModelBase
{
    private readonly IApiClient _api;
    private long? _currentStageId;
    private IReadOnlyList<long> _currentStagePageIds = Array.Empty<long>();

    [ObservableProperty]
    private int _totalStars;

    public ObservableCollection<MainlineChapterVm> Chapters { get; } = new();

    public MainlineViewModel(IApiClient api) => _api = api;

    public string TotalStarsText => $"总星 {TotalStars}";

    partial void OnTotalStarsChanged(int value) => OnPropertyChanged(nameof(TotalStarsText));

    [ObservableProperty]
    private bool _showStageDetail;

    [ObservableProperty]
    private string _stageDetailTitle = string.Empty;

    [ObservableProperty]
    private string _stageDetailSummary = string.Empty;

    public ObservableCollection<string> StageVideos { get; } = new();
    public ObservableCollection<string> StageExamples { get; } = new();
    public ObservableCollection<string> StageQuestions { get; } = new();

    [RelayCommand]
    private Task Refresh() => LoadAsync();

    public async Task LoadAsync()
    {
        ClearError();
        IsBusy = true;
        try
        {
            var overview = await _api.GetMainlineOverviewAsync();
            TotalStars = overview.TotalStars;
            Chapters.Clear();
            foreach (var chapter in overview.Chapters)
            {
                Chapters.Add(MainlineChapterVm.From(chapter));
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

    [RelayCommand]
    private async Task OpenStage(long stageId)
    {
        ClearError();
        IsBusy = true;
        try
        {
            var detail = await _api.GetStageDetailAsync(stageId);
            _currentStageId = detail.Id;
            _currentStagePageIds = detail.CoursewarePageIds ?? Array.Empty<long>();
            StageDetailTitle = $"第 {detail.Chapter} 章 · {detail.Index + 1}. {detail.Title}";
            StageDetailSummary =
                $"KC {string.Join(", ", detail.KcCodes ?? Array.Empty<string>())} · " +
                $"看 {detail.VideoWatchedSec}/{detail.RequiredVideoSec}s · " +
                $"学 {(detail.PagesReadIds?.Count ?? 0)}/{detail.RequiredPages} 页 · " +
                $"练 {(detail.ExamplesDoneIds?.Count ?? 0)}/{detail.RequiredExamples} 例 · " +
                $"测 {(detail.QuestionsCorrectIds?.Count ?? 0)}/{detail.RequiredQuestions} 题" +
                (detail.Completed ? " · 已通关" : string.Empty);
            Fill(StageVideos, detail.Videos.Select(v => $"{v.Title}（{v.DurationSec}s）"));
            Fill(StageExamples, detail.Examples.Select(e => $"{e.DisplayNo} {e.DisplayName} · {e.KcCode}"));
            Fill(StageQuestions, detail.Questions.Select(q => $"{q.Ordinal}. [{q.Type}] {q.Stem}"));
            ShowStageDetail = true;
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
    private void CloseStage() => ShowStageDetail = false;

    [RelayCommand]
    private Task ReportVideo() => RunStageActionAsync(id => _api.ReportVideoProgressAsync(id, 60));

    [RelayCommand]
    private Task MarkPagesRead() =>
        _currentStagePageIds.Count == 0
            ? Task.CompletedTask
            : RunStageActionAsync(id => _api.ReportPagesReadAsync(id, _currentStagePageIds));

    private async Task RunStageActionAsync(Func<long, Task<StageProgressDto>> action)
    {
        if (_currentStageId is not { } stageId)
        {
            return;
        }

        ClearError();
        IsBusy = true;
        try
        {
            var progress = await action(stageId);
            StageDetailSummary =
                $"看 {progress.VideoWatchedSec}/{progress.RequiredVideoSec}s · " +
                $"学 {progress.PagesRead}/{progress.RequiredPages} 页 · " +
                $"练 {progress.ExamplesDone}/{progress.RequiredExamples} 例" +
                (progress.Completed ? " · 已通关" : string.Empty);
            await LoadAsync();
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

    private static void Fill(ObservableCollection<string> target, IEnumerable<string> items)
    {
        target.Clear();
        foreach (var item in items)
        {
            target.Add(item);
        }
    }
}

/// <summary>Display row for one mainline stage (关卡).</summary>
public sealed record MainlineStageVm(long Id, string Title, string Progress, string Status)
{
    public static MainlineStageVm From(MainlineStageDto stage)
    {
        var progress =
            $"看 {stage.VideoWatchedSec}/{stage.RequiredVideoSec}s · " +
            $"学 {stage.PagesRead}/{stage.RequiredPages} 页 · " +
            $"练 {stage.ExamplesDone}/{stage.RequiredExamples} 题";
        var status = stage.Completed ? "已通关" : "进行中";
        return new MainlineStageVm(stage.Id, $"{stage.Index + 1}. {stage.Title}", progress, status);
    }
}

/// <summary>Display row for one mainline chapter (章) with its stages.</summary>
public sealed record MainlineChapterVm(
    string Title,
    string Subtitle,
    IReadOnlyList<MainlineStageVm> Stages)
{
    public static MainlineChapterVm From(MainlineChapterDto chapter)
    {
        var parts = new List<string> { $"星 {chapter.Stars}" };
        if (!string.IsNullOrWhiteSpace(chapter.TestStatus))
        {
            parts.Add($"测验 {chapter.TestStatus}");
        }
        if (chapter.BestTestScore is { } best)
        {
            parts.Add($"最佳 {best} 分");
        }
        parts.Add(chapter.Unlocked ? "已解锁" : "未解锁");

        var stages = chapter.Stages.Select(MainlineStageVm.From).ToList();
        return new MainlineChapterVm(
            $"第 {chapter.Chapter} 章 · {chapter.Title}",
            string.Join(" · ", parts),
            stages);
    }
}
