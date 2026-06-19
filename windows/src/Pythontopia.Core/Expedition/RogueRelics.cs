namespace Pythontopia.Core.Expedition;

/// <summary>
/// 无尽远征「学习增益」（roguelike relics，教育化命名）— 端口自 web <c>rogueRelics.ts</c>。
/// 一局内可累积的被动增益：从「资源宝箱」与「精英挑战」通关获得。效果都正向且教育友好。
/// </summary>
public sealed record RogueRelic(string Id, string Name, string Icon, string Desc);

public static class RogueRelics
{
    public const string SturdyNotes = "sturdy_notes";
    public const string CalmMind = "calm_mind";
    public const string ComboScholar = "combo_scholar";
    public const string SecondWind = "second_wind";
    public const string FocusLens = "focus_lens";

    public static readonly IReadOnlyList<RogueRelic> All = new[]
    {
        new RogueRelic(SturdyNotes, "厚实笔记", "🛡", "体力上限 +1，并立即回 1 点（更耐打）。"),
        new RogueRelic(CalmMind, "沉静心法", "🧘", "答错少扣 1 点体力（至少扣 1）。"),
        new RogueRelic(ComboScholar, "连题学者", "🔥", "通关得分 +20%。"),
        new RogueRelic(SecondWind, "复盘回气", "💧", "答错时气势减半，而非清零。"),
        new RogueRelic(FocusLens, "专注透镜", "🔍", "营地复盘额外回 1 点体力。"),
    };

    public static RogueRelic? ById(string id) => All.FirstOrDefault(r => r.Id == id);

    public static string Icon(string id) => ById(id)?.Icon ?? "◆";

    public static string Title(string id)
    {
        var r = ById(id);
        return r is null ? id : $"{r.Name}：{r.Desc}";
    }
}
