namespace Pythontopia.Core.Expedition;

/// <summary>
/// 无尽远征配置 + KC↔章节工具 — 端口自 web <c>frontend/src/data/rogueConfig.ts</c>。
/// KC 编码固定 PY43-KC-001..065，章节区间与课程包 43《Python 语言基础》7 章 65 KC 对齐。
/// </summary>
public static class RogueConfig
{
    /// <summary>[章节, KC 起编号, KC 止编号]（含端点）。</summary>
    public static readonly IReadOnlyList<(int Chapter, int Lo, int Hi)> KcChapterRanges = new[]
    {
        (1, 1, 7), (2, 8, 16), (3, 17, 30), (4, 31, 38), (5, 39, 47), (6, 48, 53), (7, 54, 65),
    };

    public const int KcTotal = 65;

    /// <summary>地图形状默认值（含起点层；boss 单独追加一层 → 实际 layers = ExpeditionLayers + 1）。</summary>
    public const int ExpeditionLayers = 9;
    public const int LayerMinWidth = 2;
    public const int LayerMaxWidth = 4;

    /// <summary>HP / 计分。</summary>
    public const int ExpeditionMaxHp = 5;
    public const int RestHeal = 1;

    /// <summary>BOSS 多阶段：连过 N 道才算击穿知识闸。</summary>
    public const int BossPhases = 3;

    /// <summary>无尽补星门槛（与后端 MainlineRecoveryService 一致）。</summary>
    public const int StarMinCorrect = 8;
    public const double StarMinAccuracy = 0.8;

    public static string KcCode(int num) => $"PY43-KC-{num:D3}";

    /// <summary>全部 65 个 KC 编码。</summary>
    public static IReadOnlyList<string> AllKcCodes()
    {
        var list = new List<string>(KcTotal);
        for (var i = 1; i <= KcTotal; i++)
        {
            list.Add(KcCode(i));
        }
        return list;
    }

    /// <summary>某 KC 属于第几章（解析不出时回退第 1 章）。</summary>
    public static int KcChapter(string? code)
    {
        var num = ParseKcNumber(code);
        if (num is null)
        {
            return 1;
        }
        foreach (var (chapter, lo, hi) in KcChapterRanges)
        {
            if (num >= lo && num <= hi)
            {
                return chapter;
            }
        }
        return 1;
    }

    /// <summary>某章的全部 KC 编码。</summary>
    public static IReadOnlyList<string> ChapterKcs(int chapter)
    {
        foreach (var (c, lo, hi) in KcChapterRanges)
        {
            if (c == chapter)
            {
                var list = new List<string>(hi - lo + 1);
                for (var n = lo; n <= hi; n++)
                {
                    list.Add(KcCode(n));
                }
                return list;
            }
        }
        return Array.Empty<string>();
    }

    private static int? ParseKcNumber(string? code)
    {
        if (string.IsNullOrEmpty(code))
        {
            return null;
        }
        // Accept both PY43-KC-002 and KC-002 forms; grab the trailing digit group.
        var idx = code.LastIndexOf("KC-", StringComparison.OrdinalIgnoreCase);
        var start = idx >= 0 ? idx + 3 : 0;
        var digits = new string(code[start..].TakeWhile(char.IsDigit).ToArray());
        return int.TryParse(digits, out var n) ? n : null;
    }

    /// <summary>
    /// 节点类型 → 题型候选池（加权随机取一个）。chest/rest 无题型。
    /// battle 偏代码判题，event 偏概念，bug 偏改错，elite 偏综合，boss 偏组合大题。
    /// </summary>
    public static readonly IReadOnlyDictionary<string, string[]> NodeKindPools = new Dictionary<string, string[]>
    {
        [RogueNodeKind.Battle] = new[] { "dual_track", "fill_blank", "closed_loop" },
        [RogueNodeKind.Elite] = new[] { "dual_track", "multi_round" },
        [RogueNodeKind.Event] = new[] { "short_answer", "pdf_question" },
        [RogueNodeKind.Bug] = new[] { "debug", "bug_mine" },
        [RogueNodeKind.Chest] = Array.Empty<string>(),
        [RogueNodeKind.Rest] = Array.Empty<string>(),
        [RogueNodeKind.Boss] = new[] { "multi_round", "dual_track" },
    };

    /// <summary>节点类型中文名（地图节点标签 / 结算用）。</summary>
    public static readonly IReadOnlyDictionary<string, string> NodeKindLabel = new Dictionary<string, string>
    {
        [RogueNodeKind.Battle] = "判题战斗",
        [RogueNodeKind.Elite] = "精英挑战",
        [RogueNodeKind.Event] = "概念考点",
        [RogueNodeKind.Bug] = "抓 Bug",
        [RogueNodeKind.Chest] = "资源宝箱",
        [RogueNodeKind.Rest] = "复盘营地",
        [RogueNodeKind.Boss] = "知识闸 · BOSS",
    };

    /// <summary>节点类型图标（emoji，HUD / 节点用）。</summary>
    public static readonly IReadOnlyDictionary<string, string> NodeKindIcon = new Dictionary<string, string>
    {
        [RogueNodeKind.Battle] = "⚔",
        [RogueNodeKind.Elite] = "✦",
        [RogueNodeKind.Event] = "❓",
        [RogueNodeKind.Bug] = "🐞",
        [RogueNodeKind.Chest] = "🎁",
        [RogueNodeKind.Rest] = "⛺",
        [RogueNodeKind.Boss] = "★",
    };

    public static string KindLabel(string kind) =>
        NodeKindLabel.TryGetValue(kind, out var v) ? v : kind;

    public static string KindIcon(string kind) =>
        NodeKindIcon.TryGetValue(kind, out var v) ? v : "◆";
}
