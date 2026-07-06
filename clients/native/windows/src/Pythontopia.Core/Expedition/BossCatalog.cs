namespace Pythontopia.Core.Expedition;

/// <summary>7 章 boss 元数据（名字 + KC 范围）— 端口自 web <c>frontend/src/data/bossCatalog.ts</c>。</summary>
public sealed record ChapterBoss(int Chapter, string Name, string StartKc, string EndKc);

/// <summary>章末 Boss 目录。用于无尽远征的聚焦章节标签与结算 boss 名。</summary>
public static class BossCatalog
{
    public static readonly IReadOnlyList<ChapterBoss> Bosses = new[]
    {
        new ChapterBoss(1, "算盘灵 · 计算工具守卫", "PY43-KC-001", "PY43-KC-007"),
        new ChapterBoss(2, "解释器学徒 · Python 语言守关", "PY43-KC-008", "PY43-KC-016"),
        new ChapterBoss(3, "二进制独眼 · 信息表示之灵", "PY43-KC-017", "PY43-KC-030"),
        new ChapterBoss(4, "流程钟楼 · 自动化锻造神", "PY43-KC-031", "PY43-KC-038"),
        new ChapterBoss(5, "集合棱镜 · 数据结构灵兽", "PY43-KC-039", "PY43-KC-047"),
        new ChapterBoss(6, "文档守护神 · 文件存取灵", "PY43-KC-048", "PY43-KC-053"),
        new ChapterBoss(7, "归纳之镜 · 面向对象与抽象终极", "PY43-KC-054", "PY43-KC-065"),
    };

    public static ChapterBoss? ByChapter(int chapter) =>
        Bosses.FirstOrDefault(b => b.Chapter == chapter);

    public static string NameForChapter(int chapter) =>
        ByChapter(chapter)?.Name ?? "知识守关者";
}
