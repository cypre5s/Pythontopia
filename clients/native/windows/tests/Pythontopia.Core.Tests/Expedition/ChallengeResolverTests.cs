using Pythontopia.Core.Dto;
using Pythontopia.Core.Expedition;
using Xunit;

namespace Pythontopia.Core.Tests.Expedition;

public class ChallengeResolverTests
{
    private static FrontierPick Pick(string kc, int chapter, int difficulty) => new(kc, chapter, difficulty, "test");

    [Fact]
    public void Resolve_RestAndChest_HaveNoChallenge()
    {
        var resolver = new ChallengeResolver(Array.Empty<FrontierPick>(), new KcFrontier(), focusChapter: 1, TestRng.Constant(0));
        Assert.Equal(new ResolvedChallenge(null, null, 0), resolver.Resolve(RogueNodeKind.Rest, 1));
        Assert.Equal(new ResolvedChallenge(null, null, 0), resolver.Resolve(RogueNodeKind.Chest, 1));
    }

    [Fact]
    public void Resolve_ConsumesBackendPicksFirst()
    {
        var resolver = new ChallengeResolver(
            new[] { Pick("PY43-KC-010", 2, 3) },
            new KcFrontier(),
            focusChapter: null,
            TestRng.Constant(0));

        var resolved = resolver.Resolve(RogueNodeKind.Battle, 1);
        Assert.Equal("PY43-KC-010", resolved.KcCode);
        Assert.Equal(3, resolved.Difficulty);
        Assert.Equal("dual_track", resolved.ChallengeKind); // battle pool[0] with rng 0
    }

    [Fact]
    public void Resolve_EliteBumpsDifficulty()
    {
        var resolver = new ChallengeResolver(new[] { Pick("PY43-KC-010", 2, 2) }, new KcFrontier(), null, TestRng.Constant(0));
        var resolved = resolver.Resolve(RogueNodeKind.Elite, 1);
        Assert.Equal(3, resolved.Difficulty); // 2 + 1
    }

    [Fact]
    public void Resolve_BossHasFloorDifficulty()
    {
        var resolver = new ChallengeResolver(new[] { Pick("PY43-KC-010", 2, 1) }, new KcFrontier(), null, TestRng.Constant(0));
        var resolved = resolver.Resolve(RogueNodeKind.Boss, 1);
        Assert.Equal(3, resolved.Difficulty); // max(3, 1 + 1)
    }

    [Fact]
    public void Resolve_FallsBackToLocalFrontierWhenPicksExhausted()
    {
        var frontier = new KcFrontier(
            mastery: new Dictionary<string, double> { ["PY43-KC-001"] = 0.5 },
            rng: TestRng.Constant(0));
        var resolver = new ChallengeResolver(Array.Empty<FrontierPick>(), frontier, focusChapter: 1, TestRng.Constant(0));

        var resolved = resolver.Resolve(RogueNodeKind.Battle, 1);
        Assert.Equal("PY43-KC-001", resolved.KcCode); // ZPD band ranks first in chapter 1
        Assert.Equal(3, resolved.Difficulty); // difficultyForKc(0.5)
    }
}
