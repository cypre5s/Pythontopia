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
/// 百晓工坊: classifies a one-line message via POST /api/intent/route then dispatches —
/// ASK → tutor answer SSE, REVIEW → Bug 矿洞, the 6 resource intents → workshop generation.
/// Also exposes the optional 学习总管 (orchestrator) turn stream.
/// </summary>
public partial class WorkshopViewModel : ViewModelBase
{
    private static readonly JsonSerializerOptions PrettyJson = new()
    {
        WriteIndented = true,
        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
    };

    private static readonly HashSet<string> ResourceIntents = new(StringComparer.OrdinalIgnoreCase)
    {
        "HANDOUT", "MINDMAP", "PRACTICE", "CODE_LAB", "READING", "STORYBOARD",
    };

    private readonly IApiClient _api;
    private readonly List<IntentCourseKc> _courseKcs = new();

    [ObservableProperty]
    private string _message = string.Empty;

    [ObservableProperty]
    private string? _manualKcInput;

    [ObservableProperty]
    private IntentRouteResponse? _intent;

    [ObservableProperty]
    private string? _answer;

    [ObservableProperty]
    private string? _traceLog;

    [ObservableProperty]
    private string? _dispatchNote;

    [ObservableProperty]
    private bool _showReviewButton;

    [ObservableProperty]
    private LearningResourceDto? _resource;

    [ObservableProperty]
    private string? _resourceContent;

    public ObservableCollection<string> AnchoredKcs { get; } = new();

    /// <summary>Raised when the routed intent is REVIEW and the user opts to open Bug 矿洞.</summary>
    public event EventHandler? ReviewRequested;

    public WorkshopViewModel(IApiClient api) => _api = api;

    public bool HasIntent => Intent is not null;
    public bool HasAnswer => !string.IsNullOrEmpty(Answer);
    public bool HasResource => Resource is not null;
    public bool HasTrace => !string.IsNullOrEmpty(TraceLog);

    public string IntentSummary => Intent is null
        ? string.Empty
        : $"意图：{IntentLabel(Intent.Intent)}  ·  置信度 {Math.Round(Intent.Confidence * 100)}%"
            + (string.IsNullOrWhiteSpace(Intent.FocusHint) ? string.Empty : $"  ·  {Intent.FocusHint}");

    public string ResourceMeta => Resource is null
        ? string.Empty
        : $"{Resource.Title}  ·  {Resource.Type}  ·  接地 {Resource.GroundingStatus}  ·  安全 {Resource.SafetyStatus}";

    public async Task LoadAsync()
    {
        if (_courseKcs.Count > 0)
        {
            return;
        }

        try
        {
            var kcs = await _api.GetKcsAsync();
            _courseKcs.Clear();
            foreach (var kc in kcs)
            {
                if (!string.IsNullOrWhiteSpace(kc.KcCode))
                {
                    _courseKcs.Add(new IntentCourseKc(kc.KcCode, kc.KcName));
                }
            }
        }
        catch (Exception ex)
        {
            // Routing still works without the catalogue; surface the issue but don't block input.
            SetError(ex);
        }
    }

    [RelayCommand]
    private async Task SendAsync()
    {
        if (string.IsNullOrWhiteSpace(Message))
        {
            ErrorMessage = "请输入一句话";
            return;
        }

        ClearError();
        ResetOutputs();
        IsBusy = true;
        try
        {
            var request = new IntentRouteRequest(
                Message.Trim(),
                ManualKcCodes: ParseManualKcs(),
                CourseKcs: _courseKcs.Count > 0 ? _courseKcs : null);
            Intent = await _api.RouteIntentAsync(request);

            AnchoredKcs.Clear();
            foreach (var code in Intent.KcCodes ?? Array.Empty<string>())
            {
                AnchoredKcs.Add(code);
            }
            RaiseIntentChanged();

            await DispatchAsync(Intent);
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

    private async Task DispatchAsync(IntentRouteResponse intent)
    {
        var kind = intent.Intent?.ToUpperInvariant();
        switch (kind)
        {
            case "ASK":
                await StreamTutorAsync(intent);
                break;
            case "REVIEW":
                ShowReviewButton = true;
                DispatchNote = "记忆矿脉回声复习：建议前往 Bug 矿洞，按 FSRS 到期知识点复盘。";
                break;
            case "VIDEO":
                DispatchNote = "VIDEO：建议到课程页观看任务点视频；B 站精选讲解检索暂未在桌面端集成。";
                break;
            default:
                if (kind is not null && ResourceIntents.Contains(kind))
                {
                    await GenerateResourceAsync(intent, kind);
                }
                else
                {
                    DispatchNote = $"未知意图：{intent.Intent}";
                }
                break;
        }
    }

    [RelayCommand]
    private void OpenBugMine() => ReviewRequested?.Invoke(this, EventArgs.Empty);

    [RelayCommand]
    private async Task OrchestrateAsync()
    {
        if (string.IsNullOrWhiteSpace(Message))
        {
            ErrorMessage = "请输入一句话";
            return;
        }

        ClearError();
        Answer = string.Empty;
        TraceLog = string.Empty;
        IsBusy = true;
        try
        {
            var request = new OrchestratorTurnRequest(
                Message.Trim(),
                CourseKcCodes: _courseKcs.Count > 0 ? _courseKcs.Select(k => k.Code).ToList() : null);

            string? lastResult = null;
            await foreach (var evt in _api.StreamOrchestratorTurnAsync(request))
            {
                switch (evt.Event.ToLowerInvariant())
                {
                    case "token":
                        AppendDelta(evt.Data);
                        break;
                    case "result":
                        lastResult = evt.Data;
                        break;
                    case "plan":
                    case "trace":
                        AppendTrace(evt);
                        break;
                }
            }

            if (string.IsNullOrEmpty(Answer) && lastResult is not null)
            {
                Answer = ExtractText(lastResult);
            }
            if (string.IsNullOrEmpty(Answer))
            {
                Answer = "(学习总管未返回文本)";
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

    private async Task StreamTutorAsync(IntentRouteResponse intent)
    {
        Answer = string.Empty;
        var request = new TutorAskRequest(Message.Trim(), KcCodes: intent.KcCodes);
        await foreach (var payload in _api.AskTutorStreamAsync(request))
        {
            AppendDelta(payload);
        }
        if (string.IsNullOrEmpty(Answer))
        {
            Answer = "(导师未返回文本)";
        }
    }

    private async Task GenerateResourceAsync(IntentRouteResponse intent, string type)
    {
        var kcCodes = (intent.KcCodes is { Count: > 0 } ? intent.KcCodes : ParseManualKcs())?.ToList();
        if (kcCodes is null || kcCodes.Count == 0)
        {
            DispatchNote = "资源生成需要至少一个知识点：请在下方知识点编码框填写（如 PY43-KC-001），或在描述中点明主题。";
            return;
        }

        DispatchNote = $"正在生成 {IntentLabel(type)} …";
        var request = new ResourceCreateRequest(kcCodes, type, intent.FocusHint);
        var created = await _api.CreateResourceAsync(request);
        if (created.ResourceId is not { } resourceId)
        {
            DispatchNote = $"资源生成返回状态：{created.Status}（无资源 id）";
            return;
        }

        Resource = await _api.GetResourceAsync(resourceId);
        ResourceContent = RenderResourceContent(Resource);
        DispatchNote = null;
        RaiseResourceChanged();
    }

    private List<string>? ParseManualKcs()
    {
        if (string.IsNullOrWhiteSpace(ManualKcInput))
        {
            return null;
        }

        var codes = ManualKcInput
            .Split(new[] { ',', '，', ' ', '\n', '\r', '\t' }, StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToList();
        return codes.Count > 0 ? codes : null;
    }

    private void AppendDelta(string payload)
    {
        if (string.IsNullOrWhiteSpace(payload))
        {
            return;
        }

        try
        {
            using var doc = JsonDocument.Parse(payload);
            if (doc.RootElement.ValueKind == JsonValueKind.Object
                && doc.RootElement.TryGetProperty("delta", out var delta)
                && delta.ValueKind == JsonValueKind.String)
            {
                Answer += delta.GetString();
            }
        }
        catch (JsonException)
        {
            // Non-JSON token payloads are ignored; tutor/orchestrator deltas are always JSON.
        }
    }

    private void AppendTrace(SseEvent evt)
    {
        var label = evt.Event;
        try
        {
            using var doc = JsonDocument.Parse(evt.Data);
            if (doc.RootElement.ValueKind == JsonValueKind.Object)
            {
                foreach (var key in new[] { "node", "agent", "step", "name", "label", "title" })
                {
                    if (doc.RootElement.TryGetProperty(key, out var v) && v.ValueKind == JsonValueKind.String)
                    {
                        label = $"{evt.Event} · {v.GetString()}";
                        break;
                    }
                }
            }
        }
        catch (JsonException)
        {
            // keep the plain event name
        }

        TraceLog = string.IsNullOrEmpty(TraceLog) ? $"› {label}" : $"{TraceLog}\n› {label}";
    }

    private static string ExtractText(string payload)
    {
        try
        {
            using var doc = JsonDocument.Parse(payload);
            if (doc.RootElement.ValueKind == JsonValueKind.String)
            {
                return doc.RootElement.GetString() ?? string.Empty;
            }
            if (doc.RootElement.ValueKind == JsonValueKind.Object)
            {
                foreach (var key in new[] { "answer", "text", "markdown", "summary", "message", "reply", "content" })
                {
                    if (doc.RootElement.TryGetProperty(key, out var v) && v.ValueKind == JsonValueKind.String)
                    {
                        return v.GetString() ?? string.Empty;
                    }
                }
            }
        }
        catch (JsonException)
        {
            return payload;
        }

        return payload;
    }

    private static string RenderResourceContent(LearningResourceDto resource)
    {
        var summary = string.IsNullOrWhiteSpace(resource.Summary) ? string.Empty : resource.Summary + "\n\n";
        return summary + RenderContent(resource.Content);
    }

    private static string RenderContent(JsonElement? content)
    {
        if (content is not { } element
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
            foreach (var key in new[] { "markdown", "text", "body", "content", "mermaid" })
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

    private void ResetOutputs()
    {
        Answer = null;
        TraceLog = null;
        DispatchNote = null;
        ShowReviewButton = false;
        Resource = null;
        ResourceContent = null;
        Intent = null;
        AnchoredKcs.Clear();
        RaiseIntentChanged();
        RaiseResourceChanged();
    }

    private void RaiseIntentChanged()
    {
        OnPropertyChanged(nameof(HasIntent));
        OnPropertyChanged(nameof(IntentSummary));
    }

    private void RaiseResourceChanged()
    {
        OnPropertyChanged(nameof(HasResource));
        OnPropertyChanged(nameof(ResourceMeta));
    }

    partial void OnAnswerChanged(string? value) => OnPropertyChanged(nameof(HasAnswer));

    partial void OnTraceLogChanged(string? value) => OnPropertyChanged(nameof(HasTrace));

    private static string IntentLabel(string? intent) => intent?.ToUpperInvariant() switch
    {
        "ASK" => "答疑",
        "VIDEO" => "视频讲解",
        "REVIEW" => "回声复盘",
        "HANDOUT" => "讲义",
        "MINDMAP" => "思维导图",
        "PRACTICE" => "练习题",
        "CODE_LAB" => "代码实验",
        "READING" => "拓展阅读",
        "STORYBOARD" => "故事板",
        _ => intent ?? "—",
    };
}
