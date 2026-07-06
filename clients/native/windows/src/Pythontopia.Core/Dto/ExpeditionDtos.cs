using Pythontopia.Core.Expedition;

namespace Pythontopia.Core.Dto;

/// <summary>POST /api/world/boss/select-frontier body — 知识前沿选择器输入。</summary>
public sealed record FrontierSelectRequest(
    int? FocusChapter,
    IReadOnlyList<string> RecentKcs,
    IReadOnlyList<string> Exclude,
    int Count);

/// <summary>一条知识前沿候选（后端读权威掌握度 + graph 排序 + 间隔重复产出）。</summary>
public sealed record FrontierPick(
    string KcCode,
    int Chapter,
    int Difficulty,
    string? Reason);

/// <summary>POST /api/world/boss/select-frontier response.</summary>
public sealed record FrontierSelectResult(IReadOnlyList<FrontierPick> Picks);

/// <summary>
/// 一局远征 run 的可持久化快照（与 web <c>ExpeditionRunSnapshot</c> 同构，后端
/// <c>expedition_run</c> 表存它）。GET/PUT/DELETE /api/world/expedition/run。
/// </summary>
public sealed class ExpeditionRunSnapshot
{
    public string Phase { get; set; } = RogueRunPhase.Idle;
    public int? FocusChapter { get; set; }
    public List<RogueNode> Nodes { get; set; } = new();
    public int Layers { get; set; }
    public string? CurrentNodeId { get; set; }
    public int Hp { get; set; }
    public int MaxHp { get; set; }
    public int Momentum { get; set; }
    public int BestMomentum { get; set; }
    public int Score { get; set; }
    public List<string> Relics { get; set; } = new();
    public bool IsDaily { get; set; }
    public List<RogueHistoryEntry> History { get; set; } = new();
}
