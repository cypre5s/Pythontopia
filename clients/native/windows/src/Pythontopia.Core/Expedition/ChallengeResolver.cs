using Pythontopia.Core.Dto;

namespace Pythontopia.Core.Expedition;

/// <summary>
/// 把「节点类型 + 层号」解析为「KC / 题型 / 难度」— 端口自 web <c>rogueRun.resolveChallenge</c>。
/// 优先消费后端前沿选择器的 picks（权威 + 间隔重复），用尽后回退本地 <see cref="KcFrontier"/>。
/// 供 <see cref="RogueMapGenerator.Generate"/> 注入；有 used-KC 去重状态，故为一次性实例。
/// </summary>
public sealed class ChallengeResolver
{
    private readonly Queue<FrontierPick> _queue;
    private readonly KcFrontier _frontier;
    private readonly int? _focusChapter;
    private readonly Func<double> _rng;
    private readonly HashSet<string> _usedKcs = new();

    public ChallengeResolver(
        IEnumerable<FrontierPick> picks,
        KcFrontier frontier,
        int? focusChapter,
        Func<double>? rng = null)
    {
        _queue = new Queue<FrontierPick>(picks ?? Array.Empty<FrontierPick>());
        _frontier = frontier ?? new KcFrontier();
        _focusChapter = focusChapter;
        _rng = rng ?? Random.Shared.NextDouble;
    }

    public ResolvedChallenge Resolve(string kind, int layer)
    {
        if (kind is RogueNodeKind.Rest or RogueNodeKind.Chest)
        {
            return new ResolvedChallenge(null, null, 0);
        }

        string kc;
        int difficulty;
        if (_queue.Count > 0)
        {
            var pick = _queue.Dequeue();
            kc = pick.KcCode;
            difficulty = pick.Difficulty;
        }
        else
        {
            kc = _frontier.PickKc(_focusChapter, _usedKcs);
            difficulty = _frontier.DifficultyForKc(kc);
        }
        _usedKcs.Add(kc);

        var pool = RogueConfig.NodeKindPools.TryGetValue(kind, out var p) ? p : Array.Empty<string>();
        var challengeKind = pool.Length > 0
            ? pool[Math.Clamp((int)Math.Floor(_rng() * pool.Length), 0, pool.Length - 1)]
            : "dual_track";

        if (kind == RogueNodeKind.Elite)
        {
            difficulty = Math.Min(5, difficulty + 1);
        }
        if (kind == RogueNodeKind.Boss)
        {
            difficulty = Math.Min(5, Math.Max(3, difficulty + 1));
        }

        return new ResolvedChallenge(kc, challengeKind, difficulty);
    }
}
