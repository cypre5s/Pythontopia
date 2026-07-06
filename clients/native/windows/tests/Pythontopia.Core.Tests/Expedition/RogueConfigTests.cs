using Pythontopia.Core.Expedition;
using Xunit;

namespace Pythontopia.Core.Tests.Expedition;

public class RogueConfigTests
{
    [Fact]
    public void AllKcCodes_Has65_CodesPaddedToThreeDigits()
    {
        var codes = RogueConfig.AllKcCodes();
        Assert.Equal(65, codes.Count);
        Assert.Equal("PY43-KC-001", codes[0]);
        Assert.Equal("PY43-KC-065", codes[^1]);
    }

    [Theory]
    [InlineData("PY43-KC-001", 1)]
    [InlineData("PY43-KC-007", 1)]
    [InlineData("PY43-KC-008", 2)]
    [InlineData("PY43-KC-030", 3)]
    [InlineData("PY43-KC-038", 4)]
    [InlineData("PY43-KC-047", 5)]
    [InlineData("PY43-KC-053", 6)]
    [InlineData("PY43-KC-065", 7)]
    [InlineData("KC-010", 2)]
    public void KcChapter_MapsToCourseRanges(string code, int expected) =>
        Assert.Equal(expected, RogueConfig.KcChapter(code));

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("nonsense")]
    public void KcChapter_FallsBackToChapter1(string? code) =>
        Assert.Equal(1, RogueConfig.KcChapter(code));

    [Fact]
    public void ChapterKcs_ReturnsContiguousRange()
    {
        var ch1 = RogueConfig.ChapterKcs(1);
        Assert.Equal(7, ch1.Count);
        Assert.Equal("PY43-KC-001", ch1[0]);
        Assert.Equal("PY43-KC-007", ch1[^1]);

        Assert.Equal(12, RogueConfig.ChapterKcs(7).Count);
        Assert.Empty(RogueConfig.ChapterKcs(99));
    }

    [Fact]
    public void NodeKindPools_CoverEveryKind_RestAndChestEmpty()
    {
        Assert.Empty(RogueConfig.NodeKindPools[RogueNodeKind.Rest]);
        Assert.Empty(RogueConfig.NodeKindPools[RogueNodeKind.Chest]);
        Assert.NotEmpty(RogueConfig.NodeKindPools[RogueNodeKind.Battle]);
        Assert.NotEmpty(RogueConfig.NodeKindPools[RogueNodeKind.Boss]);
    }

    [Fact]
    public void KindLabelAndIcon_ResolveKnownAndUnknown()
    {
        Assert.Equal("知识闸 · BOSS", RogueConfig.KindLabel(RogueNodeKind.Boss));
        Assert.Equal("★", RogueConfig.KindIcon(RogueNodeKind.Boss));
        Assert.Equal("?", RogueConfig.KindLabel("?")); // unknown returns the key itself
        Assert.Equal("◆", RogueConfig.KindIcon("?"));  // unknown returns a fallback glyph
    }

    [Fact]
    public void KcNames_DisplayName_HandlesPrefixedBareAndMissing()
    {
        Assert.Equal("for 循环与遍历", KcNames.DisplayName("PY43-KC-031"));
        Assert.Equal("变量与运算符", KcNames.DisplayName("KC-010"));
        Assert.Equal("本知识点", KcNames.DisplayName(null));
        Assert.Equal("ZZZ", KcNames.DisplayName("ZZZ"));
        Assert.Equal(65, KcNames.Names.Count);
    }

    [Fact]
    public void BossCatalog_HasSevenChapters_WithNameFallback()
    {
        Assert.Equal(7, BossCatalog.Bosses.Count);
        Assert.Equal("算盘灵 · 计算工具守卫", BossCatalog.NameForChapter(1));
        Assert.Equal("知识守关者", BossCatalog.NameForChapter(99));
    }

    [Fact]
    public void RogueRelics_FiveDistinct_WithIconLookup()
    {
        Assert.Equal(5, RogueRelics.All.Count);
        Assert.Equal(5, RogueRelics.All.Select(r => r.Id).Distinct().Count());
        Assert.Equal("🛡", RogueRelics.Icon(RogueRelics.SturdyNotes));
        Assert.Equal("◆", RogueRelics.Icon("unknown"));
    }

    [Fact]
    public void RogueEvents_PickScenario_SubstitutesKcName()
    {
        var text = RogueEvents.PickScenario("循环控制语句", TestRng.Constant(0));
        Assert.Contains("循环控制语句", text);
        Assert.DoesNotContain("{kc}", text);
        Assert.Equal(3, RogueEvents.Choices.Count);
    }
}
