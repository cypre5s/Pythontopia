using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using Pythontopia.App.ViewModels;

namespace Pythontopia.App.Views;

public sealed partial class LecturePage : Page
{
    public LectureViewModel ViewModel { get; }

    public LecturePage()
    {
        ViewModel = App.GetService<LectureViewModel>();
        InitializeComponent();
        DataContext = ViewModel;
    }

    protected override void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        _ = ViewModel.LoadAsync();
    }

    private void OnGoTraining(object sender, RoutedEventArgs e) => Frame?.Navigate(typeof(PracticePage));

    private void OnGoLibrary(object sender, RoutedEventArgs e) => Frame?.Navigate(typeof(LibraryPage));

    private void OnGoTown(object sender, RoutedEventArgs e) => Frame?.Navigate(typeof(TownPage));
}
