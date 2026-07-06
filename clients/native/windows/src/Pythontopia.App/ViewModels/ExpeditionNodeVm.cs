using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Expedition;

namespace Pythontopia.App.ViewModels;

/// <summary>A single expedition map node, projected for the map ItemsControl.</summary>
public sealed class ExpeditionNodeVm
{
    public ExpeditionNodeVm(RogueNode node, Action<string> onEnter)
    {
        Id = node.Id;
        State = node.State;
        Kind = node.Kind;
        Icon = RogueConfig.KindIcon(node.Kind);
        KindLabel = RogueConfig.KindLabel(node.Kind);
        KcName = node.KcCode is null ? string.Empty : KcNames.DisplayName(node.KcCode);
        DifficultyStars = node.Difficulty > 0
            ? new string('★', node.Difficulty) + new string('☆', Math.Max(0, 5 - node.Difficulty))
            : string.Empty;
        CanEnter = node.State == RogueNodeState.Available;
        IsCleared = node.State == RogueNodeState.Cleared;
        DoneGlyph = IsCleared ? "✓" : string.Empty;
        // Dim locked / bypassed nodes; highlight the playable ones.
        Opacity = node.State switch
        {
            RogueNodeState.Locked => 0.5,
            RogueNodeState.Bypassed => 0.3,
            _ => 1.0,
        };
        EnterCommand = new RelayCommand(() => onEnter(Id), () => CanEnter);
    }

    public string Id { get; }
    public string State { get; }
    public string Kind { get; }
    public string Icon { get; }
    public string KindLabel { get; }
    public string KcName { get; }
    public string DifficultyStars { get; }
    public bool CanEnter { get; }
    public bool IsCleared { get; }
    public string DoneGlyph { get; }
    public double Opacity { get; }
    public bool HasKc => KcName.Length > 0;
    public bool HasDifficulty => DifficultyStars.Length > 0;
    public IRelayCommand EnterCommand { get; }
}

/// <summary>One layer (row) of the expedition map.</summary>
public sealed class ExpeditionLayerVm
{
    public ExpeditionLayerVm(string tag, IReadOnlyList<ExpeditionNodeVm> nodes)
    {
        Tag = tag;
        Nodes = nodes;
    }

    public string Tag { get; }
    public IReadOnlyList<ExpeditionNodeVm> Nodes { get; }
}

/// <summary>A node positioned on the pixel expedition tower canvas (layer 0 at the bottom, boss at top).</summary>
public sealed class ExpeditionTowerNodeVm
{
    public ExpeditionTowerNodeVm(RogueNode node, double x, double y, double width, double height, Action<string> onEnter)
    {
        Id = node.Id;
        State = node.State;
        Kind = node.Kind;
        Icon = RogueConfig.KindIcon(node.Kind);
        KindLabel = RogueConfig.KindLabel(node.Kind);
        KcName = node.KcCode is null ? string.Empty : KcNames.DisplayName(node.KcCode);
        DifficultyStars = node.Difficulty > 0 ? new string('★', Math.Min(5, node.Difficulty)) : string.Empty;
        X = x;
        Y = y;
        Width = width;
        Height = height;
        CanEnter = node.State == RogueNodeState.Available;
        IsBoss = node.Kind == RogueNodeKind.Boss;
        IsCleared = node.State == RogueNodeState.Cleared;
        Opacity = node.State switch
        {
            RogueNodeState.Locked => 0.45,
            RogueNodeState.Bypassed => 0.3,
            _ => 1.0,
        };
        EnterCommand = new RelayCommand(() => onEnter(Id), () => CanEnter);
    }

    public string Id { get; }
    public string State { get; }
    public string Kind { get; }
    public string Icon { get; }
    public string KindLabel { get; }
    public string KcName { get; }
    public string DifficultyStars { get; }
    public double X { get; }
    public double Y { get; }
    public double Width { get; }
    public double Height { get; }
    public bool CanEnter { get; }
    public bool IsBoss { get; }
    public bool IsCleared { get; }
    public double Opacity { get; }
    public IRelayCommand EnterCommand { get; }
}

/// <summary>A directed edge segment between two tower nodes.</summary>
public sealed record ExpeditionTowerEdgeVm(double X1, double Y1, double X2, double Y2, bool Open);

/// <summary>Focus-chapter option for the start screen dropdown.</summary>
public sealed class ExpeditionFocusOption
{
    public ExpeditionFocusOption(int? chapter, string label)
    {
        Chapter = chapter;
        Label = label;
    }

    public int? Chapter { get; }
    public string Label { get; }
}
