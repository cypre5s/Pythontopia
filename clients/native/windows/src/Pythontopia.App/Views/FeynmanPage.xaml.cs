using Microsoft.UI.Xaml.Controls;
using Pythontopia.App.ViewModels;

namespace Pythontopia.App.Views;

public sealed partial class FeynmanPage : Page
{
    public FeynmanViewModel ViewModel { get; }

    public FeynmanPage()
    {
        ViewModel = App.GetService<FeynmanViewModel>();
        InitializeComponent();
        DataContext = ViewModel;
    }
}
