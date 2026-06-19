using Pythontopia.Core.Expedition;
using Xunit;

namespace Pythontopia.Core.Tests.Expedition;

public class RogueRunEngineTests
{
    private static RogueNode Node(string id, int layer, int col, string kind, int diff, string state, params string[] next)
    {
        var battleish = kind is not (RogueNodeKind.Rest or RogueNodeKind.Chest);
        return new RogueNode
        {
            Id = id,
            Layer = layer,
            Col = col,
            Kind = kind,
            KcCode = battleish ? "PY43-KC-001" : null,
            ChallengeKind = battleish ? "dual_track" : null,
            Difficulty = diff,
            State = state,
            Next = next.ToList(),
        };
    }

    private static RogueMap Linear(string kind1, string kind2)
    {
        // L0 (start, available) -> L1 -> L2 boss.
        var nodes = new List<RogueNode>
        {
            Node("L0-0", 0, 0, kind1, 2, RogueNodeState.Available, "L1-0"),
            Node("L1-0", 1, 0, kind2, 2, RogueNodeState.Locked, "L2-0"),
            Node("L2-0", 2, 0, RogueNodeKind.Boss, 3, RogueNodeState.Locked),
        };
        return new RogueMap(nodes, 3);
    }

    [Fact]
    public void StartRun_InitialisesMapState()
    {
        var engine = new RogueRunEngine(TestRng.Constant(0));
        engine.StartRun(Linear(RogueNodeKind.Battle, RogueNodeKind.Battle), focus: 1);

        Assert.Equal(RogueRunPhase.Map, engine.Phase);
        Assert.Equal(RogueConfig.ExpeditionMaxHp, engine.Hp);
        Assert.Equal(0, engine.Score);
        Assert.Equal(1, engine.FocusChapter);
        Assert.Equal(RogueNodeState.Available, engine.NodeById("L0-0")!.State);
    }

    [Fact]
    public void Battle_Pass_ScoresAndUnlocksNextLayer()
    {
        var engine = new RogueRunEngine(TestRng.Constant(0));
        engine.StartRun(Linear(RogueNodeKind.Battle, RogueNodeKind.Battle), focus: 1);

        Assert.Equal(EnterAction.StartChallenge, engine.EnterNode("L0-0").Action);
        var outcome = engine.ApplyJudge(passed: true);
        engine.ContinueAfterChallenge();

        Assert.True(outcome.Passed);
        Assert.False(outcome.BossAdvanced);
        Assert.Equal(20, engine.Score); // diff 2 * 10, no momentum bonus yet
        Assert.Equal(1, engine.Momentum);
        Assert.Equal(RogueConfig.ExpeditionMaxHp, engine.Hp);
        Assert.Equal(RogueRunPhase.Map, engine.Phase);
        Assert.Equal(RogueNodeState.Cleared, engine.NodeById("L0-0")!.State);
        Assert.Equal(RogueNodeState.Available, engine.NodeById("L1-0")!.State);
    }

    [Fact]
    public void Battle_Fail_ReducesHpAndResetsMomentum()
    {
        var engine = new RogueRunEngine(TestRng.Constant(0)) { Resilience = 0.5 };
        engine.StartRun(Linear(RogueNodeKind.Battle, RogueNodeKind.Battle), focus: 1);
        engine.EnterNode("L0-0");

        engine.ApplyJudge(passed: false);
        engine.ContinueAfterChallenge();

        Assert.Equal(RogueConfig.ExpeditionMaxHp - 2, engine.Hp);
        Assert.Equal(0, engine.Momentum);
        Assert.Equal(RogueRunPhase.Map, engine.Phase);
        Assert.Equal(RogueNodeState.Available, engine.NodeById("L0-0")!.State); // not cleared on a fail
    }

    [Fact]
    public void LowResilience_LosesLessHpOnFail()
    {
        var engine = new RogueRunEngine(TestRng.Constant(0)) { Resilience = 0.2 };
        engine.StartRun(Linear(RogueNodeKind.Battle, RogueNodeKind.Battle), focus: 1);
        engine.EnterNode("L0-0");

        engine.ApplyJudge(passed: false);

        Assert.Equal(RogueConfig.ExpeditionMaxHp - 1, engine.Hp);
    }

    [Fact]
    public void Rest_HealsUpToMax_AndBypassesSiblings()
    {
        // L0 battle -> {L1-0 battle, L1-1 rest} -> L2 boss.
        var nodes = new List<RogueNode>
        {
            Node("L0-0", 0, 0, RogueNodeKind.Battle, 2, RogueNodeState.Available, "L1-0", "L1-1"),
            Node("L1-0", 1, 0, RogueNodeKind.Battle, 2, RogueNodeState.Locked, "L2-0"),
            Node("L1-1", 1, 1, RogueNodeKind.Rest, 0, RogueNodeState.Locked, "L2-0"),
            Node("L2-0", 2, 0, RogueNodeKind.Boss, 3, RogueNodeState.Locked),
        };
        var engine = new RogueRunEngine(TestRng.Constant(0));
        engine.StartRun(new RogueMap(nodes, 3), focus: 1);

        engine.EnterNode("L0-0");
        engine.ApplyJudge(passed: true);
        engine.ContinueAfterChallenge();
        engine.EnterNode("L1-0");
        engine.ApplyJudge(passed: false); // hp 5 -> 3
        engine.ContinueAfterChallenge();

        var rest = engine.EnterNode("L1-1");
        Assert.Equal(EnterAction.RestResolved, rest.Action);
        Assert.Equal(4, engine.Hp); // healed 3 -> 4
        Assert.Equal(RogueNodeState.Cleared, engine.NodeById("L1-1")!.State);
        Assert.Equal(RogueNodeState.Bypassed, engine.NodeById("L1-0")!.State);
    }

    [Fact]
    public void Chest_GrantsRelic()
    {
        var nodes = new List<RogueNode>
        {
            Node("L0-0", 0, 0, RogueNodeKind.Chest, 0, RogueNodeState.Available, "L1-0"),
            Node("L1-0", 1, 0, RogueNodeKind.Boss, 3, RogueNodeState.Locked),
        };
        var engine = new RogueRunEngine(TestRng.Constant(0));
        engine.StartRun(new RogueMap(nodes, 2), focus: 1);

        var outcome = engine.EnterNode("L0-0");

        Assert.Equal(EnterAction.ChestResolved, outcome.Action);
        Assert.NotNull(outcome.GrantedRelicId);
        Assert.Single(engine.Relics);
    }

    [Fact]
    public void Boss_MultiPhase_AdvancesThenClears()
    {
        var nodes = new List<RogueNode>
        {
            Node("L0-0", 0, 0, RogueNodeKind.Boss, 3, RogueNodeState.Available),
        };
        var engine = new RogueRunEngine(TestRng.Constant(0));
        engine.StartRun(new RogueMap(nodes, 1), focus: 1);

        Assert.Equal(EnterAction.StartChallenge, engine.EnterNode("L0-0").Action);
        Assert.Equal(1, engine.BossPhase);

        Assert.True(engine.ApplyJudge(passed: true).BossAdvanced);
        Assert.Equal(2, engine.BossPhase);
        Assert.True(engine.ApplyJudge(passed: true).BossAdvanced);
        Assert.Equal(3, engine.BossPhase);

        var final = engine.ApplyJudge(passed: true);
        Assert.False(final.BossAdvanced);
        engine.ContinueAfterChallenge();

        Assert.Equal(RogueRunPhase.Cleared, engine.Phase);
        Assert.Equal(3, engine.Momentum);
        Assert.True(engine.Score > 0);
        Assert.Equal(RogueNodeState.Cleared, engine.NodeById("L0-0")!.State);
    }

    [Fact]
    public void Event_Review_HealsAndScoresWithoutBattle()
    {
        var nodes = new List<RogueNode>
        {
            Node("L0-0", 0, 0, RogueNodeKind.Event, 2, RogueNodeState.Available, "L1-0"),
            Node("L1-0", 1, 0, RogueNodeKind.Boss, 3, RogueNodeState.Locked),
        };
        var engine = new RogueRunEngine(TestRng.Constant(0));
        engine.StartRun(new RogueMap(nodes, 2), focus: 1);

        Assert.Equal(EnterAction.EnteredEvent, engine.EnterNode("L0-0").Action);
        Assert.Equal(RogueRunPhase.Event, engine.Phase);
        Assert.NotEmpty(engine.ActiveEventText);

        var outcome = engine.ChooseEvent(RogueEventChoice.Review);
        Assert.Equal(EnterAction.ReviewResolved, outcome.Action);
        Assert.Equal(10, engine.Score); // diff 2 * 5
        Assert.Equal(RogueRunPhase.Map, engine.Phase);
        Assert.Equal(RogueNodeState.Cleared, engine.NodeById("L0-0")!.State);
        Assert.Equal(RogueNodeState.Available, engine.NodeById("L1-0")!.State);
    }

    [Fact]
    public void Event_Study_BumpsDifficultyAndScoreMultiplier()
    {
        var nodes = new List<RogueNode>
        {
            Node("L0-0", 0, 0, RogueNodeKind.Event, 2, RogueNodeState.Available, "L1-0"),
            Node("L1-0", 1, 0, RogueNodeKind.Boss, 3, RogueNodeState.Locked),
        };
        var engine = new RogueRunEngine(TestRng.Constant(0));
        engine.StartRun(new RogueMap(nodes, 2), focus: 1);

        engine.EnterNode("L0-0");
        Assert.Equal(EnterAction.StartChallenge, engine.ChooseEvent(RogueEventChoice.Study).Action);
        Assert.Equal(3, engine.ActiveNode!.Difficulty); // 2 -> 3

        engine.ApplyJudge(passed: true);
        Assert.Equal(45, engine.Score); // base 30 * 1.5 event multiplier
    }

    [Fact]
    public void EnterNode_RejectsLockedOrWrongPhase()
    {
        var engine = new RogueRunEngine(TestRng.Constant(0));
        engine.StartRun(Linear(RogueNodeKind.Battle, RogueNodeKind.Battle), focus: 1);

        Assert.Equal(EnterAction.Ignored, engine.EnterNode("L1-0").Action); // locked
        engine.EnterNode("L0-0"); // now phase == challenge
        Assert.Equal(EnterAction.Ignored, engine.EnterNode("L0-0").Action); // wrong phase
    }

    [Fact]
    public void Snapshot_RoundTrips()
    {
        var engine = new RogueRunEngine(TestRng.Constant(0));
        engine.StartRun(Linear(RogueNodeKind.Battle, RogueNodeKind.Battle), focus: 2);
        engine.EnterNode("L0-0");
        engine.ApplyJudge(passed: true);
        engine.ContinueAfterChallenge();

        var snap = engine.Snapshot();
        var restored = new RogueRunEngine(TestRng.Constant(0));
        restored.ApplySnapshot(snap);

        Assert.Equal(RogueRunPhase.Map, restored.Phase);
        Assert.Equal(engine.Hp, restored.Hp);
        Assert.Equal(engine.Score, restored.Score);
        Assert.Equal(engine.Momentum, restored.Momentum);
        Assert.Equal(2, restored.FocusChapter);
        Assert.Equal(3, restored.Nodes.Count);
        Assert.Equal(RogueNodeState.Cleared, restored.NodeById("L0-0")!.State);
        Assert.Equal(RogueNodeState.Available, restored.NodeById("L1-0")!.State);
    }

    [Fact]
    public void ApplySnapshot_NormalisesChallengePhaseToMap()
    {
        var snap = new Pythontopia.Core.Dto.ExpeditionRunSnapshot
        {
            Phase = RogueRunPhase.Challenge,
            Nodes = new List<RogueNode> { Node("L0-0", 0, 0, RogueNodeKind.Battle, 2, RogueNodeState.Available) },
            Layers = 1,
            Hp = 4,
            MaxHp = 5,
        };
        var engine = new RogueRunEngine(TestRng.Constant(0));
        engine.ApplySnapshot(snap);
        Assert.Equal(RogueRunPhase.Map, engine.Phase); // mid-challenge resumes safely on the map
    }

    [Fact]
    public void StarReport_GateRespectsFocusAndThreshold()
    {
        var engine = new RogueRunEngine(TestRng.Constant(0));

        engine.StartRun(Linear(RogueNodeKind.Battle, RogueNodeKind.Battle), focus: null);
        Assert.False(engine.TryBeginStarReport(out _, out _, out _, out _)); // no focus chapter

        engine.StartRun(Linear(RogueNodeKind.Battle, RogueNodeKind.Battle), focus: 1);
        Assert.False(engine.TryBeginStarReport(out _, out _, out _, out _)); // 0 attempts
    }

    [Fact]
    public void Abort_ReturnsToIdle()
    {
        var engine = new RogueRunEngine(TestRng.Constant(0));
        engine.StartRun(Linear(RogueNodeKind.Battle, RogueNodeKind.Battle), focus: 1);
        engine.Abort();
        Assert.Equal(RogueRunPhase.Idle, engine.Phase);
    }

    [Fact]
    public void IsResumablePhase_OnlyMidRunPhases()
    {
        Assert.True(RogueRunEngine.IsResumablePhase(RogueRunPhase.Map));
        Assert.True(RogueRunEngine.IsResumablePhase(RogueRunPhase.Challenge));
        Assert.True(RogueRunEngine.IsResumablePhase(RogueRunPhase.Event));
        Assert.False(RogueRunEngine.IsResumablePhase(RogueRunPhase.Idle));
        Assert.False(RogueRunEngine.IsResumablePhase(RogueRunPhase.Cleared));
        Assert.False(RogueRunEngine.IsResumablePhase(RogueRunPhase.Over));
    }
}
