using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using Pythontopia.App.ViewModels;

namespace Pythontopia.App.Views;

public sealed partial class CalendarPage : Page
{
    public CalendarViewModel ViewModel { get; }

    public CalendarPage()
    {
        ViewModel = App.GetService<CalendarViewModel>();
        InitializeComponent();
        DataContext = ViewModel;
    }

    protected override async void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        await ViewModel.LoadAsync();
    }
}
