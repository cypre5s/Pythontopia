using System.Collections.ObjectModel;
using System.Text;
using System.Text.Encodings.Web;
using System.Text.Json;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Bug 矿洞 (错题矿洞 / 遗忘回声) review. Lists open mine sessions and FSRS-due echo candidates,
/// opens a session, gates the 5 depth-layer cards behind server reveals and submits retry / variant
/// code for real server judging.
/// </summary>
public partial class BugMineViewModel : ViewModelBase
{
    private static readonly JsonSerializerOptions PrettyJson = new()
    {
        WriteIndented = true,
        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
    };

    private readonly IApiClient _api;

    [ObservableProperty]
    private BugMineSessionDto? _session;

    [ObservableProperty]
    private PublicProblemDto? _variantDetail;

    [ObservableProperty]
    private string _retryCode = "# 在此修正你的代码\n";

    [ObservableProperty]
    private string? _verdict;

    [ObservableProperty]
    private string? _judgeSummary;

    [ObservableProperty]
    private string? _submitStatus;

    public ObservableCollection<BugMineErrorBookItemDto> ErrorBook { get; } = new();
    public ObservableCollection<BugMineEchoCandidateDto> Echoes { get; } = new();
    public ObservableCollection<BugMineDepthStepDto> DepthSteps { get; } = new();
    public ObservableCollection<BugMineCardItem> Cards { get; } = new();

    public BugMineViewModel(IApiClient api) => _api = api;

    public bool HasSession => Session is not null;
    public bool HasVariant => Session?.VariantProblem?.Id is not null;

    public string SessionSummary => Session is null
        ? string.Empty
        : $"会话 #{Session.Id}  ·  状态 {StatusLabel(Session.Status)}  ·  深度 {Session.DepthLayer}/{Session.ProfileDepthTarget}  ·  重试 {Session.RetryCount}";

    public string ProblemStatement => Session?.Problem?.StatementMarkdown ?? string.Empty;
    public string ProblemTitle => Session?.Problem?.Title ?? string.Empty;
    public string SamplesText => FormatSamples(Session?.Problem?.Samples);

    public async Task LoadAsync()
    {
        ClearError();
        IsBusy = true;
        try
        {
            var book = await _api.GetBugMineErrorBookAsync();
            ErrorBook.Clear();
            foreach (var item in book)
            {
                ErrorBook.Add(item);
            }

            var echoes = await _api.GetBugMineEchoesAsync();
            Echoes.Clear();
            foreach (var echo in echoes)
            {
                Echoes.Add(echo);
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
    private Task RefreshAsync() => LoadAsync();

    [RelayCommand]
    private async Task OpenEchoAsync(BugMineEchoCandidateDto? candidate)
    {
        if (candidate is null)
        {
            return;
        }

        ClearError();
        IsBusy = true;
        try
        {
            ApplySession(await _api.OpenBugMineEchoAsync(candidate.KcCode));
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
    private async Task OpenErrorAsync(BugMineErrorBookItemDto? item)
    {
        if (item?.SessionId is not { } sessionId)
        {
            return;
        }

        ClearError();
        IsBusy = true;
        try
        {
            ApplySession(await _api.GetBugMineSessionAsync(sessionId));
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
    private async Task RevealAsync(BugMineCardItem? card)
    {
        if (card is null || Session?.Id is not { } sessionId)
        {
            return;
        }

        ClearError();
        IsBusy = true;
        try
        {
            await _api.RevealBugMineCardAsync(sessionId, card.CardId);
            ApplySession(await _api.GetBugMineSessionAsync(sessionId));
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
    private Task RetrySubmitAsync() => SubmitAsync(isVariant: false);

    [RelayCommand]
    private Task VariantSubmitAsync() => SubmitAsync(isVariant: true);

    private async Task SubmitAsync(bool isVariant)
    {
        if (Session?.Id is not { } sessionId)
        {
            return;
        }
        if (string.IsNullOrWhiteSpace(RetryCode))
        {
            ErrorMessage = "代码不能为空";
            return;
        }

        ClearError();
        Verdict = null;
        JudgeSummary = null;
        var progress = new Progress<string>(s => SubmitStatus = s);
        IsBusy = true;
        try
        {
            SubmitStatus = "提交中…";
            var created = isVariant
                ? await _api.BugMineVariantSubmitAsync(sessionId, RetryCode)
                : await _api.BugMineRetrySubmitAsync(sessionId, RetryCode);

            var result = await _api.PollSubmissionAsync(created.Id, progress);
            Verdict = result.JudgeResult?.Verdict ?? result.Submission.Status;
            JudgeSummary = result.JudgeResult is { } judge
                ? $"{judge.Verdict}  ·  通过 {judge.PassedCases}/{judge.TotalCases}"
                    + (string.IsNullOrWhiteSpace(judge.FailureReason) ? string.Empty : $"  ·  {judge.FailureReason}")
                : $"状态：{result.Submission.Status}";

            // The submit advances depth / mastery server-side; re-pull the session to reflect it.
            ApplySession(await _api.GetBugMineSessionAsync(sessionId));
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
    private async Task LoadVariantAsync()
    {
        if (Session?.VariantProblem?.Id is not { } variantId)
        {
            return;
        }

        ClearError();
        IsBusy = true;
        try
        {
            VariantDetail = await _api.GetPublicProblemAsync(variantId);
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
    private Task CloseResolvedAsync() => CloseAsync("RESOLVED");

    [RelayCommand]
    private Task CloseAbandonedAsync() => CloseAsync("ABANDONED");

    private async Task CloseAsync(string status)
    {
        if (Session?.Id is not { } sessionId)
        {
            return;
        }

        ClearError();
        IsBusy = true;
        try
        {
            await _api.CloseBugMineSessionAsync(sessionId, status);
            BackToList();
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

    [RelayCommand]
    private void BackToList()
    {
        Session = null;
        VariantDetail = null;
        Verdict = null;
        JudgeSummary = null;
        SubmitStatus = null;
        DepthSteps.Clear();
        Cards.Clear();
    }

    private void ApplySession(BugMineSessionDto session)
    {
        Session = session;
        VariantDetail = null;

        DepthSteps.Clear();
        foreach (var step in session.DepthSteps ?? Array.Empty<BugMineDepthStepDto>())
        {
            DepthSteps.Add(step);
        }

        Cards.Clear();
        foreach (var card in session.Cards ?? Array.Empty<BugMineCardDto>())
        {
            Cards.Add(new BugMineCardItem(
                card.Id ?? 0,
                CardTypeLabel(card.CardType),
                card.Revealed,
                card.Revealed ? CardBody(card.Payload) : "解锁后可见此卡片内容。"));
        }

        OnPropertyChanged(nameof(HasSession));
        OnPropertyChanged(nameof(HasVariant));
        OnPropertyChanged(nameof(SessionSummary));
        OnPropertyChanged(nameof(ProblemStatement));
        OnPropertyChanged(nameof(ProblemTitle));
        OnPropertyChanged(nameof(SamplesText));
    }

    partial void OnSessionChanged(BugMineSessionDto? value) => OnPropertyChanged(nameof(HasSession));

    private static string StatusLabel(string? status) => status switch
    {
        "OPEN" => "进行中",
        "RESOLVED" => "已攻克",
        "ABANDONED" => "已放弃",
        _ => status ?? "—",
    };

    private static string CardTypeLabel(string? cardType) => cardType switch
    {
        "COUNTER_EXAMPLE" => "反例",
        "VARIABLE_TRACE" => "变量追踪",
        "WRONG_ASSUMPTION" => "错误假设",
        "HINT_LAYER_1" => "提示 · 第 1 层",
        "HINT_LAYER_2" => "提示 · 第 2 层",
        "HINT_LAYER_3" => "提示 · 第 3 层",
        _ => cardType ?? "卡片",
    };

    private static string CardBody(JsonElement? payload)
    {
        if (payload is not { } element
            || element.ValueKind is JsonValueKind.Null or JsonValueKind.Undefined)
        {
            return string.Empty;
        }

        if (element.ValueKind == JsonValueKind.String)
        {
            return element.GetString() ?? string.Empty;
        }

        if (element.ValueKind == JsonValueKind.Object)
        {
            foreach (var key in new[] { "text", "body", "content", "markdown", "hint", "message", "explanation" })
            {
                foreach (var property in element.EnumerateObject())
                {
                    if (string.Equals(property.Name, key, StringComparison.OrdinalIgnoreCase)
                        && property.Value.ValueKind == JsonValueKind.String)
                    {
                        return property.Value.GetString() ?? string.Empty;
                    }
                }
            }
        }

        try
        {
            return JsonSerializer.Serialize(element, PrettyJson);
        }
        catch
        {
            return element.ToString();
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

/// <summary>Display projection of a <see cref="BugMineCardDto"/> for the reveal-gated card list.</summary>
public sealed record BugMineCardItem(long CardId, string TypeLabel, bool Revealed, string Body);
