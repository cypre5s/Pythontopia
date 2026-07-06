using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Navigation;
using Microsoft.UI.Xaml.Shapes;
using Pythontopia.App.ViewModels;

namespace Pythontopia.App.Views;

public sealed partial class ExpeditionPage : Page
{
    private static readonly SolidColorBrush EdgeOpenBrush = new(Windows.UI.Color.FromArgb(0xCC, 0xD9, 0x9F, 0x3A));
    private static readonly SolidColorBrush EdgeDimBrush = new(Windows.UI.Color.FromArgb(0x55, 0x8A, 0x7A, 0x5A));
    private static readonly SolidColorBrush ClearedBrush = new(Colors.LightGreen);
    private static readonly SolidColorBrush GoldBrush = new(Colors.Goldenrod);

    private readonly DispatcherTimer _timer;

    public ExpeditionViewModel ViewModel { get; }

    public ExpeditionPage()
    {
        ViewModel = App.GetService<ExpeditionViewModel>();
        InitializeComponent();
        DataContext = ViewModel;

        // 1s clock that advances the in-challenge timer (the view-model gates it by phase).
        _timer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
        _timer.Tick += (_, _) => ViewModel.TickSecond();

        ViewModel.TowerInvalidated += OnTowerInvalidated;

        Loaded += (_, _) =>
        {
            _timer.Start();
            RebuildTower();
        };
        Unloaded += (_, _) =>
        {
            _timer.Stop();
            ViewModel.TowerInvalidated -= OnTowerInvalidated;
        };
    }

    protected override void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        _ = ViewModel.OnNavigatedToAsync();
    }

    private void OnTowerInvalidated(object? sender, EventArgs e) => RebuildTower();

    /// <summary>Rebuilds the pixel tower canvas from the view-model's node/edge geometry.</summary>
    private void RebuildTower()
    {
        if (TowerCanvas is null)
        {
            return;
        }
        TowerCanvas.Children.Clear();

        // Edges first so node boxes paint on top.
        foreach (var edge in ViewModel.TowerEdges)
        {
            TowerCanvas.Children.Add(new Line
            {
                X1 = edge.X1,
                Y1 = edge.Y1,
                X2 = edge.X2,
                Y2 = edge.Y2,
                StrokeThickness = 2,
                Stroke = edge.Open ? EdgeOpenBrush : EdgeDimBrush,
            });
        }

        foreach (var node in ViewModel.TowerNodes)
        {
            var content = new StackPanel { Spacing = 1 };
            var header = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4 };
            header.Children.Add(new TextBlock { Text = node.Icon, FontSize = 13 });
            header.Children.Add(new TextBlock { Text = node.KindLabel, FontSize = 12, FontWeight = FontWeights.SemiBold });
            if (node.IsCleared)
            {
                header.Children.Add(new TextBlock { Text = "✓", FontSize = 12, Foreground = ClearedBrush, FontWeight = FontWeights.Bold });
            }
            content.Children.Add(header);
            if (node.KcName.Length > 0)
            {
                content.Children.Add(new TextBlock { Text = node.KcName, FontSize = 10, Opacity = 0.75, TextTrimming = TextTrimming.CharacterEllipsis });
            }
            if (node.DifficultyStars.Length > 0)
            {
                content.Children.Add(new TextBlock { Text = node.DifficultyStars, FontSize = 10, Foreground = GoldBrush });
            }

            var button = new Button
            {
                Width = node.Width,
                Height = node.Height,
                Padding = new Thickness(6, 4, 6, 4),
                Opacity = node.Opacity,
                Command = node.EnterCommand,
                HorizontalContentAlignment = HorizontalAlignment.Left,
                VerticalContentAlignment = VerticalAlignment.Center,
                Content = content,
            };
            if (node.IsBoss)
            {
                button.BorderBrush = GoldBrush;
                button.BorderThickness = new Thickness(2);
            }

            Canvas.SetLeft(button, node.X);
            Canvas.SetTop(button, node.Y);
            TowerCanvas.Children.Add(button);
        }
    }
}
