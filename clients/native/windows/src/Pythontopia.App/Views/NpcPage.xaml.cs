using Microsoft.UI.Xaml.Controls;
using Pythontopia.App.ViewModels;

namespace Pythontopia.App.Views;

public sealed partial class NpcPage : Page
{
    public NpcViewModel ViewModel { get; }

    public NpcPage()
    {
        ViewModel = App.GetService<NpcViewModel>();
        InitializeComponent();
        DataContext = ViewModel;
    }
}
