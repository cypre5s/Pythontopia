using Microsoft.UI.Xaml.Controls;
using Pythontopia.App.ViewModels;

namespace Pythontopia.App.Views;

public sealed partial class BilibiliPage : Page
{
    public BilibiliViewModel ViewModel { get; }

    public BilibiliPage()
    {
        ViewModel = App.GetService<BilibiliViewModel>();
        InitializeComponent();
        DataContext = ViewModel;
    }
}
