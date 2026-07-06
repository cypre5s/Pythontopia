using Microsoft.UI.Xaml.Controls;
using Pythontopia.App.ViewModels;

namespace Pythontopia.App.Views;

public sealed partial class TownPage : Page
{
    public TownViewModel ViewModel { get; }

    public TownPage()
    {
        ViewModel = App.GetService<TownViewModel>();
        InitializeComponent();
        DataContext = ViewModel;
    }
}
