using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the B 站精选 page: keyword search over external Bilibili explainer videos
/// (GET /api/videos/bilibili?keyword=). Results open in the system browser.
/// </summary>
public partial class BilibiliViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    [ObservableProperty]
    private string _keyword = "for 循环";

    public ObservableCollection<BilibiliVideoVm> Videos { get; } = new();

    public BilibiliViewModel(IApiClient api) => _api = api;

    [RelayCommand]
    private Task Search() => LoadAsync();

    public async Task LoadAsync()
    {
        if (string.IsNullOrWhiteSpace(Keyword))
        {
            ErrorMessage = "请输入搜索关键词";
            return;
        }

        ClearError();
        IsBusy = true;
        try
        {
            var videos = await _api.SearchBilibiliVideosAsync(Keyword.Trim(), limit: 10);
            Videos.Clear();
            foreach (var video in videos)
            {
                Videos.Add(BilibiliVideoVm.From(video));
            }
        }
        catch (Exception ex)
        {
            SetError(ex);
        }
        finally
        {
            IsBusy = false;
        }
    }
}

public sealed record BilibiliVideoVm(string Title, string Meta, string Summary, Uri? Link)
{
    public static BilibiliVideoVm From(BilibiliVideoDto d)
    {
        var minutes = d.DurationSec / 60;
        var seconds = d.DurationSec % 60;
        var meta = $"{d.Author} · {minutes}:{seconds:D2} · 播放 {d.Play} · 弹幕 {d.Danmaku}";
        var link = Uri.TryCreate(d.Url, UriKind.Absolute, out var uri) ? uri : null;
        return new BilibiliVideoVm(
            string.IsNullOrWhiteSpace(d.Title) ? "(无标题)" : d.Title!,
            meta,
            d.AiSummary ?? d.Description ?? string.Empty,
            link);
    }
}
