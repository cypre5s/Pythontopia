using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the 章节测验 (chapter test) page: shows the latest test result for a chosen chapter
/// (read-only) from <c>GET /api/mainline/chapters/{chapter}/test/latest</c>.
/// </summary>
public partial class ChapterTestViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    public IReadOnlyList<int> ChapterOptions { get; } = Enumerable.Range(1, 7).ToList();

    [ObservableProperty]
    private int _selectedChapter = 1;

    [ObservableProperty]
    private string _summary = "选择章节并加载最近一次测验结果。";

    [ObservableProperty]
    private bool _hasResult;

    public ObservableCollection<ChapterTestItemVm> Items { get; } = new();

    [ObservableProperty]
    private bool _hasExam;

    [ObservableProperty]
    private string _examStatus = string.Empty;

    private long _examTestId;

    public ObservableCollection<ChapterTestQuestionVm> ExamItems { get; } = new();

    public ChapterTestViewModel(IApiClient api) => _api = api;

    [RelayCommand]
    private Task Load() => LoadAsync();

    public async Task LoadAsync()
    {
        ClearError();
        IsBusy = true;
        HasResult = false;
        try
        {
            var result = await _api.GetLatestChapterTestAsync(SelectedChapter);
            Summary =
                $"第 {result.Chapter} 章 · 第 {result.AttemptNo} 次 · 状态 {result.Status} · " +
                $"得分 {result.Score}/{result.MaxScore} · {(result.Passed ? "通过" : "未通过")} · 星 {result.Stars}" +
                (result.Overtime ? " · 超时" : string.Empty);

            Items.Clear();
            foreach (var item in result.Items)
            {
                Items.Add(ChapterTestItemVm.From(item));
            }
            HasResult = Items.Count > 0;
        }
        catch (ApiException ex)
        {
            Summary = $"暂无第 {SelectedChapter} 章测验记录（或加载失败：{ex.Message}）。";
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
    private async Task StartExam()
    {
        ClearError();
        IsBusy = true;
        HasResult = false;
        try
        {
            var prep = await _api.PrepareChapterTestAsync(SelectedChapter);
            ExamStatus = $"准备状态：{prep.Status}";
            var paper = await _api.StartChapterTestAsync(SelectedChapter);
            _examTestId = paper.TestId;
            ExamItems.Clear();
            foreach (var question in paper.Items)
            {
                ExamItems.Add(new ChapterTestQuestionVm(question));
            }
            HasExam = ExamItems.Count > 0;
            ExamStatus =
                $"第 {paper.Chapter} 章 · 第 {paper.AttemptNo} 次 · 限时 {paper.TimeLimitSec / 60} 分钟 · 共 {paper.Items.Count} 题";
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
    private async Task SubmitExam()
    {
        if (!HasExam)
        {
            return;
        }

        ClearError();
        IsBusy = true;
        try
        {
            var answers = ExamItems.ToDictionary(q => q.Qid, q => q.Answer ?? string.Empty);
            var result = await _api.SubmitChapterTestAsync(SelectedChapter, _examTestId, answers);
            Summary =
                $"提交完成 · 得分 {result.Score}/{result.MaxScore} · " +
                $"{(result.Passed ? "通过" : "未通过")} · 星 {result.Stars}";
            Items.Clear();
            foreach (var item in result.Items)
            {
                Items.Add(ChapterTestItemVm.From(item));
            }
            HasResult = Items.Count > 0;
            HasExam = false;
            ExamItems.Clear();
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

/// <summary>An answerable exam question row (two-way bound <see cref="Answer"/>).</summary>
public partial class ChapterTestQuestionVm : ObservableObject
{
    public string Qid { get; }
    public string Header { get; }
    public string Body { get; }
    public string OptionsText { get; }

    [ObservableProperty]
    private string _answer = string.Empty;

    public ChapterTestQuestionVm(ChapterTestQuestionViewDto dto)
    {
        Qid = dto.Qid ?? string.Empty;
        var label = string.IsNullOrWhiteSpace(dto.Title) ? dto.Qid : dto.Title;
        Header = $"[{dto.Type}] {label} · {dto.Score} 分";
        Body = string.IsNullOrWhiteSpace(dto.StatementMarkdown) ? (dto.Stem ?? string.Empty) : dto.StatementMarkdown!;
        OptionsText = dto.Options is { Count: > 0 } options ? string.Join("\n", options) : string.Empty;
    }
}

public sealed record ChapterTestItemVm(string Title, string Detail, string Verdict)
{
    public static ChapterTestItemVm From(ChapterTestQuestionResultDto d)
    {
        var detail = $"得分 {d.AwardedScore}/{d.MaxScore}";
        if (d is { PassedCases: { } passed, TotalCases: { } total })
        {
            detail += $" · 用例 {passed}/{total}";
        }
        if (!string.IsNullOrWhiteSpace(d.Explain))
        {
            detail += " · " + d.Explain;
        }
        return new ChapterTestItemVm($"{d.Qid} · {d.Type}", detail, d.Correct ? "正确" : "错误");
    }
}
