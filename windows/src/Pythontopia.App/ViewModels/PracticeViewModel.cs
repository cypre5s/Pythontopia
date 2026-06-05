using System.Collections.ObjectModel;
using System.Text;
using System.Text.Json;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;

namespace Pythontopia.App.ViewModels;

public partial class PracticeViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    [ObservableProperty]
    private KcDto? _selectedKc;

    [ObservableProperty]
    private ProblemDto? _selectedProblem;

    [ObservableProperty]
    private PublicProblemDto? _detail;

    [ObservableProperty]
    private string _code = "# 在此编写 Python 代码\n";

    [ObservableProperty]
    private string? _samplesText;

    [ObservableProperty]
    private string? _verdict;

    [ObservableProperty]
    private string? _judgeSummary;

    [ObservableProperty]
    private string? _submitStatus;

    public ObservableCollection<KcDto> Kcs { get; } = new();
    public ObservableCollection<ProblemDto> Problems { get; } = new();

    public PracticeViewModel(IApiClient api) => _api = api;

    public async Task LoadAsync()
    {
        ClearError();
        IsBusy = true;
        try
        {
            var kcs = await _api.GetKcsAsync();
            Kcs.Clear();
            foreach (var kc in kcs)
            {
                Kcs.Add(kc);
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

    partial void OnSelectedKcChanged(KcDto? value)
    {
        Problems.Clear();
        ResetProblem();
        if (value?.KcCode is { } kcCode)
        {
            _ = LoadProblemsAsync(kcCode);
        }
    }

    partial void OnSelectedProblemChanged(ProblemDto? value)
    {
        ResetProblem();
        if (value?.ProblemId is { } problemId)
        {
            _ = LoadDetailAsync(problemId);
        }
    }

    private void ResetProblem()
    {
        Detail = null;
        SamplesText = null;
        Verdict = null;
        JudgeSummary = null;
        SubmitStatus = null;
    }

    private async Task LoadProblemsAsync(string kcCode)
    {
        ClearError();
        IsBusy = true;
        try
        {
            var problems = await _api.GetProblemsAsync(kcCode);
            Problems.Clear();
            foreach (var problem in problems)
            {
                Problems.Add(problem);
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

    private async Task LoadDetailAsync(long problemId)
    {
        ClearError();
        IsBusy = true;
        try
        {
            Detail = await _api.GetPublicProblemAsync(problemId);
            SamplesText = FormatSamples(Detail.Samples);
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
    private async Task SubmitAsync()
    {
        if (Detail is null)
        {
            ErrorMessage = "请先选择题目";
            return;
        }
        if (string.IsNullOrWhiteSpace(Code))
        {
            ErrorMessage = "代码不能为空";
            return;
        }

        ClearError();
        Verdict = null;
        JudgeSummary = null;
        var progress = new Progress<string>(s => SubmitStatus = s);

        try
        {
            IsBusy = true;
            var result = await _api.SubmitAndPollAsync(Detail.Id, Code, progress);
            Verdict = result.JudgeResult?.Verdict ?? result.Submission.Status;
            JudgeSummary = result.JudgeResult is { } judge
                ? $"{judge.Verdict}  ·  通过 {judge.PassedCases}/{judge.TotalCases}"
                    + (string.IsNullOrWhiteSpace(judge.FailureReason) ? string.Empty : $"  ·  {judge.FailureReason}")
                : $"状态：{result.Submission.Status}";
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

    private static string FormatSamples(JsonElement? samples)
    {
        if (samples is not { } element
            || element.ValueKind is JsonValueKind.Null or JsonValueKind.Undefined)
        {
            return string.Empty;
        }

        try
        {
            if (element.ValueKind == JsonValueKind.Array)
            {
                var builder = new StringBuilder();
                var index = 1;
                foreach (var item in element.EnumerateArray())
                {
                    var input = TryGet(item, "input", "stdin");
                    var output = TryGet(item, "output", "expected", "stdout");
                    builder.AppendLine($"# 样例 {index++}");
                    if (input is not null)
                    {
                        builder.AppendLine($"输入:\n{input}");
                    }
                    if (output is not null)
                    {
                        builder.AppendLine($"输出:\n{output}");
                    }
                    if (input is null && output is null)
                    {
                        builder.AppendLine(item.ToString());
                    }
                    builder.AppendLine();
                }
                return builder.ToString().TrimEnd();
            }

            return element.ToString();
        }
        catch
        {
            return element.ToString();
        }
    }

    private static string? TryGet(JsonElement item, params string[] names)
    {
        if (item.ValueKind != JsonValueKind.Object)
        {
            return null;
        }

        foreach (var name in names)
        {
            foreach (var property in item.EnumerateObject())
            {
                if (string.Equals(property.Name, name, StringComparison.OrdinalIgnoreCase))
                {
                    return property.Value.ValueKind == JsonValueKind.String
                        ? property.Value.GetString()
                        : property.Value.ToString();
                }
            }
        }

        return null;
    }
}
