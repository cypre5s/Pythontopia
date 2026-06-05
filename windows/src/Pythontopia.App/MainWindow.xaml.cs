using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Pythontopia.App.Views;
using Pythontopia.Core.Auth;

namespace Pythontopia.App;

public sealed partial class MainWindow : Window
{
    private readonly SessionService _session;

    public MainWindow()
    {
        InitializeComponent();
        Title = "Pythontopia";
        _session = App.GetService<SessionService>();
        _session.SessionChanged += OnSessionChanged;
        UpdateForSession();
    }

    private void OnSessionChanged(object? sender, EventArgs e)
        => DispatcherQueue.TryEnqueue(UpdateForSession);

    private void UpdateForSession()
    {
        if (_session.IsAuthenticated)
        {
            Nav.IsPaneVisible = true;
            ContentFrame.Navigate(typeof(TownPage));
            Nav.SelectedItem = Nav.MenuItems.OfType<NavigationViewItem>().FirstOrDefault();
        }
        else
        {
            Nav.IsPaneVisible = false;
            Nav.SelectedItem = null;
            ContentFrame.Navigate(typeof(LoginPage));
        }
    }

    private void Nav_ItemInvoked(NavigationView sender, NavigationViewItemInvokedEventArgs args)
    {
        if (args.InvokedItemContainer?.Tag is not string tag)
        {
            return;
        }

        if (tag == "Logout")
        {
            _session.Logout();
            return;
        }

        var pageType = tag switch
        {
            "TownPage" => typeof(TownPage),
            "ProfilePage" => typeof(ProfilePage),
            "QuestsPage" => typeof(QuestsPage),
            "CoursePage" => typeof(CoursePage),
            "PracticePage" => typeof(PracticePage),
            "ClassroomPage" => typeof(ClassroomPage),
            _ => null,
        };

        if (pageType is not null)
        {
            ContentFrame.Navigate(pageType);
        }
    }
}
