using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;

namespace Pythontopia.App.ViewModels;

public partial class CourseViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    [ObservableProperty]
    private CoursePackDto? _pack;

    [ObservableProperty]
    private ChapterDto? _selectedChapter;

    [ObservableProperty]
    private KcDto? _selectedKc;

    [ObservableProperty]
    private PageDto? _selectedPage;

    [ObservableProperty]
    private ImageSource? _pageImage;

    public ObservableCollection<ChapterDto> Chapters { get; } = new();
    public ObservableCollection<KcDto> Kcs { get; } = new();
    public ObservableCollection<PageDto> Pages { get; } = new();

    public CourseViewModel(IApiClient api) => _api = api;

    public string PackSummary => Pack is null
        ? "课程包未加载"
        : $"{Pack.Name}  ·  文档 {Pack.DocumentCount} · 页 {Pack.PageCount} · 章 {Pack.ChapterCount} · KC {Pack.KcCount} · 示例 {Pack.ExampleCount} · 题 {Pack.ProblemCount}";

    public async Task LoadAsync()
    {
        ClearError();
        IsBusy = true;
        try
        {
            Pack = await _api.GetCoursePackAsync();
            OnPropertyChanged(nameof(PackSummary));

            var chapters = await _api.GetChaptersAsync();
            Chapters.Clear();
            foreach (var chapter in chapters.OrderBy(c => c.OrderIndex))
            {
                Chapters.Add(chapter);
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

    partial void OnSelectedChapterChanged(ChapterDto? value)
    {
        Kcs.Clear();
        Pages.Clear();
        PageImage = null;
        if (value?.ChapterId is { } chapterId)
        {
            _ = LoadKcsAsync(chapterId);
        }
    }

    partial void OnSelectedKcChanged(KcDto? value)
    {
        Pages.Clear();
        PageImage = null;
        if (value?.KcCode is { } kcCode)
        {
            _ = LoadPagesAsync(kcCode);
        }
    }

    partial void OnSelectedPageChanged(PageDto? value)
    {
        if (value?.PageId is { } pageId)
        {
            PageImage = new BitmapImage(_api.GetPageImageUri(pageId));
        }
        else
        {
            PageImage = null;
        }
    }

    private async Task LoadKcsAsync(long chapterId)
    {
        ClearError();
        try
        {
            IsBusy = true;
            var kcs = await _api.GetKcsAsync(chapterId);
            Kcs.Clear();
            foreach (var kc in kcs)
            {
                Kcs.Add(kc);
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

    private async Task LoadPagesAsync(string kcCode)
    {
        ClearError();
        try
        {
            IsBusy = true;
            var pages = await _api.GetPagesAsync(kcCode);
            Pages.Clear();
            foreach (var page in pages.OrderBy(p => p.PageNo))
            {
                Pages.Add(page);
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
