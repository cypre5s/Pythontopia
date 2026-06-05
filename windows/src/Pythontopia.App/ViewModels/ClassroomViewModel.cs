using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;

namespace Pythontopia.App.ViewModels;

public partial class ClassroomViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    [ObservableProperty]
    private string _joinCode = string.Empty;

    public ObservableCollection<ClassroomDto> MyClassrooms { get; } = new();
    public ObservableCollection<ClassroomQuestDto> ClassQuests { get; } = new();

    public ClassroomViewModel(IApiClient api) => _api = api;

    public async Task LoadAsync()
    {
        ClearError();
        IsBusy = true;
        try
        {
            var mine = await _api.GetMyClassroomsAsync();
            MyClassrooms.Clear();
            foreach (var classroom in mine)
            {
                MyClassrooms.Add(classroom);
            }

            var quests = await _api.GetMyClassroomQuestsAsync();
            ClassQuests.Clear();
            foreach (var quest in quests)
            {
                ClassQuests.Add(quest);
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
    private async Task JoinAsync()
    {
        if (string.IsNullOrWhiteSpace(JoinCode))
        {
            ErrorMessage = "请输入班级邀请码";
            return;
        }

        ClearError();
        try
        {
            IsBusy = true;
            await _api.JoinClassroomAsync(JoinCode.Trim());
            JoinCode = string.Empty;
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
