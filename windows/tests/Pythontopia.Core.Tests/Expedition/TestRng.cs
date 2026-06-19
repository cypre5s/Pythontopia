namespace Pythontopia.Core.Tests.Expedition;

/// <summary>Deterministic RNG sources so expedition logic can be tested without flakiness.</summary>
internal static class TestRng
{
    public static Func<double> Constant(double value) => () => value;

    public static Func<double> Sequence(params double[] values)
    {
        var i = 0;
        return () => values.Length == 0 ? 0 : values[i++ % values.Length];
    }

    /// <summary>Same LCG the production daily-challenge seed uses, for stable structural tests.</summary>
    public static Func<double> Lcg(int seed)
    {
        var s = (uint)seed;
        if (s == 0)
        {
            s = 1;
        }
        return () =>
        {
            s = unchecked((s * 1664525u) + 1013904223u);
            return s / (double)uint.MaxValue;
        };
    }
}
