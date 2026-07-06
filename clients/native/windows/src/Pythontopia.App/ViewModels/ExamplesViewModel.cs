using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the 例题库 (worked-examples) page: lists course-pack examples and shows the selected
/// example's code + explanation. Backed by GET /api/course/examples (+/{id}).
/// </summary>
public partial class ExamplesViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    public ObservableCollection<CourseExampleDto> Examples { get; } = new();

    [ObservableProperty]
    private CourseExampleDto? _selected;

    public ExamplesViewModel(IApiClient api) => _api = api;

    [RelayCommand]
    private Task Refresh() => LoadAsync();

    public async Task LoadAsync()
    {
        ClearError();
        IsBusy = true;
        try
        {
            var list = await _api.GetExamplesAsync();
            Examples.Clear();
            foreach (var example in list)
            {
                Examples.Add(example);
            }
            Selected ??= Examples.FirstOrDefault();
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
