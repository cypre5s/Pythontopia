using Pythontopia.Core.Expedition;
using Xunit;

namespace Pythontopia.Core.Tests.Expedition;

public class KcFrontierTests
{
    [Fact]
    public void MasteryOf_ReturnsScoreOrZero()
    {
        var frontier = new KcFrontier(new Dictionary<string, double> { ["PY43-KC-001"] = 0.42 });
        Assert.Equal(0.42, frontier.MasteryOf("PY43-KC-001"));
        Assert.Equal(0, frontier.MasteryOf("PY43-KC-999"));
    }

    [Theory]
    [InlineData(0.0, 1)]
    [InlineData(1.0, 5)]
    [InlineData(0.5, 3)]
    public void DifficultyForKc_ScalesWithMastery(double mastery, int expected)
    {
        var frontier = new KcFrontier(new Dictionary<string, double> { ["PY43-KC-001"] = mastery });
        Assert.Equal(expected, frontier.DifficultyForKc("PY43-KC-001"));
    }

    [Fact]
    public void Frontier_PrioritisesWeakUnmasteredKc()
    {
        var frontier = new KcFrontier(
            mastery: new Dictionary<string, double> { ["PY43-KC-007"] = 0.3 },
            weakKcs: new[] { "PY43-KC-007" });

        var ranked = frontier.Frontier(focusChapter: 1);
        Assert.Equal("PY43-KC-007", ranked[0]);
    }

    [Fact]
    public void Frontier_RanksZpdAboveMastered()
    {
        var frontier = new KcFrontier(new Dictionary<string, double>
        {
            ["PY43-KC-010"] = 0.5, // ZPD band
            ["PY43-KC-011"] = 0.95, // already mastered
        });

        var ranked = frontier.Frontier(focusChapter: 2).ToList();
        Assert.True(ranked.IndexOf("PY43-KC-010") < ranked.IndexOf("PY43-KC-011"));
    }

    [Fact]
    public void PickKc_RespectsExcludeSet()
    {
        var frontier = new KcFrontier(
            mastery: new Dictionary<string, double>(),
            weakKcs: new[] { "PY43-KC-007" },
            rng: TestRng.Constant(0));

        // KC-007 ranks first; excluding it yields the next-best (chapter-1 ordering by ties).
        var picked = frontier.PickKc(focusChapter: 1, exclude: new HashSet<string> { "PY43-KC-007" });
        Assert.NotEqual("PY43-KC-007", picked);
        Assert.StartsWith("PY43-KC-00", picked);
    }
}
