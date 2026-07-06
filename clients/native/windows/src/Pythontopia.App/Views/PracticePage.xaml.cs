using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using Pythontopia.App.ViewModels;

namespace Pythontopia.App.Views;

public sealed partial class PracticePage : Page
{
    public PracticeViewModel ViewModel { get; }

    public PracticePage()
    {
        ViewModel = App.GetService<PracticeViewModel>();
        InitializeComponent();
        DataContext = ViewModel;
    }

    protected override async void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        await ViewModel.LoadAsync();
    }
}
