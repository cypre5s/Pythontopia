using Pythontopia.Core.Expedition;
using Xunit;

namespace Pythontopia.Core.Tests.Expedition;

public class RogueMapGeneratorTests
{
    // A trivial resolver: every battle-ish node anchors KC-001 at difficulty 2.
    private static ResolvedChallenge Resolve(string kind, int layer) =>
        kind is RogueNodeKind.Rest or RogueNodeKind.Chest
            ? new ResolvedChallenge(null, null, 0)
            : new ResolvedChallenge("PY43-KC-001", "dual_track", 2);

    [Theory]
    [InlineData(1)]
    [InlineData(7)]
    [InlineData(42)]
    [InlineData(20260619)]
    public void Generate_ProducesReachableDag(int seed)
    {
        var map = RogueMapGenerator.Generate(Resolve, TestRng.Lcg(seed));

        // Default shape: ExpeditionLayers mid layers + 1 boss layer.
        Assert.Equal(RogueConfig.ExpeditionLayers + 1, map.Layers);

        var byLayer = Enumerable.Range(0, map.Layers)
            .Select(l => map.Nodes.Where(n => n.Layer == l).ToList())
            .ToList();

        // Single battle start; single boss finish.
        Assert.Single(byLayer[0]);
        Assert.Equal(RogueNodeKind.Battle, byLayer[0][0].Kind);
        Assert.Equal(RogueNodeState.Available, byLayer[0][0].State);
        Assert.Single(byLayer[^1]);
        Assert.Equal(RogueNodeKind.Boss, byLayer[^1][0].Kind);

        // Every non-start node has an in-edge; every non-boss node has an out-edge.
        var targets = map.Nodes.SelectMany(n => n.Next).ToHashSet();
        foreach (var node in map.Nodes)
        {
            if (node.Layer > 0)
            {
                Assert.Contains(node.Id, targets);
            }
            if (node.Layer < map.Layers - 1)
            {
                Assert.NotEmpty(node.Next);
            }
        }

        // The boss must be reachable from the start by following out-edges.
        Assert.True(BossReachable(map));
    }

    [Theory]
    [InlineData(1)]
    [InlineData(99)]
    public void Generate_PlacesCampEveryThreeLayers(int seed)
    {
        var map = RogueMapGenerator.Generate(Resolve, TestRng.Lcg(seed));
        // Mid layers at index 3 and 6 force a rest node in column 0.
        foreach (var layer in new[] { 3, 6 })
        {
            var col0 = map.Nodes.Single(n => n.Layer == layer && n.Col == 0);
            Assert.Equal(RogueNodeKind.Rest, col0.Kind);
        }
    }

    [Fact]
    public void Generate_MidLayerWidthsWithinBounds()
    {
        var map = RogueMapGenerator.Generate(Resolve, TestRng.Lcg(7));
        for (var l = 1; l < map.Layers - 1; l++)
        {
            var width = map.Nodes.Count(n => n.Layer == l);
            Assert.InRange(width, RogueConfig.LayerMinWidth, RogueConfig.LayerMaxWidth);
        }
    }

    [Fact]
    public void Generate_RestAndChestHaveNoChallenge()
    {
        var map = RogueMapGenerator.Generate(Resolve, TestRng.Lcg(3));
        foreach (var node in map.Nodes.Where(n => n.Kind is RogueNodeKind.Rest or RogueNodeKind.Chest))
        {
            Assert.Null(node.KcCode);
            Assert.Null(node.ChallengeKind);
            Assert.Equal(0, node.Difficulty);
        }
    }

    private static bool BossReachable(RogueMap map)
    {
        var start = map.Nodes.First(n => n.Layer == 0);
        var boss = map.Nodes.First(n => n.Layer == map.Layers - 1);
        var byId = map.Nodes.ToDictionary(n => n.Id);
        var seen = new HashSet<string>();
        var stack = new Stack<RogueNode>();
        stack.Push(start);
        while (stack.Count > 0)
        {
            var node = stack.Pop();
            if (node.Id == boss.Id)
            {
                return true;
            }
            if (!seen.Add(node.Id))
            {
                continue;
            }
            foreach (var next in node.Next)
            {
                if (byId.TryGetValue(next, out var target))
                {
                    stack.Push(target);
                }
            }
        }
        return false;
    }
}
