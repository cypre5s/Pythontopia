using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using Pythontopia.App.ViewModels;

namespace Pythontopia.App.Views;

public sealed partial class GraduationPage : Page
{
    public GraduationViewModel ViewModel { get; }

    public GraduationPage()
    {
        ViewModel = App.GetService<GraduationViewModel>();
        InitializeComponent();
        DataContext = ViewModel;
    }

    protected override void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        _ = ViewModel.LoadAsync();
    }

    private void OnGoTown(object sender, RoutedEventArgs e) => Frame?.Navigate(typeof(TownPage));

    private void OnGoProfileRoom(object sender, RoutedEventArgs e) => Frame?.Navigate(typeof(ProfileRoomPage));

    private void OnGoMainline(object sender, RoutedEventArgs e) => Frame?.Navigate(typeof(MainlinePage));
}
