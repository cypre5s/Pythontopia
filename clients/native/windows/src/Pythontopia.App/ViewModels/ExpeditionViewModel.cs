using System.Collections.ObjectModel;
using System.Globalization;
using System.IO;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;
using Pythontopia.Core.Expedition;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives 无尽远征 (endless expedition) — the Roguelike that replaces the retired arena grid.
/// The knowledge-frontier selector (mastery + profile) generates a branching map; the player
/// picks a path and battles real graded problems (reusing the boss problem/attempt endpoints).
/// All game rules live in the pure <see cref="RogueRunEngine"/>; this view-model performs the I/O
/// (frontier select, problem fetch, judging, backend run persistence) and projects state for XAML.
/// </summary>
public sealed partial class ExpeditionViewModel : ViewModelBase
{
    private readonly IApiClient _api;
    private readonly RogueRunEngine _engine = new();
    private readonly string _dailyFilePath;

    private BossProblemDto? _problem;
    private DateTimeOffset _problemAt;

    public ObservableCollection<ExpeditionLayerVm> Layers { get; } = new();
    public IReadOnlyList<ExpeditionFocusOption> FocusOptions { get; }

    // ── pixel tower (像素远征塔) geometry; rebuilt on every map refresh ──
    public List<ExpeditionTowerNodeVm> TowerNodes { get; private set; } = new();
    public List<ExpeditionTowerEdgeVm> TowerEdges { get; private set; } = new();

    /// <summary>Raised when the tower geometry changes so the page can redraw its canvas.</summary>
    public event EventHandler? TowerInvalidated;

    [ObservableProperty] private double _towerWidth;
    [ObservableProperty] private double _towerHeight;
    [ObservableProperty] private bool _mapIsPixel = true;
    [ObservableProperty] private bool _mapIsList;

    [ObservableProperty] private bool _isIdle = true;
    [ObservableProperty] private bool _isMap;
    [ObservableProperty] private bool _isEvent;
    [ObservableProperty] private bool _isChallenge;
    [ObservableProperty] private bool _isEnded;
    [ObservableProperty] private bool _isCleared;

    [ObservableProperty] private bool _statsVisible;
    [ObservableProperty] private string _hpText = string.Empty;
    [ObservableProperty] private string _momentumText = "×0";
    [ObservableProperty] private string _scoreText = "0";
    [ObservableProperty] private string _depthText = "0";
    [ObservableProperty] private bool _relicsVisible;
    [ObservableProperty] private string _relicsText = string.Empty;

    [ObservableProperty] private ExpeditionFocusOption? _selectedFocus;
    [ObservableProperty] private bool _dailyAvailable = true;
    [ObservableProperty] private string _dailyButtonText = "☀ 今日挑战 · 弱项";

    [ObservableProperty] private string _eventTitle = string.Empty;
    [ObservableProperty] private string _eventText = string.Empty;

    [ObservableProperty] private string _challengeKindChip = string.Empty;
    [ObservableProperty] private string _kcChip = string.Empty;
    [ObservableProperty] private string _difficultyChip = string.Empty;
    [ObservableProperty] private bool _bossPhaseVisible;
    [ObservableProperty] private string _bossPhaseChip = string.Empty;
    [ObservableProperty] private string _timerChip = "⏱ 0s";
    [ObservableProperty] private string _statement = string.Empty;
    [ObservableProperty] private string _hints = string.Empty;
    [ObservableProperty] private bool _hintsVisible;
    [ObservableProperty] private string _code = string.Empty;
    [ObservableProperty] private bool _loading;
    [ObservableProperty] private bool _problemPanelVisible;

    [ObservableProperty] private bool _resultVisible;
    [ObservableProperty] private string _resultText = string.Empty;
    [ObservableProperty] private bool _resultIsPass;
    [ObservableProperty] private bool _failHpVisible;
    [ObservableProperty] private string _failHpText = string.Empty;

    [ObservableProperty] private bool _showSubmit;
    [ObservableProperty] private bool _showContinue;
    [ObservableProperty] private bool _showRetry;
    [ObservableProperty] private bool _showOver;
    [ObservableProperty] private string _continueLabel = "继续闯关";

    [ObservableProperty] private string _endTitle = string.Empty;
    [ObservableProperty] private string _endStatsText = string.Empty;
    [ObservableProperty] private string _starMessage = string.Empty;
    [ObservableProperty] private bool _starVisible;

    [ObservableProperty] private int _elapsedSec;

    public ExpeditionViewModel(IApiClient api)
    {
        _api = api;
        _dailyFilePath = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Pythontopia",
            "expedition-daily.txt");

        var options = new List<ExpeditionFocusOption> { new(null, "全局自适应（按画像）") };
        foreach (var boss in BossCatalog.Bosses)
        {
            options.Add(new ExpeditionFocusOption(boss.Chapter, $"第 {boss.Chapter} 章 · {boss.Name}"));
        }
        FocusOptions = options;
        _selectedFocus = options[0];

        RefreshAll();
    }

    // ── lifecycle ──

    /// <summary>Called by the page on navigate: resume an active backend run when idle (cross-device).</summary>
    public async Task OnNavigatedToAsync()
    {
        if (_engine.Phase == RogueRunPhase.Idle)
        {
            await TryResumeAsync().ConfigureAwait(true);
        }
        RefreshAll();
    }

    /// <summary>Driven by a 1s page timer to advance the challenge clock.</summary>
    public void TickSecond()
    {
        if (_engine.Phase == RogueRunPhase.Challenge && !Loading && _problem is not null && !ResultVisible)
        {
            ElapsedSec++;
            TimerChip = $"⏱ {ElapsedSec}s";
        }
    }

    // ── commands ──

    [RelayCommand]
    private void ShowPixelMap()
    {
        MapIsPixel = true;
        MapIsList = false;
        TowerInvalidated?.Invoke(this, EventArgs.Empty);
    }

    [RelayCommand]
    private void ShowListMap()
    {
        MapIsPixel = false;
        MapIsList = true;
    }

    [RelayCommand]
    private Task Start() => StartRunAsync(fresh: false);

    [RelayCommand]
    private Task Restart() => StartRunAsync(fresh: true);

    [RelayCommand]
    private async Task Daily()
    {
        if (!DailyAvailable)
        {
            ErrorMessage = "今日挑战已完成，明天再来";
            return;
        }
        if (IsBusy)
        {
            return;
        }
        ClearError();
        ResetRunMessages();
        IsBusy = true;
        try
        {
            var seed = int.Parse(TodayKey(), CultureInfo.InvariantCulture);
            var rng = MakeLcg(seed);
            var picks = await SafeFrontierAsync(null).ConfigureAwait(true);
            var frontier = await BuildLocalFrontierAsync().ConfigureAwait(true);
            var resolver = new ChallengeResolver(picks, frontier, null, rng);
            var map = RogueMapGenerator.Generate(resolver.Resolve, rng);
            _engine.StartRun(map, null, daily: true);
            await PersistAsync().ConfigureAwait(true);
        }
        catch (Exception ex)
        {
            SetError(ex);
        }
        finally
        {
            IsBusy = false;
            RefreshAll();
        }
    }

    [RelayCommand]
    private async Task Abandon()
    {
        _engine.Abort();
        RefreshAll();
        await SafeDeleteRunAsync().ConfigureAwait(true);
    }

    [RelayCommand]
    private async Task ChooseEvent(string choice)
    {
        if (IsBusy)
        {
            return;
        }
        var outcome = _engine.ChooseEvent(choice);
        if (outcome.Action == EnterAction.StartChallenge)
        {
            RefreshAll();
            await BeginChallengeAsync().ConfigureAwait(true);
            await PersistAsync().ConfigureAwait(true);
        }
        else
        {
            await PersistAsync().ConfigureAwait(true);
            RefreshAll();
        }
    }

    [RelayCommand]
    private async Task Submit()
    {
        var node = _engine.ActiveNode;
        if (node is null || _problem?.ProblemId is not { } problemId || IsBusy)
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
        Loading = true;
        RefreshChallenge();
        try
        {
            var elapsed = Math.Max(0, (int)(DateTimeOffset.UtcNow - _problemAt).TotalSeconds);
            var result = await _api.SubmitBossAttemptAsync(_problem.Chapter, _problem.Stage, problemId, Code, elapsed)
                .ConfigureAwait(true);

            var outcome = _engine.ApplyJudge(result.Passed);
            ResultIsPass = result.Passed;
            ResultText = (result.Passed ? "答对！" : "答错。")
                + $" 用例 {result.PassedTestCases}/{result.TotalTestCases}"
                + (string.IsNullOrWhiteSpace(result.FailureFirstHint) ? string.Empty : " · " + result.FailureFirstHint);
            ResultVisible = true;

            await MaybeReportStarAsync().ConfigureAwait(true);

            if (outcome.BossAdvanced)
            {
                // 击穿一阶段，连续作战：清结果，立刻进入下一阶段出题。
                ResultVisible = false;
                await PersistAsync().ConfigureAwait(true);
                IsBusy = false; // BeginChallenge manages its own loading state
                await BeginChallengeAsync().ConfigureAwait(true);
                return;
            }

            await PersistAsync().ConfigureAwait(true);
        }
        catch (Exception ex)
        {
            SetError(ex);
        }
        finally
        {
            IsBusy = false;
            Loading = false;
            RefreshAll();
        }
    }

    [RelayCommand]
    private async Task Continue()
    {
        _engine.ContinueAfterChallenge();
        ResultVisible = false;
        if (_engine.Phase is RogueRunPhase.Over or RogueRunPhase.Cleared)
        {
            if (_engine.IsDaily)
            {
                SaveDailyDate(TodayDate());
            }
            await SafeDeleteRunAsync().ConfigureAwait(true);
        }
        else
        {
            await PersistAsync().ConfigureAwait(true);
        }
        RefreshAll();
    }

    [RelayCommand]
    private async Task Retry()
    {
        ResultVisible = false;
        RefreshChallenge();
        await BeginChallengeAsync().ConfigureAwait(true);
    }

    // ── node entry (invoked by ExpeditionNodeVm command delegate) ──

    private async Task EnterNodeAsync(string id)
    {
        if (IsBusy)
        {
            return;
        }
        var outcome = _engine.EnterNode(id);
        switch (outcome.Action)
        {
            case EnterAction.StartChallenge:
                RefreshAll();
                await BeginChallengeAsync().ConfigureAwait(true);
                await PersistAsync().ConfigureAwait(true);
                break;
            case EnterAction.EnteredEvent:
            case EnterAction.RestResolved:
            case EnterAction.ChestResolved:
                await PersistAsync().ConfigureAwait(true);
                RefreshAll();
                break;
            default:
                break;
        }
    }

    // ── helpers ──

    private async Task StartRunAsync(bool fresh)
    {
        if (IsBusy)
        {
            return;
        }
        ClearError();
        ResetRunMessages();
        IsBusy = true;
        try
        {
            if (!fresh && await TryResumeAsync().ConfigureAwait(true))
            {
                return;
            }
            var focus = SelectedFocus?.Chapter;
            var picks = await SafeFrontierAsync(focus).ConfigureAwait(true);
            var frontier = await BuildLocalFrontierAsync().ConfigureAwait(true);
            var resolver = new ChallengeResolver(picks, frontier, focus);
            var map = RogueMapGenerator.Generate(resolver.Resolve);
            _engine.StartRun(map, focus, daily: false);
            await PersistAsync().ConfigureAwait(true);
        }
        catch (Exception ex)
        {
            SetError(ex);
        }
        finally
        {
            IsBusy = false;
            RefreshAll();
        }
    }

    private async Task BeginChallengeAsync()
    {
        var node = _engine.ActiveNode;
        if (node is null)
        {
            return;
        }
        ClearError();
        Loading = true;
        ResultVisible = false;
        Code = string.Empty;
        ElapsedSec = 0;
        _problem = null;
        RefreshChallenge();
        try
        {
            var chapter = RogueConfig.KcChapter(node.KcCode);
            var stage = Math.Clamp(node.Difficulty, 1, 4);
            _problem = await _api.RequestBossProblemAsync(chapter, stage, node.KcCode, node.Difficulty).ConfigureAwait(true);
            _problemAt = DateTimeOffset.UtcNow;
            Statement = string.IsNullOrWhiteSpace(_problem.Statement) ? "(无题面)" : _problem.Statement!;
            Hints = _problem.Hints is { Count: > 0 } hints ? "提示：" + string.Join(" / ", hints) : string.Empty;
        }
        catch (Exception ex)
        {
            SetError(ex);
        }
        finally
        {
            Loading = false;
            RefreshChallenge();
        }
    }

    private async Task MaybeReportStarAsync()
    {
        if (!_engine.TryBeginStarReport(out var chapter, out var correct, out var total, out var streak))
        {
            return;
        }
        try
        {
            var result = await _api.SubmitEndlessSummaryAsync(chapter, correct, total, streak).ConfigureAwait(true);
            if (result.StarAwarded)
            {
                StarMessage = $"第 {chapter} 章远征补星 +1 ★";
                StarVisible = true;
            }
        }
        catch
        {
            _engine.ResetStarReport();
        }
    }

    private async Task<bool> TryResumeAsync()
    {
        try
        {
            var snap = await _api.GetExpeditionRunAsync().ConfigureAwait(true);
            if (snap is { Nodes.Count: > 0 } && RogueRunEngine.IsResumablePhase(snap.Phase))
            {
                _engine.ApplySnapshot(snap);
                return true;
            }
        }
        catch
        {
            // backend unreachable: stay idle, the player can start manually
        }
        return false;
    }

    private async Task<IReadOnlyList<FrontierPick>> SafeFrontierAsync(int? focus)
    {
        var recent = _engine.History
            .Select(h => h.KcCode)
            .Where(k => !string.IsNullOrEmpty(k))
            .Select(k => k!)
            .Take(12)
            .ToList();
        try
        {
            return await _api.SelectFrontierAsync(new FrontierSelectRequest(focus, recent, Array.Empty<string>(), 28))
                .ConfigureAwait(true);
        }
        catch
        {
            return Array.Empty<FrontierPick>();
        }
    }

    private async Task<KcFrontier> BuildLocalFrontierAsync()
    {
        try
        {
            var mastery = await _api.GetMasteryAsync().ConfigureAwait(true);
            var map = mastery.ToDictionary(m => m.KcCode, m => (double)m.MasteryScore);
            return new KcFrontier(map);
        }
        catch
        {
            return new KcFrontier();
        }
    }

    private async Task PersistAsync()
    {
        if (_engine.Phase == RogueRunPhase.Idle)
        {
            return;
        }
        try
        {
            await _api.SaveExpeditionRunAsync(_engine.Snapshot()).ConfigureAwait(true);
        }
        catch
        {
            // best-effort; the run continues in-memory even if the backend is unreachable
        }
    }

    private async Task SafeDeleteRunAsync()
    {
        try
        {
            await _api.DeleteExpeditionRunAsync().ConfigureAwait(true);
        }
        catch
        {
            // best-effort
        }
    }

    private void ResetRunMessages()
    {
        StarMessage = string.Empty;
        StarVisible = false;
    }

    // ── projection ──

    private void RefreshAll()
    {
        var p = _engine.Phase;
        IsIdle = p == RogueRunPhase.Idle;
        IsMap = p == RogueRunPhase.Map;
        IsEvent = p == RogueRunPhase.Event;
        IsChallenge = p == RogueRunPhase.Challenge;
        IsEnded = p is RogueRunPhase.Cleared or RogueRunPhase.Over;
        IsCleared = p == RogueRunPhase.Cleared;

        RefreshStats();
        RefreshMap();
        RefreshEvent();
        RefreshChallenge();
        RefreshEnd();

        DailyAvailable = ComputeDailyAvailable();
        DailyButtonText = DailyAvailable ? "☀ 今日挑战 · 弱项" : "今日挑战已完成";
    }

    private void RefreshStats()
    {
        StatsVisible = _engine.Phase != RogueRunPhase.Idle;
        var hp = Math.Max(0, _engine.Hp);
        var empty = Math.Max(0, _engine.MaxHp - _engine.Hp);
        HpText = new string('♥', hp) + new string('♡', empty);
        MomentumText = $"×{_engine.Momentum}";
        ScoreText = _engine.Score.ToString(CultureInfo.InvariantCulture);
        DepthText = _engine.Depth.ToString(CultureInfo.InvariantCulture);
        RelicsVisible = _engine.Phase != RogueRunPhase.Idle && _engine.Relics.Count > 0;
        RelicsText = string.Join(" ", _engine.Relics.Select(RogueRelics.Icon));
    }

    private void RefreshMap()
    {
        Layers.Clear();
        TowerNodes = new List<ExpeditionTowerNodeVm>();
        TowerEdges = new List<ExpeditionTowerEdgeVm>();

        if (_engine.Phase == RogueRunPhase.Map)
        {
            var byLayer = _engine.NodesByLayer();

            // List view rows.
            for (var i = 0; i < byLayer.Count; i++)
            {
                var tag = i == _engine.Layers - 1 ? "顶层" : $"第 {i + 1} 层";
                var nodes = byLayer[i]
                    .Select(n => new ExpeditionNodeVm(n, id => _ = EnterNodeAsync(id)))
                    .ToList();
                Layers.Add(new ExpeditionLayerVm(tag, nodes));
            }

            BuildTower(byLayer);
        }

        TowerInvalidated?.Invoke(this, EventArgs.Empty);
    }

    /// <summary>Lay out the branching DAG as a climbable tower: layer 0 at the bottom, boss at the top.</summary>
    private void BuildTower(IReadOnlyList<IReadOnlyList<Pythontopia.Core.Expedition.RogueNode>> byLayer)
    {
        const double rowH = 92, nodeW = 142, nodeH = 56, padY = 12, canvasW = 760;
        var layers = _engine.Layers;
        TowerWidth = canvasW;
        TowerHeight = (layers * rowH) + (padY * 2);

        var centers = new Dictionary<string, (double Cx, double TopY, double BottomY)>();
        for (var layer = 0; layer < byLayer.Count; layer++)
        {
            var row = byLayer[layer];
            var yIndex = layers - 1 - layer; // boss (last layer) lands at the top
            var y = (yIndex * rowH) + padY;
            for (var c = 0; c < row.Count; c++)
            {
                var node = row[c];
                var cx = (c + 1.0) / (row.Count + 1.0) * canvasW;
                var x = cx - (nodeW / 2);
                TowerNodes.Add(new ExpeditionTowerNodeVm(node, x, y, nodeW, nodeH, id => _ = EnterNodeAsync(id)));
                centers[node.Id] = (cx, y, y + nodeH);
            }
        }

        foreach (var node in _engine.Nodes)
        {
            if (!centers.TryGetValue(node.Id, out var from))
            {
                continue;
            }
            foreach (var nid in node.Next)
            {
                if (!centers.TryGetValue(nid, out var to))
                {
                    continue;
                }
                var target = _engine.NodeById(nid);
                var open = node.State == RogueNodeState.Cleared
                    && target is not null
                    && (target.State == RogueNodeState.Cleared || target.State == RogueNodeState.Available);
                // Edge goes from this node's top up to the target's bottom (target sits above).
                TowerEdges.Add(new ExpeditionTowerEdgeVm(from.Cx, from.TopY, to.Cx, to.BottomY, open));
            }
        }
    }

    private void RefreshEvent()
    {
        if (_engine.Phase == RogueRunPhase.Event && _engine.ActiveNode is { } node)
        {
            EventTitle = $"概念事件 · {KcNames.DisplayName(node.KcCode)}";
            EventText = _engine.ActiveEventText;
        }
    }

    private void RefreshChallenge()
    {
        if (_engine.ActiveNode is { } node && _engine.Phase == RogueRunPhase.Challenge)
        {
            ChallengeKindChip = $"{RogueConfig.KindIcon(node.Kind)} {RogueConfig.KindLabel(node.Kind)}";
            KcChip = KcNames.DisplayName(node.KcCode);
            DifficultyChip = $"难度 {node.Difficulty} / 5";
            BossPhaseVisible = node.Kind == RogueNodeKind.Boss;
            BossPhaseChip = $"阶段 {_engine.BossPhase} / {_engine.BossPhasesTotal}";
        }
        TimerChip = $"⏱ {ElapsedSec}s";
        HintsVisible = Hints.Length > 0;
        ProblemPanelVisible = _engine.Phase == RogueRunPhase.Challenge && _problem is not null && !Loading;

        var hasResult = ResultVisible;
        ShowSubmit = _problem is not null && !hasResult && _engine.Phase == RogueRunPhase.Challenge && !Loading;
        ShowContinue = hasResult && ResultIsPass;
        ShowRetry = hasResult && !ResultIsPass && _engine.Hp > 0;
        ShowOver = hasResult && !ResultIsPass && _engine.Hp <= 0;
        ContinueLabel = _engine.ActiveNode?.Kind == RogueNodeKind.Boss ? "通关！" : "继续闯关";
        FailHpVisible = hasResult && !ResultIsPass;
        FailHpText = $"答错 · 体力 {_engine.Hp}/{_engine.MaxHp}";
    }

    private void RefreshEnd()
    {
        if (!IsEnded)
        {
            return;
        }
        var chapter = _engine.FocusChapter ?? _problem?.Chapter ?? 1;
        EndTitle = _engine.Phase == RogueRunPhase.Cleared
            ? $"击穿知识闸 · 战胜 {BossCatalog.NameForChapter(chapter)}！"
            : "本局远征结束";
        EndStatsText =
            $"深度 {_engine.Depth} 层 · 得分 {_engine.Score} · 最高气势 ×{_engine.BestMomentum} · 答对 {_engine.PassedCount}/{_engine.Attempts}";
    }

    // ── daily gate persistence (a tiny local file, mirroring the web's localStorage gate) ──

    private bool ComputeDailyAvailable() => LoadDailyDate() != TodayDate();

    private static string TodayDate() => DateTime.UtcNow.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);

    private static string TodayKey() => DateTime.UtcNow.ToString("yyyyMMdd", CultureInfo.InvariantCulture);

    private string? LoadDailyDate()
    {
        try
        {
            return File.Exists(_dailyFilePath) ? File.ReadAllText(_dailyFilePath).Trim() : null;
        }
        catch
        {
            return null;
        }
    }

    private void SaveDailyDate(string date)
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(_dailyFilePath)!);
            File.WriteAllText(_dailyFilePath, date);
        }
        catch
        {
            // privacy-restricted FS: skip persistence, daily gate falls back to in-session only
        }
    }

    /// <summary>Deterministic LCG so the daily challenge yields a stable map for the date seed.</summary>
    private static Func<double> MakeLcg(int seed)
    {
        var s = (uint)seed;
        if (s == 0)
        {
            s = 1;
        }
        return () =>
        {
            s = unchecked((s * 1664525u) + 1013904223u);
            return s / (double)uint.MaxValue;
        };
    }
}
