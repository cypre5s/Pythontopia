using Pythontopia.Core.Dto;

namespace Pythontopia.Core.Expedition;

/// <summary>EnterNode / ChooseEvent 之后调用方要执行的动作。</summary>
public enum EnterAction
{
    /// <summary>非法（节点不可选 / 阶段不对），无变化。</summary>
    Ignored,

    /// <summary>营地即时结算：已回血，仍停在地图。</summary>
    RestResolved,

    /// <summary>宝箱即时结算：已发学习增益（见 <see cref="EnterOutcome.GrantedRelicId"/>），仍停在地图。</summary>
    ChestResolved,

    /// <summary>进入概念事件：需展示 <see cref="RogueRunEngine.ActiveEventText"/> + 三选一。</summary>
    EnteredEvent,

    /// <summary>就地复盘即时结算（事件节点 review）：已回血 + 计分，回到地图。</summary>
    ReviewResolved,

    /// <summary>进入战斗：调用方应为 <see cref="RogueRunEngine.ActiveNode"/> 拉题并判题。</summary>
    StartChallenge,
}

public readonly record struct EnterOutcome(EnterAction Action, string? GrantedRelicId = null, int HealAmount = 0);

/// <summary>ApplyJudge 之后的结果，指导调用方下一步。</summary>
public readonly record struct JudgeOutcome(bool Passed, bool BossAdvanced, string? GrantedRelicId);

/// <summary>
/// 无尽远征 run 状态机（纯逻辑，无 I/O）— 端口自 web Pinia store <c>stores/rogueRun.ts</c> 的玩法规则。
/// 调用方（ViewModel）负责出题 / 判题 / 持久化等 I/O；本引擎只持有并推进游戏状态，便于单测。
/// </summary>
public sealed class RogueRunEngine
{
    private readonly Func<double> _rng;
    private List<RogueNode> _nodes = new();
    private readonly List<string> _relics = new();
    private readonly List<RogueHistoryEntry> _history = new();
    private bool _lastPassed;

    public RogueRunEngine(Func<double>? rng = null) => _rng = rng ?? Random.Shared.NextDouble;

    // ── 状态 ──
    public string Phase { get; private set; } = RogueRunPhase.Idle;
    public int? FocusChapter { get; private set; }
    public IReadOnlyList<RogueNode> Nodes => _nodes;
    public int Layers { get; private set; }
    public string? CurrentNodeId { get; private set; }
    public int Hp { get; private set; }
    public int MaxHp { get; private set; }
    public int Momentum { get; private set; }
    public int BestMomentum { get; private set; }
    public int Score { get; private set; }
    public IReadOnlyList<string> Relics => _relics;
    public IReadOnlyList<RogueHistoryEntry> History => _history;
    public RogueNode? ActiveNode { get; private set; }
    public int BossPhase { get; private set; } = 1;
    public int BossPhasesTotal { get; private set; } = RogueConfig.BossPhases;
    public bool IsDaily { get; private set; }
    public string ActiveEventText { get; private set; } = string.Empty;
    public double EventScoreMult { get; private set; } = 1;
    public bool StarReported { get; private set; }

    /// <summary>韧性 0..1（来自画像「情绪与坚持度」）：低韧性答错扣血更少，避免逼脆弱学生。</summary>
    public double Resilience { get; set; } = 0.5;

    // ── 派生 ──
    public int PassedCount => _history.Count(h => h.Passed);
    public int Attempts => _history.Count;
    public int Depth => _nodes.Count(n => n.State == RogueNodeState.Cleared && n.ChallengeKind is not null);

    public IReadOnlyList<IReadOnlyList<RogueNode>> NodesByLayer()
    {
        var rows = new List<List<RogueNode>>();
        for (var i = 0; i < Layers; i++)
        {
            rows.Add(new List<RogueNode>());
        }
        foreach (var n in _nodes)
        {
            if (n.Layer >= 0 && n.Layer < rows.Count)
            {
                rows[n.Layer].Add(n);
            }
        }
        return rows.Select(r => (IReadOnlyList<RogueNode>)r.OrderBy(n => n.Col).ToList()).ToList();
    }

    public RogueNode? NodeById(string id) => _nodes.FirstOrDefault(n => n.Id == id);

    public bool HasRelic(string id) => _relics.Contains(id);

    // ── 开局 ──
    public void StartRun(RogueMap map, int? focus, bool daily = false)
    {
        ArgumentNullException.ThrowIfNull(map);
        FocusChapter = focus;
        _nodes = map.Nodes.ToList();
        Layers = map.Layers;
        CurrentNodeId = null;
        Hp = RogueConfig.ExpeditionMaxHp;
        MaxHp = RogueConfig.ExpeditionMaxHp;
        Momentum = 0;
        BestMomentum = 0;
        Score = 0;
        _relics.Clear();
        _history.Clear();
        BossPhase = 1;
        IsDaily = daily;
        StarReported = false;
        ClearActive();
        Phase = RogueRunPhase.Map;
    }

    // ── 选节点 ──
    public EnterOutcome EnterNode(string id)
    {
        if (Phase != RogueRunPhase.Map)
        {
            return new EnterOutcome(EnterAction.Ignored);
        }
        var node = NodeById(id);
        if (node is null || node.State != RogueNodeState.Available)
        {
            return new EnterOutcome(EnterAction.Ignored);
        }
        CurrentNodeId = id;

        switch (node.Kind)
        {
            case RogueNodeKind.Rest:
            {
                var heal = RogueConfig.RestHeal + (HasRelic(RogueRelics.FocusLens) ? 1 : 0);
                Hp = Math.Min(MaxHp, Hp + heal);
                MarkCleared(node);
                return new EnterOutcome(EnterAction.RestResolved, HealAmount: heal);
            }
            case RogueNodeKind.Chest:
            {
                MarkCleared(node);
                var relic = GrantRelic();
                return new EnterOutcome(EnterAction.ChestResolved, GrantedRelicId: relic);
            }
            case RogueNodeKind.Event:
            {
                ActiveNode = node;
                EventScoreMult = 1;
                ActiveEventText = RogueEvents.PickScenario(KcNames.DisplayName(node.KcCode), _rng);
                Phase = RogueRunPhase.Event;
                return new EnterOutcome(EnterAction.EnteredEvent);
            }
            default:
            {
                if (node.Kind == RogueNodeKind.Boss)
                {
                    BossPhase = 1;
                }
                ActiveNode = node;
                Phase = RogueRunPhase.Challenge;
                return new EnterOutcome(EnterAction.StartChallenge);
            }
        }
    }

    /// <summary>事件节点三选一：钻研(难度+1，得分×1.5) / 稳练(常规) / 复盘(不战，回血+小分直接过)。</summary>
    public EnterOutcome ChooseEvent(string choice)
    {
        var node = ActiveNode;
        if (node is null || Phase != RogueRunPhase.Event)
        {
            return new EnterOutcome(EnterAction.Ignored);
        }
        if (choice == RogueEventChoice.Review)
        {
            var heal = 1 + (HasRelic(RogueRelics.FocusLens) ? 1 : 0);
            Hp = Math.Min(MaxHp, Hp + heal);
            Score += node.Difficulty * 5;
            MarkCleared(node);
            ClearActive();
            Phase = RogueRunPhase.Map;
            return new EnterOutcome(EnterAction.ReviewResolved, HealAmount: heal);
        }
        if (choice == RogueEventChoice.Study)
        {
            node.Difficulty = Math.Min(5, node.Difficulty + 1);
            EventScoreMult = 1.5;
        }
        else
        {
            EventScoreMult = 1;
        }
        Phase = RogueRunPhase.Challenge;
        return new EnterOutcome(EnterAction.StartChallenge);
    }

    // ── 判题结算 ──
    public int ScoreFor(RogueNode node)
    {
        var baseScore = node.Difficulty * 10;
        var momentumBonus = (int)Math.Round(baseScore * Momentum * 0.1);
        var kindMult = node.Kind is RogueNodeKind.Elite or RogueNodeKind.Boss ? 1.5 : 1.0;
        var relicMult = HasRelic(RogueRelics.ComboScholar) ? 1.2 : 1.0;
        return (int)Math.Round((baseScore + momentumBonus) * kindMult * relicMult * EventScoreMult);
    }

    /// <summary>把一次判题结果灌入 run。返回结果指导调用方下一步（boss 进阶则继续出题，否则展示结算）。</summary>
    public JudgeOutcome ApplyJudge(bool passed)
    {
        var node = ActiveNode;
        if (node is null)
        {
            return new JudgeOutcome(false, false, null);
        }
        _lastPassed = passed;
        var gained = passed ? ScoreFor(node) : 0;
        _history.Insert(0, new RogueHistoryEntry
        {
            NodeId = node.Id,
            KcCode = node.KcCode,
            Kind = node.Kind,
            Passed = passed,
            Score = gained,
            At = DateTimeOffset.UtcNow.ToString("o"),
        });
        if (_history.Count > 50)
        {
            _history.RemoveRange(50, _history.Count - 50);
        }

        if (passed)
        {
            Score += gained;
            Momentum += 1;
            if (Momentum > BestMomentum)
            {
                BestMomentum = Momentum;
            }
            // BOSS 多阶段：未到最后一阶段则进入下一阶段、不结算，连续作战。
            if (node.Kind == RogueNodeKind.Boss && BossPhase < BossPhasesTotal)
            {
                BossPhase += 1;
                return new JudgeOutcome(true, BossAdvanced: true, null);
            }
            MarkCleared(node);
            string? relic = null;
            if (node.Kind == RogueNodeKind.Elite)
            {
                relic = GrantRelic();
            }
            return new JudgeOutcome(true, false, relic);
        }

        // 答错：温和扣血（低韧性扣更少；「沉静心法」再 -1，至少扣 1）。
        var loss = Resilience < 0.4 ? 1 : 2;
        if (HasRelic(RogueRelics.CalmMind))
        {
            loss = Math.Max(1, loss - 1);
        }
        Hp = Math.Max(0, Hp - loss);
        // 「复盘回气」：气势减半而非清零。
        Momentum = HasRelic(RogueRelics.SecondWind) ? Momentum / 2 : 0;
        return new JudgeOutcome(false, false, null);
    }

    /// <summary>战斗结算后返回地图；刚通关 boss → 胜利；HP 归零 → 结束。</summary>
    public void ContinueAfterChallenge()
    {
        var wasBoss = ActiveNode?.Kind == RogueNodeKind.Boss;
        var passed = _lastPassed;
        ClearActive();
        if (Hp <= 0)
        {
            Phase = RogueRunPhase.Over;
        }
        else if (wasBoss && passed)
        {
            Phase = RogueRunPhase.Cleared;
        }
        else
        {
            Phase = RogueRunPhase.Map;
        }
    }

    /// <summary>弃局：回到起始页（调用方另负责清后端活跃 run）。</summary>
    public void Abort()
    {
        Phase = RogueRunPhase.Idle;
        ClearActive();
    }

    // ── 补星上报闸门（与后端 MainlineRecoveryService 阈值一致） ──
    public bool TryBeginStarReport(out int chapter, out int correct, out int total, out int streak)
    {
        chapter = FocusChapter ?? 0;
        correct = PassedCount;
        total = Attempts;
        streak = BestMomentum;
        if (StarReported || FocusChapter is null)
        {
            return false;
        }
        if (total <= 0 || correct < RogueConfig.StarMinCorrect || (double)correct / total < RogueConfig.StarMinAccuracy)
        {
            return false;
        }
        StarReported = true; // tentatively claim;调用方失败时调 ResetStarReport 允许重试
        return true;
    }

    public void ResetStarReport() => StarReported = false;

    // ── 快照 ──
    public ExpeditionRunSnapshot Snapshot() => new()
    {
        Phase = Phase,
        FocusChapter = FocusChapter,
        Nodes = _nodes,
        Layers = Layers,
        CurrentNodeId = CurrentNodeId,
        Hp = Hp,
        MaxHp = MaxHp,
        Momentum = Momentum,
        BestMomentum = BestMomentum,
        Score = Score,
        Relics = _relics.ToList(),
        IsDaily = IsDaily,
        History = _history.ToList(),
    };

    /// <summary>把一份快照灌入引擎（中途答题/事件刷新 → 退回地图）；供续局复用。</summary>
    public void ApplySnapshot(ExpeditionRunSnapshot p)
    {
        ArgumentNullException.ThrowIfNull(p);
        Phase = IsResumablePhase(p.Phase) && p.Phase != RogueRunPhase.Map ? RogueRunPhase.Map : p.Phase;
        FocusChapter = p.FocusChapter;
        _nodes = p.Nodes ?? new List<RogueNode>();
        Layers = p.Layers;
        CurrentNodeId = p.CurrentNodeId;
        Hp = p.Hp;
        MaxHp = p.MaxHp == 0 ? RogueConfig.ExpeditionMaxHp : p.MaxHp;
        Momentum = p.Momentum;
        BestMomentum = p.BestMomentum;
        Score = p.Score;
        _relics.Clear();
        if (p.Relics is not null)
        {
            _relics.AddRange(p.Relics);
        }
        IsDaily = p.IsDaily;
        _history.Clear();
        if (p.History is not null)
        {
            _history.AddRange(p.History);
        }
        StarReported = false;
        ClearActive();
    }

    /// <summary>续局允许的 phase（中途答题/事件刷新统一回退到地图）。</summary>
    public static bool IsResumablePhase(string phase) =>
        phase is RogueRunPhase.Map or RogueRunPhase.Challenge or RogueRunPhase.Event;

    // ── 内部 ──
    private void ClearActive()
    {
        ActiveNode = null;
        ActiveEventText = string.Empty;
        EventScoreMult = 1;
    }

    /// <summary>标记节点通关：解锁出边、同层其它可选节点置为绕过（不可回头）。</summary>
    private void MarkCleared(RogueNode node)
    {
        node.State = RogueNodeState.Cleared;
        foreach (var sib in _nodes)
        {
            if (sib.Layer == node.Layer && sib.Id != node.Id && sib.State == RogueNodeState.Available)
            {
                sib.State = RogueNodeState.Bypassed;
            }
        }
        foreach (var id in node.Next)
        {
            var target = NodeById(id);
            if (target is { State: RogueNodeState.Locked })
            {
                target.State = RogueNodeState.Available;
            }
        }
    }

    /// <summary>获得一件未拥有的学习增益；集齐则返回 null（不重复发）。</summary>
    private string? GrantRelic()
    {
        var owned = new HashSet<string>(_relics);
        var candidates = RogueRelics.All.Where(r => !owned.Contains(r.Id)).ToList();
        if (candidates.Count == 0)
        {
            return null;
        }
        var idx = Math.Clamp((int)Math.Floor(_rng() * candidates.Count), 0, candidates.Count - 1);
        var relic = candidates[idx];
        _relics.Add(relic.Id);
        if (relic.Id == RogueRelics.SturdyNotes)
        {
            MaxHp += 1;
            Hp += 1;
        }
        return relic.Id;
    }
}
