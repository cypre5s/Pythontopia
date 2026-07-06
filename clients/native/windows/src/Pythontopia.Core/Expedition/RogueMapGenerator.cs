namespace Pythontopia.Core.Expedition;

/// <summary>resolveChallenge 的结果：给定节点类型 + 层号产出 KC / 题型 / 难度。</summary>
public readonly record struct ResolvedChallenge(string? KcCode, string? ChallengeKind, int Difficulty);

/// <summary>
/// 无尽远征地图生成（纯函数，可注入 RNG + KC 解析器，便于单测）— 端口自 web <c>rogueMap.ts</c>。
/// 产出一张分层 DAG：layer 0 单起点（battle），最后一层单 boss，中间层 2..4 个节点，
/// 每 3 层保证一个营地（rest）。连边保证「每个下层节点都有入边、每个上层节点都有出边」→ 必可达 boss。
/// </summary>
public static class RogueMapGenerator
{
    /// <param name="resolveChallenge">给定节点类型 + 层号 → KC / 题型 / 难度（由调用方注入：知识前沿选择器）。</param>
    /// <param name="rng">随机源（默认 <see cref="Random.Shared"/>），测试可注入确定性序列。</param>
    /// <param name="layers">中间区段层数（含起点层；boss 层会再追加一层）。</param>
    public static RogueMap Generate(
        Func<string, int, ResolvedChallenge> resolveChallenge,
        Func<double>? rng = null,
        int? layers = null)
    {
        ArgumentNullException.ThrowIfNull(resolveChallenge);
        var rand = rng ?? Random.Shared.NextDouble;
        var midLayers = Math.Max(3, layers ?? RogueConfig.ExpeditionLayers);
        var totalLayers = midLayers + 1; // + boss 层

        int RandInt(int lo, int hi) => lo + (int)Math.Floor(rand() * (hi - lo + 1));

        // 1) 每层节点类型
        var layerKinds = new List<List<string>>();
        for (var layer = 0; layer < totalLayers; layer++)
        {
            if (layer == 0)
            {
                layerKinds.Add(new List<string> { RogueNodeKind.Battle });
                continue;
            }
            if (layer == totalLayers - 1)
            {
                layerKinds.Add(new List<string> { RogueNodeKind.Boss });
                continue;
            }
            var width = RandInt(RogueConfig.LayerMinWidth, RogueConfig.LayerMaxWidth);
            var forceRest = layer % 3 == 0; // 每 3 层一个营地
            var kinds = new List<string>(width);
            for (var col = 0; col < width; col++)
            {
                kinds.Add(forceRest && col == 0 ? RogueNodeKind.Rest : PickKindForLayer(layer, midLayers, rand));
            }
            layerKinds.Add(kinds);
        }

        // 2) 建节点
        var byLayer = new List<List<RogueNode>>();
        var nodes = new List<RogueNode>();
        for (var layer = 0; layer < layerKinds.Count; layer++)
        {
            var kinds = layerKinds[layer];
            var row = new List<RogueNode>(kinds.Count);
            for (var col = 0; col < kinds.Count; col++)
            {
                var kind = kinds[col];
                var resolved = resolveChallenge(kind, layer);
                row.Add(new RogueNode
                {
                    Id = $"L{layer}-{col}",
                    Layer = layer,
                    Col = col,
                    Kind = kind,
                    KcCode = resolved.KcCode,
                    ChallengeKind = resolved.ChallengeKind,
                    Difficulty = resolved.Difficulty,
                    Next = new List<string>(),
                    State = layer == 0 ? RogueNodeState.Available : RogueNodeState.Locked,
                });
            }
            byLayer.Add(row);
            nodes.AddRange(row);
        }

        // 3) 连边：按归一化列位置取最近 1..2 个；再补齐让下层每个节点都有入边
        static double PosOf(RogueNode n, int len) => len <= 1 ? 0.5 : (double)n.Col / (len - 1);
        for (var layer = 0; layer < totalLayers - 1; layer++)
        {
            var cur = byLayer[layer];
            var nxt = byLayer[layer + 1];
            foreach (var node in cur)
            {
                var p = PosOf(node, cur.Count);
                var sorted = nxt
                    .OrderBy(a => Math.Abs(PosOf(a, nxt.Count) - p))
                    .ToList();
                var links = 1 + (rand() < 0.45 && sorted.Count > 1 ? 1 : 0);
                node.Next = sorted.Take(links).Select(n => n.Id).ToList();
            }
            var covered = new HashSet<string>(cur.SelectMany(n => n.Next));
            foreach (var target in nxt)
            {
                if (covered.Contains(target.Id))
                {
                    continue;
                }
                var p = PosOf(target, nxt.Count);
                var src = cur.OrderBy(a => Math.Abs(PosOf(a, cur.Count) - p)).FirstOrDefault();
                if (src is not null && !src.Next.Contains(target.Id))
                {
                    src.Next.Add(target.Id);
                }
            }
        }

        return new RogueMap(nodes, totalLayers);
    }

    private static string PickKindForLayer(int layer, int midLayers, Func<double> rng)
    {
        var t = midLayers > 0 ? (double)layer / midLayers : 0;
        var pool = new List<string> { RogueNodeKind.Battle, RogueNodeKind.Battle, RogueNodeKind.Event };
        if (t > 0.25)
        {
            pool.Add(RogueNodeKind.Bug);
        }
        if (t > 0.3)
        {
            pool.Add(RogueNodeKind.Chest);
        }
        if (t > 0.55)
        {
            pool.Add(RogueNodeKind.Elite);
        }
        var idx = (int)Math.Floor(rng() * pool.Count);
        return idx >= 0 && idx < pool.Count ? pool[idx] : RogueNodeKind.Battle;
    }
}
