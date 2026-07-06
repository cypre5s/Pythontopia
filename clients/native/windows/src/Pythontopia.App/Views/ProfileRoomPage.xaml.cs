using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using Pythontopia.App.ViewModels;

namespace Pythontopia.App.Views;

public sealed partial class ProfileRoomPage : Page
{
    public ProfileRoomViewModel ViewModel { get; }

    public ProfileRoomPage()
    {
        ViewModel = App.GetService<ProfileRoomViewModel>();
        InitializeComponent();
        DataContext = ViewModel;
    }

    protected override void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        _ = ViewModel.LoadAsync();
    }
}
