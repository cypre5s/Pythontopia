using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the Boss 副本 page: request an (AI/golden) boss problem for a chapter/stage, then
/// submit a solution for server-side judging. Backed by POST /api/world/boss/{chapter}/...
/// </summary>
public partial class BossViewModel : ViewModelBase
{
    private readonly IApiClient _api;
    private BossProblemDto? _problem;
    private DateTimeOffset _problemLoadedAt;

    public IReadOnlyList<int> ChapterOptions { get; } = Enumerable.Range(1, 7).ToList();
    public IReadOnlyList<int> StageOptions { get; } = Enumerable.Range(1, 4).ToList();

    [ObservableProperty]
    private int _selectedChapter = 1;

    [ObservableProperty]
    private int _selectedStage = 1;

    [ObservableProperty]
    private bool _hasProblem;

    [ObservableProperty]
    private string _statement = "选择章节 / 阶段，召唤 Boss 题。";

    [ObservableProperty]
    private string _hints = string.Empty;

    [ObservableProperty]
    private string _sample = string.Empty;

    [ObservableProperty]
    private string _code = string.Empty;

    [ObservableProperty]
    private string _result = string.Empty;

    public BossViewModel(IApiClient api) => _api = api;

    [RelayCommand]
    private async Task Summon()
    {
        ClearError();
        IsBusy = true;
        Result = string.Empty;
        try
        {
            var problem = await _api.RequestBossProblemAsync(SelectedChapter, SelectedStage);
            _problem = problem;
            _problemLoadedAt = DateTimeOffset.UtcNow;
            HasProblem = true;
            Statement = string.IsNullOrWhiteSpace(problem.Statement) ? "(无题面)" : problem.Statement!;
            Hints = problem.Hints is { Count: > 0 } hints ? "提示：" + string.Join(" / ", hints) : string.Empty;
            Sample = string.IsNullOrEmpty(problem.SampleInput) && string.IsNullOrEmpty(problem.SampleOutput)
                ? string.Empty
                : $"样例输入:\n{problem.SampleInput}\n样例输出:\n{problem.SampleOutput}";
            Code = string.Empty;
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
    private async Task Submit()
    {
        if (_problem is not { ProblemId: { } problemId })
        {
            return;
        }
        if (string.IsNullOrWhiteSpace(Code))
        {
            ErrorMessage = "请输入代码";
            return;
        }

        ClearError();
        IsBusy = true;
        try
        {
            var elapsed = Math.Max(0, (int)(DateTimeOffset.UtcNow - _problemLoadedAt).TotalSeconds);
            var result = await _api.SubmitBossAttemptAsync(SelectedChapter, _problem.Stage, problemId, Code, elapsed);
            var text = (result.Passed ? "通关！" : "未通关。") +
                       $" 用例 {result.PassedTestCases}/{result.TotalTestCases} · 得分 {result.Score}";
            if (!string.IsNullOrWhiteSpace(result.Feedback))
            {
                text += "\n" + result.Feedback;
            }
            if (!string.IsNullOrWhiteSpace(result.FailureFirstHint))
            {
                text += "\n首错提示：" + result.FailureFirstHint;
            }
            Result = text;
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
