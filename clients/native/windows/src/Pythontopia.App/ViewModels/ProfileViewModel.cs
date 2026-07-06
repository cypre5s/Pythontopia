using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Auth;
using Pythontopia.Core.Dto;

namespace Pythontopia.App.ViewModels;

public partial class ProfileViewModel : ViewModelBase
{
    private readonly IApiClient _api;
    private readonly SessionService _session;

    [ObservableProperty]
    private LearnerProfileDto? _profile;

    [ObservableProperty]
    private bool _hasProfile;

    public ObservableCollection<ProfileDimensionDto> Dimensions { get; } = new();
    public ObservableCollection<MasterySnapshotDto> Mastery { get; } = new();

    public ProfileViewModel(IApiClient api, SessionService session)
    {
        _api = api;
        _session = session;
    }

    public string DisplayName => _session.CurrentUser?.DisplayName ?? _session.CurrentUser?.Username ?? "—";
    public string Role => _session.CurrentUser?.Role ?? "—";

    public async Task LoadAsync()
    {
        ClearError();
        IsBusy = true;
        try
        {
            Profile = await _api.GetProfileAsync();
            HasProfile = Profile is not null;

            var dimensions = await _api.GetProfileDimensionsAsync();
            Dimensions.Clear();
            foreach (var dimension in dimensions)
            {
                Dimensions.Add(dimension);
            }

            var mastery = await _api.GetMasteryAsync();
            Mastery.Clear();
            foreach (var snapshot in mastery.OrderByDescending(m => m.MasteryScore))
            {
                Mastery.Add(snapshot);
            }

            OnPropertyChanged(nameof(DisplayName));
            OnPropertyChanged(nameof(Role));
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
    private Task IncreaseAsync(ProfileDimensionDto dimension) => AdjustAsync(dimension, 0.1);

    [RelayCommand]
    private Task DecreaseAsync(ProfileDimensionDto dimension) => AdjustAsync(dimension, -0.1);

    private async Task AdjustAsync(ProfileDimensionDto dimension, double delta)
    {
        if (dimension is null || !dimension.SelfEditable)
        {
            return;
        }

        ClearError();
        try
        {
            IsBusy = true;
            await _api.UpdateProfileDimensionAsync(dimension.Key, new DimensionSelfUpdateRequest(delta));
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
