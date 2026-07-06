using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using Pythontopia.App.ViewModels;

namespace Pythontopia.App.Views;

public sealed partial class WorkshopPage : Page
{
    public WorkshopViewModel ViewModel { get; }

    public WorkshopPage()
    {
        ViewModel = App.GetService<WorkshopViewModel>();
        InitializeComponent();
        DataContext = ViewModel;
        ViewModel.ReviewRequested += OnReviewRequested;
    }

    private void OnReviewRequested(object? sender, EventArgs e)
        => Frame?.Navigate(typeof(BugMinePage));

    protected override async void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        await ViewModel.LoadAsync();
    }
}
