using CommunityToolkit.Mvvm.ComponentModel;
using Pythontopia.Core.Api;

namespace Pythontopia.App.ViewModels;

public abstract partial class ViewModelBase : ObservableObject
{
    [ObservableProperty]
    private bool _isBusy;

    [ObservableProperty]
    private string? _errorMessage;

    public bool HasError => !string.IsNullOrEmpty(ErrorMessage);

    protected void ClearError() => ErrorMessage = null;

    partial void OnErrorMessageChanged(string? value) => OnPropertyChanged(nameof(HasError));

    protected void SetError(Exception ex) =>
        ErrorMessage = ex switch
        {
            ApiException api when api.Details.Count > 0 => $"{api.Message}（{string.Join("；", api.Details)}）",
            ApiException api => api.Message,
            _ => ex.Message,
        };
}
