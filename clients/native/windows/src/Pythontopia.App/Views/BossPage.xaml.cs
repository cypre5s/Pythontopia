using Microsoft.UI.Xaml.Controls;
using Pythontopia.App.ViewModels;

namespace Pythontopia.App.Views;

public sealed partial class BossPage : Page
{
    public BossViewModel ViewModel { get; }

    public BossPage()
    {
        ViewModel = App.GetService<BossViewModel>();
        InitializeComponent();
        DataContext = ViewModel;
    }
}
