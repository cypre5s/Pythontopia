using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using Pythontopia.App.ViewModels;

namespace Pythontopia.App.Views;

public sealed partial class BugMinePage : Page
{
    public BugMineViewModel ViewModel { get; }

    public BugMinePage()
    {
        ViewModel = App.GetService<BugMineViewModel>();
        InitializeComponent();
        DataContext = ViewModel;
    }

    protected override async void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        await ViewModel.LoadAsync();
    }
}
