using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the 技能树 page: lists skill nodes with unlock state (GET /api/world/skills) and lets
/// the learner unlock an eligible skill (POST /api/world/skills/{key}/unlock).
/// </summary>
public partial class SkillsViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    public ObservableCollection<SkillVm> Skills { get; } = new();

    public SkillsViewModel(IApiClient api) => _api = api;

    [RelayCommand]
    private Task Refresh() => LoadAsync();

    public async Task LoadAsync()
    {
        ClearError();
        IsBusy = true;
        try
        {
            var response = await _api.GetSkillsAsync();
            var unlocked = response.Unlocked.ToDictionary(u => u.SkillKey);
            Skills.Clear();
            foreach (var node in response.Nodes.OrderBy(n => n.Tier))
            {
                Skills.Add(SkillVm.From(node, unlocked.TryGetValue(node.SkillKey, out var u) ? u : null));
            }
        }
        catch (Exception ex)
        {
            SetError(ex);
        }
        finally
        {
            IsBusy = false;
        }
    }

    [RelayCommand]
    private async Task Unlock(string skillKey)
    {
        ClearError();
        IsBusy = true;
        try
        {
            await _api.UnlockSkillAsync(skillKey);
            await LoadAsync();
        }
        catch (Exception ex)
        {
            SetError(ex);
        }
        finally
        {
            IsBusy = false;
        }
    }
}

public sealed record SkillVm(string SkillKey, string Title, string Meta, bool Unlocked, bool CanUnlock)
{
    public static SkillVm From(SkillNodeDto node, LearnerSkillDto? unlocked)
    {
        var isUnlocked = unlocked is not null;
        var meta = $"Tier {node.Tier}";
        if (node.KcCodes is { Count: > 0 } kcs)
        {
            meta += " · KC " + string.Join(", ", kcs);
        }
        meta += isUnlocked ? $" · 已解锁 · 熟练度 {unlocked!.Proficiency}" : " · 未解锁";
        if (!string.IsNullOrWhiteSpace(node.Description))
        {
            meta += " · " + node.Description;
        }
        return new SkillVm(node.SkillKey, node.DisplayName ?? node.SkillKey, meta, isUnlocked, !isUnlocked);
    }
}
