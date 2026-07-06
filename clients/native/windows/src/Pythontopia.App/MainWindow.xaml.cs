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
        ("GuidePage", typeof(GuidePage), "新手引导"),
        ("TownPage", typeof(TownPage), "像素小镇"),
        ("MainlinePage", typeof(MainlinePage), "主线地图"),
        ("PathPage", typeof(PathPage), "学习路径"),
        ("ProfilePage", typeof(ProfilePage), "学习画像"),
        ("ProfileRoomPage", typeof(ProfileRoomPage), "学习档案室"),
        ("QuestsPage", typeof(QuestsPage), "任务公告板"),
        ("CoursePage", typeof(CoursePage), "课程"),
        ("ExamplesPage", typeof(ExamplesPage), "例题库"),
        ("LibraryPage", typeof(LibraryPage), "课件库"),
        ("LecturePage", typeof(LecturePage), "视频讲解"),
        ("BilibiliPage", typeof(BilibiliPage), "B 站精选"),
        ("PracticePage", typeof(PracticePage), "训练场"),
        ("ChapterTestPage", typeof(ChapterTestPage), "章节测验"),
        ("FeynmanPage", typeof(FeynmanPage), "费曼学弟"),
        ("WorkshopPage", typeof(WorkshopPage), "百晓工坊"),
        ("MediaPage", typeof(MediaPage), "AI 配图"),
        ("BugMinePage", typeof(BugMinePage), "Bug 矿洞"),
        ("BossPage", typeof(BossPage), "Boss 副本"),
        ("EndlessPage", typeof(EndlessPage), "无尽模式"),
        ("ExpeditionPage", typeof(ExpeditionPage), "无尽远征"),
        ("ReviewHallPage", typeof(ReviewHallPage), "复盘大厅"),
        ("InsightsPage", typeof(InsightsPage), "学情洞察"),
        ("GraduationPage", typeof(GraduationPage), "毕业典礼"),
        ("ClassroomPage", typeof(ClassroomPage), "班级"),
        ("CalendarPage", typeof(CalendarPage), "世界日历"),
        ("SavesPage", typeof(SavesPage), "存档"),
        ("SkillsPage", typeof(SkillsPage), "技能树"),
        ("NpcPage", typeof(NpcPage), "NPC 互动"),
        ("CreditsPage", typeof(CreditsPage), "致谢"),
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
        CenterOnScreen();

        _session = App.GetService<SessionService>();
        _session.SessionChanged += OnSessionChanged;
        UpdateForSession();
    }

    /// <summary>Center the window on the primary display's work area for a polished first launch.</summary>
    private void CenterOnScreen()
    {
        var area = Microsoft.UI.Windowing.DisplayArea.GetFromWindowId(
            AppWindow.Id, Microsoft.UI.Windowing.DisplayAreaFallback.Primary);
        if (area is null)
        {
            return;
        }
        var x = area.WorkArea.X + ((area.WorkArea.Width - AppWindow.Size.Width) / 2);
        var y = area.WorkArea.Y + ((area.WorkArea.Height - AppWindow.Size.Height) / 2);
        AppWindow.Move(new Windows.Graphics.PointInt32(x, y));
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
