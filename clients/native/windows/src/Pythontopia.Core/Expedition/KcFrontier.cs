namespace Pythontopia.Core.Expedition;

/// <summary>
/// 知识前沿选择器（本地回退）— 端口自 web composable <c>useKcFrontier.ts</c>。
/// 从掌握度快照 + 画像弱项 + 可选聚焦章节，产出按优先级排序的候选 KC。
/// 优先级（高→低）：画像弱项 KC 且未掌握 &gt; ZPD 中带（0.2–0.8）&gt; 未掌握 &gt; 越不熟越靠前。
/// 后端权威 select-frontier 失败/超时时由调用方回退到本地此选择器，绝不阻塞玩家。
/// </summary>
public sealed class KcFrontier
{
    private readonly IReadOnlyDictionary<string, double> _mastery;
    private readonly HashSet<string> _weak;
    private readonly Func<double> _rng;

    public KcFrontier(
        IReadOnlyDictionary<string, double>? mastery = null,
        IEnumerable<string>? weakKcs = null,
        Func<double>? rng = null)
    {
        _mastery = mastery ?? new Dictionary<string, double>();
        _weak = new HashSet<string>(weakKcs ?? Array.Empty<string>());
        _rng = rng ?? Random.Shared.NextDouble;
    }

    public double MasteryOf(string code) => _mastery.TryGetValue(code, out var m) ? m : 0;

    private double Priority(string code)
    {
        var m = MasteryOf(code);
        double p = 0;
        if (_weak.Contains(code) && m < 0.8)
        {
            p += 100;
        }
        if (m >= 0.2 && m <= 0.8)
        {
            p += 50;
        }
        if (m < 0.2)
        {
            p += 20;
        }
        p += (1 - m) * 10;
        return p;
    }

    public IReadOnlyList<string> Frontier(int? focusChapter)
    {
        var pool = focusChapter is int c ? RogueConfig.ChapterKcs(c) : RogueConfig.AllKcCodes();
        return pool.OrderByDescending(Priority).ToList();
    }

    public string PickKc(int? focusChapter, ISet<string>? exclude = null, int topN = 8)
    {
        var ranked = Frontier(focusChapter);
        var filtered = exclude is null
            ? ranked
            : ranked.Where(c => !exclude.Contains(c)).ToList();
        var list = filtered.Count > 0 ? filtered : ranked;
        if (list.Count == 0)
        {
            return RogueConfig.AllKcCodes().FirstOrDefault() ?? string.Empty;
        }
        var span = Math.Max(1, Math.Min(topN, list.Count));
        var idx = (int)Math.Floor(_rng() * span);
        if (idx < 0 || idx >= list.Count)
        {
            idx = 0;
        }
        return list[idx];
    }

    public int DifficultyForKc(string code)
    {
        var m = MasteryOf(code);
        return Math.Max(1, Math.Min(5, (int)Math.Round(1 + m * 4)));
    }
}
