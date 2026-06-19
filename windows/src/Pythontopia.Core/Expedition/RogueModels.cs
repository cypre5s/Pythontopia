namespace Pythontopia.Core.Expedition;

/// <summary>
/// 无尽远征 (endless expedition) Roguelike domain models — a faithful C# port of the web
/// <c>frontend/src/types/rogue.ts</c>. A run is a layered DAG (Slay-the-Spire style): the player
/// climbs from the start layer choosing the next node along out-edges until the boss layer.
/// Kind / state / phase are kept as the exact lowercase string literals the web uses so that the
/// run snapshot round-trips byte-for-byte through the backend <c>expedition_run</c> table.
/// </summary>
public static class RogueNodeKind
{
    public const string Battle = "battle"; // 常规判题
    public const string Elite = "elite";   // 高难判题（难度 +1，奖励更高）
    public const string Event = "event";   // 概念考点（简答 / 课件问答）
    public const string Bug = "bug";       // 抓 bug（改错 / Bug 矿洞）
    public const string Chest = "chest";   // 宝箱：领本 KC 资源
    public const string Rest = "rest";     // 营地：回血 + 复盘
    public const string Boss = "boss";     // 知识闸：像素 boss
}

public static class RogueNodeState
{
    public const string Locked = "locked";
    public const string Available = "available";
    public const string Current = "current";
    public const string Cleared = "cleared";
    public const string Bypassed = "bypassed";
}

public static class RogueRunPhase
{
    public const string Idle = "idle";           // 未开始
    public const string Map = "map";             // 在地图上选节点
    public const string Event = "event";         // 概念事件：选应对
    public const string Challenge = "challenge"; // 进入某节点答题中
    public const string Cleared = "cleared";     // 打通 boss，本局胜利
    public const string Over = "over";           // HP 归零，本局结束
}

/// <summary>事件节点三选一应对（钻研 / 稳练 / 复盘）。</summary>
public static class RogueEventChoice
{
    public const string Study = "study";   // 迎难钻研：难度 +1，得分 ×1.5
    public const string Steady = "steady"; // 稳扎稳打：常规
    public const string Review = "review"; // 就地复盘：不开打，回血 + 小分直接过
}

/// <summary>分层 DAG 的一个节点（与 web <c>RogueNode</c> 同构，可序列化进 run 快照）。</summary>
public sealed class RogueNode
{
    /// <summary>节点 id，形如 "L2-1"。</summary>
    public string Id { get; set; } = string.Empty;

    /// <summary>所在层（0 = 起点层）。</summary>
    public int Layer { get; set; }

    /// <summary>层内列序（用于布局与连边）。</summary>
    public int Col { get; set; }

    /// <summary>节点类型，取 <see cref="RogueNodeKind"/> 之一。</summary>
    public string Kind { get; set; } = RogueNodeKind.Battle;

    /// <summary>锚定 KC（battle/elite/event/bug/boss 有；chest/rest 为 null）。</summary>
    public string? KcCode { get; set; }

    /// <summary>题型（映射到 arena kind，供出题用）；chest/rest 为 null。</summary>
    public string? ChallengeKind { get; set; }

    /// <summary>难度 1..5。</summary>
    public int Difficulty { get; set; }

    /// <summary>出边：可前往的下一层节点 id 列表。</summary>
    public List<string> Next { get; set; } = new();

    /// <summary>节点状态，取 <see cref="RogueNodeState"/> 之一。</summary>
    public string State { get; set; } = RogueNodeState.Locked;
}

/// <summary>一局生成出的分层地图（扁平节点 + 总层数）。</summary>
public sealed class RogueMap
{
    public RogueMap(IReadOnlyList<RogueNode> nodes, int layers)
    {
        Nodes = nodes;
        Layers = layers;
    }

    /// <summary>扁平节点列表（含 layer/col，渲染时按 layer 分组）。</summary>
    public IReadOnlyList<RogueNode> Nodes { get; }

    /// <summary>总层数（含起点层与 boss 层）。</summary>
    public int Layers { get; }
}

/// <summary>一次节点结算记录（与 web <c>RogueHistoryEntry</c> 同构）。</summary>
public sealed class RogueHistoryEntry
{
    public string NodeId { get; set; } = string.Empty;
    public string? KcCode { get; set; }
    public string Kind { get; set; } = RogueNodeKind.Battle;
    public bool Passed { get; set; }
    public int Score { get; set; }
    public string At { get; set; } = string.Empty;
}
