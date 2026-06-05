using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Auth;

namespace Pythontopia.App.ViewModels;

public partial class LoginViewModel : ViewModelBase
{
    private readonly SessionService _session;

    [ObservableProperty]
    private string _username = string.Empty;

    [ObservableProperty]
    private string _password = string.Empty;

    [ObservableProperty]
    private string _displayName = string.Empty;

    [ObservableProperty]
    private bool _isRegisterMode;

    public LoginViewModel(SessionService session) => _session = session;

    public string SubmitLabel => IsRegisterMode ? "注册并登录" : "登录";
    public string ToggleLabel => IsRegisterMode ? "已有账号？去登录" : "没有账号？去注册";

    partial void OnIsRegisterModeChanged(bool value)
    {
        OnPropertyChanged(nameof(SubmitLabel));
        OnPropertyChanged(nameof(ToggleLabel));
    }

    [RelayCommand]
    private void ToggleMode()
    {
        IsRegisterMode = !IsRegisterMode;
        ClearError();
    }

    [RelayCommand]
    private async Task SubmitAsync()
    {
        ClearError();

        if (string.IsNullOrWhiteSpace(Username) || string.IsNullOrWhiteSpace(Password))
        {
            ErrorMessage = "请输入用户名和密码";
            return;
        }

        try
        {
            IsBusy = true;
            var username = Username.Trim();
            if (IsRegisterMode)
            {
                var display = string.IsNullOrWhiteSpace(DisplayName) ? username : DisplayName.Trim();
                await _session.RegisterAsync(username, Password, display);
            }
            else
            {
                await _session.LoginAsync(username, Password);
            }

            Password = string.Empty;
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
