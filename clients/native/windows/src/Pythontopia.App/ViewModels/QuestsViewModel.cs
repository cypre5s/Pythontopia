using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;

namespace Pythontopia.App.ViewModels;

public partial class QuestsViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    public ObservableCollection<QuestDto> Today { get; } = new();
    public ObservableCollection<QuestDto> All { get; } = new();

    public QuestsViewModel(IApiClient api) => _api = api;

    [RelayCommand]
    private Task Refresh() => LoadAsync();

    public async Task LoadAsync()
    {
        ClearError();
        IsBusy = true;
        try
        {
            var today = await _api.GetTodayQuestsAsync();
            Today.Clear();
            foreach (var quest in today)
            {
                Today.Add(quest);
            }

            var all = await _api.GetQuestsAsync(limit: 30);
            All.Clear();
            foreach (var quest in all)
            {
                All.Add(quest);
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
    private Task StartAsync(QuestDto quest) => RunAsync(() => _api.StartQuestAsync(quest.Id));

    [RelayCommand]
    private Task CompleteAsync(QuestDto quest) => RunAsync(() => _api.CompleteQuestAsync(quest.Id));

    [RelayCommand]
    private Task SkipAsync(QuestDto quest) => RunAsync(() => _api.SkipQuestAsync(quest.Id));

    private async Task RunAsync(Func<Task<QuestDto>> action)
    {
        ClearError();
        try
        {
            IsBusy = true;
            await action();
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
