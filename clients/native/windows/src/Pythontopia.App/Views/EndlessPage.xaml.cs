using Microsoft.UI.Xaml.Controls;
using Pythontopia.App.ViewModels;

namespace Pythontopia.App.Views;

public sealed partial class EndlessPage : Page
{
    public EndlessViewModel ViewModel { get; }

    public EndlessPage()
    {
        ViewModel = App.GetService<EndlessViewModel>();
        InitializeComponent();
        DataContext = ViewModel;
    }
}
