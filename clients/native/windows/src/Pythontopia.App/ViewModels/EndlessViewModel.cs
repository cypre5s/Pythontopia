using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the 无尽模式 (endless) page: a continuous run of boss/arena problems for a chapter,
/// tracking correct / total / streak, then reporting the run via POST /api/mainline/endless/summary.
/// </summary>
public partial class EndlessViewModel : ViewModelBase
{
    private const int Stage = 1;

    private readonly IApiClient _api;
    private BossProblemDto? _problem;
    private DateTimeOffset _problemAt;
    private int _correct;
    private int _total;
    private int _streak;

    public IReadOnlyList<int> ChapterOptions { get; } = Enumerable.Range(1, 7).ToList();

    [ObservableProperty]
    private int _selectedChapter = 1;

    [ObservableProperty]
    private bool _running;

    [ObservableProperty]
    private string _statement = "选择章节，开始无尽闯关。";

    [ObservableProperty]
    private string _hints = string.Empty;

    [ObservableProperty]
    private string _code = string.Empty;

    [ObservableProperty]
    private string _scoreText = "答对 0 / 0 · 连对 0";

    [ObservableProperty]
    private string _lastResult = string.Empty;

    [ObservableProperty]
    private string _summaryResult = string.Empty;

    public EndlessViewModel(IApiClient api) => _api = api;

    [RelayCommand]
    private async Task Start()
    {
        _correct = 0;
        _total = 0;
        _streak = 0;
        SummaryResult = string.Empty;
        LastResult = string.Empty;
        UpdateScore();
        Running = true;
        await NextProblemAsync();
    }

    [RelayCommand]
    private Task Next() => NextProblemAsync();

    private async Task NextProblemAsync()
    {
        ClearError();
        IsBusy = true;
        try
        {
            var problem = await _api.RequestBossProblemAsync(SelectedChapter, Stage);
            _problem = problem;
            _problemAt = DateTimeOffset.UtcNow;
            Statement = string.IsNullOrWhiteSpace(problem.Statement) ? "(无题面)" : problem.Statement!;
            Hints = problem.Hints is { Count: > 0 } hints ? "提示：" + string.Join(" / ", hints) : string.Empty;
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
            var elapsed = Math.Max(0, (int)(DateTimeOffset.UtcNow - _problemAt).TotalSeconds);
            var result = await _api.SubmitBossAttemptAsync(SelectedChapter, _problem.Stage, problemId, Code, elapsed);
            _total++;
            if (result.Passed)
            {
                _correct++;
                _streak++;
            }
            else
            {
                _streak = 0;
            }
            UpdateScore();
            LastResult = (result.Passed ? "答对！" : "答错。") +
                         $" 用例 {result.PassedTestCases}/{result.TotalTestCases}" +
                         (string.IsNullOrWhiteSpace(result.FailureFirstHint) ? string.Empty : " · " + result.FailureFirstHint);
            if (result.Passed)
            {
                await NextProblemAsync();
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
    private async Task Finish()
    {
        if (_total == 0)
        {
            ErrorMessage = "还没有作答记录";
            return;
        }

        ClearError();
        IsBusy = true;
        try
        {
            var result = await _api.SubmitEndlessSummaryAsync(SelectedChapter, _correct, _total, _streak);
            SummaryResult = result.StarAwarded ? "结算：获得一颗星！" : "结算：未达成补星条件。";
            Running = false;
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

    private void UpdateScore() => ScoreText = $"答对 {_correct} / {_total} · 连对 {_streak}";
}
