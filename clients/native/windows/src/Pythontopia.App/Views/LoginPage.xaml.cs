using Microsoft.UI.Xaml.Controls;
using Pythontopia.App.ViewModels;

namespace Pythontopia.App.Views;

public sealed partial class LoginPage : Page
{
    public LoginViewModel ViewModel { get; }

    public LoginPage()
    {
        ViewModel = App.GetService<LoginViewModel>();
        InitializeComponent();
    }
}
