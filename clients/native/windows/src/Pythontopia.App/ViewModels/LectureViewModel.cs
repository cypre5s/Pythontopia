using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using Pythontopia.Core.Api;
using Pythontopia.Core.Expedition;
using Windows.Media.Core;
using Windows.Media.Playback;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the 章节视频讲解 (chapter lecture) page: lists a chapter's course videos and plays the
/// selected one via the public stream URL, falling back to Bilibili explainer cards when a chapter
/// has no online video — mirroring the web LecturePage's video/PPT/Bilibili learning loop.
/// </summary>
public sealed partial class LectureViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    public IReadOnlyList<int> ChapterOptions { get; } = Enumerable.Range(1, 7).ToList();
    public ObservableCollection<LectureVideoVm> Videos { get; } = new();
    public ObservableCollection<BilibiliVideoVm> BiliVideos { get; } = new();

    [ObservableProperty] private int _selectedChapter = 1;
    [ObservableProperty] private LectureVideoVm? _selectedVideo;
    [ObservableProperty] private IMediaPlaybackSource? _videoSource;
    [ObservableProperty] private string _videoTitle = string.Empty;
    [ObservableProperty] private bool _hasVideo;
    [ObservableProperty] private bool _noVideo;
    [ObservableProperty] private string _chapterName = string.Empty;
    [ObservableProperty] private string _status = "选择章节，加载视频讲解。";

    public LectureViewModel(IApiClient api) => _api = api;

    partial void OnSelectedChapterChanged(int value) => _ = LoadAsync();

    partial void OnSelectedVideoChanged(LectureVideoVm? value)
    {
        if (value is null)
        {
            VideoSource = null;
            VideoTitle = string.Empty;
            return;
        }
        VideoSource = MediaSource.CreateFromUri(_api.GetVideoUri(value.Id));
        VideoTitle = value.Title;
    }

    public async Task LoadAsync()
    {
        ClearError();
        IsBusy = true;
        Videos.Clear();
        BiliVideos.Clear();
        VideoSource = null;
        VideoTitle = string.Empty;
        HasVideo = false;
        NoVideo = false;
        ChapterName = $"第 {SelectedChapter} 章 · {BossCatalog.NameForChapter(SelectedChapter)}";
        try
        {
            var videos = await _api.GetVideosAsync(SelectedChapter);
            foreach (var v in videos.Where(v => v.Available))
            {
                Videos.Add(new LectureVideoVm(v.Id, string.IsNullOrWhiteSpace(v.Title) ? "(无标题)" : v.Title!, FormatDuration(v.DurationSec)));
            }

            if (Videos.Count > 0)
            {
                HasVideo = true;
                SelectedVideo = Videos[0];
                Status = $"本章 {Videos.Count} 个在线视频小节。";
            }
            else
            {
                NoVideo = true;
                Status = "本章暂无在线视频，已切换到 B 站精选讲解。";
                await LoadBiliAsync();
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

    private async Task LoadBiliAsync()
    {
        try
        {
            var firstKc = RogueConfig.ChapterKcs(SelectedChapter).FirstOrDefault();
            var keyword = $"Python {KcNames.DisplayName(firstKc)}";
            var videos = await _api.SearchBilibiliVideosAsync(keyword, limit: 6);
            foreach (var v in videos)
            {
                BiliVideos.Add(BilibiliVideoVm.From(v));
            }
        }
        catch
        {
            // best-effort fallback; leave the list empty if even Bilibili search is unreachable
        }
    }

    private static string FormatDuration(int? sec)
    {
        if (sec is not { } s || s <= 0)
        {
            return string.Empty;
        }
        return $"{s / 60}:{s % 60:D2}";
    }
}

public sealed record LectureVideoVm(long Id, string Title, string Duration)
{
    public bool HasDuration => Duration.Length > 0;
}
