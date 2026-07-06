using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the 存档 page: lists the learner's RPG save slots (GET /api/world/saves) and lets
/// the player activate one (POST /api/world/saves/{slotIndex}/activate).
/// </summary>
public partial class SavesViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    public ObservableCollection<SaveVm> Saves { get; } = new();

    public SavesViewModel(IApiClient api) => _api = api;

    [RelayCommand]
    private Task Refresh() => LoadAsync();

    public async Task LoadAsync()
    {
        ClearError();
        IsBusy = true;
        try
        {
            var slots = await _api.GetSaveSlotsAsync();
            Saves.Clear();
            foreach (var slot in slots)
            {
                Saves.Add(SaveVm.From(slot));
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
    private async Task Activate(int slotIndex)
    {
        ClearError();
        IsBusy = true;
        try
        {
            await _api.ActivateSaveSlotAsync(slotIndex);
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

public sealed record SaveVm(int SlotIndex, string Title, string Meta, bool IsActive)
{
    public static SaveVm From(SaveSlotDto d)
    {
        var minutes = d.AccumulatedPlaySeconds / 60;
        var meta = $"槽位 {d.SlotIndex} · 场景 {d.SceneName} · 游玩 {minutes} 分钟 · 上次 {d.LastPlayedAt:yyyy-MM-dd}";
        var title = string.IsNullOrWhiteSpace(d.DisplayName) ? $"存档 {d.SlotIndex}" : d.DisplayName!;
        return new SaveVm(d.SlotIndex, title, meta, d.IsActive);
    }
}
