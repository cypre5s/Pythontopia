using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Pythontopia.App.Views;
using Pythontopia.Core.Auth;

namespace Pythontopia.App;

public sealed partial class MainWindow : Window
{
    private readonly SessionService _session;

    /// <summary>Navigation tag -> page type + header title shown in the shell.</summary>
    private static readonly (string Tag, Type Page, string Title)[] Destinations =
    {
        ("TownPage", typeof(TownPage), "像素小镇"),
        ("MainlinePage", typeof(MainlinePage), "主线地图"),
        ("PathPage", typeof(PathPage), "学习路径"),
        ("ProfilePage", typeof(ProfilePage), "学习画像"),
        ("QuestsPage", typeof(QuestsPage), "任务公告板"),
        ("CoursePage", typeof(CoursePage), "课程"),
        ("ExamplesPage", typeof(ExamplesPage), "例题库"),
        ("LibraryPage", typeof(LibraryPage), "课件库"),
        ("BilibiliPage", typeof(BilibiliPage), "B 站精选"),
        ("PracticePage", typeof(PracticePage), "训练场"),
        ("ChapterTestPage", typeof(ChapterTestPage), "章节测验"),
        ("FeynmanPage", typeof(FeynmanPage), "费曼学弟"),
        ("WorkshopPage", typeof(WorkshopPage), "百晓工坊"),
        ("BugMinePage", typeof(BugMinePage), "Bug 矿洞"),
        ("BossPage", typeof(BossPage), "Boss 副本"),
        ("EndlessPage", typeof(EndlessPage), "无尽模式"),
        ("ReviewHallPage", typeof(ReviewHallPage), "复盘大厅"),
        ("InsightsPage", typeof(InsightsPage), "学情洞察"),
        ("ClassroomPage", typeof(ClassroomPage), "班级"),
        ("CalendarPage", typeof(CalendarPage), "世界日历"),
        ("SavesPage", typeof(SavesPage), "存档"),
        ("SkillsPage", typeof(SkillsPage), "技能树"),
        ("NpcPage", typeof(NpcPage), "NPC 互动"),
    };

    public MainWindow()
    {
        InitializeComponent();
        Title = "Pythontopia";

        // Native shell: Mica system backdrop (falls back gracefully pre-Win11) plus a
        // custom draggable title bar so the content extends edge-to-edge.
        SystemBackdrop = new MicaBackdrop();
        ExtendsContentIntoTitleBar = true;
        SetTitleBar(AppTitleBar);
        AppWindow.Resize(new Windows.Graphics.SizeInt32(1280, 820));

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
            UserPanel.Visibility = Visibility.Visible;
            UserText.Text = _session.CurrentUser?.DisplayName
                ?? _session.CurrentUser?.Username
                ?? string.Empty;
            NavigateTo("TownPage");
            Nav.SelectedItem = Nav.MenuItems.OfType<NavigationViewItem>().FirstOrDefault();
        }
        else
        {
            Nav.IsPaneVisible = false;
            Nav.SelectedItem = null;
            Nav.Header = null;
            UserPanel.Visibility = Visibility.Collapsed;
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

        NavigateTo(tag);
    }

    private void NavigateTo(string tag)
    {
        var match = Destinations.FirstOrDefault(d => d.Tag == tag);
        if (match.Page is null)
        {
            return;
        }

        ContentFrame.Navigate(match.Page);
        Nav.Header = match.Title;
    }
}
