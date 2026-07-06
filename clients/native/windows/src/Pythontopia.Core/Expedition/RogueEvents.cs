namespace Pythontopia.Core.Expedition;

/// <summary>「概念事件」节点的三类应对 + 剧情文案池 — 端口自 web <c>rogueEvents.ts</c>。</summary>
public sealed record RogueEventChoiceMeta(string Key, string Label, string Desc, string Icon);

public static class RogueEvents
{
    public static readonly IReadOnlyList<RogueEventChoiceMeta> Choices = new[]
    {
        new RogueEventChoiceMeta(RogueEventChoice.Study, "迎难钻研", "挑战一道更难的题（难度 +1），通关得分 ×1.5。", "🔬"),
        new RogueEventChoiceMeta(RogueEventChoice.Steady, "稳扎稳打", "按当前难度挑战一题，常规得分。", "🎯"),
        new RogueEventChoiceMeta(RogueEventChoice.Review, "就地复盘", "不开打：回 1 点体力，拿少量分直接通过。", "📖"),
    };

    // {kc} 会被替换成本节点知识点中文名。
    public static readonly IReadOnlyList<string> Scenarios = new[]
    {
        "岔路口立着一块旧木牌，刻着一道关于「{kc}」的谜题。",
        "一位赶路的学徒卡在「{kc}」上，停下来向你请教。",
        "废墟石台上摊着半张「{kc}」的手稿，字迹斑驳待补。",
        "机关门上的符文与「{kc}」同源，似乎在等一个答案。",
        "篝火旁的老学者聊起「{kc}」，邀你切磋两手。",
    };

    public static string PickScenario(string kcName, Func<double>? rng = null)
    {
        var r = rng ?? Random.Shared.NextDouble;
        var idx = (int)Math.Floor(r() * Scenarios.Count);
        if (idx < 0 || idx >= Scenarios.Count)
        {
            idx = 0;
        }
        return Scenarios[idx].Replace("{kc}", kcName, StringComparison.Ordinal);
    }
}
