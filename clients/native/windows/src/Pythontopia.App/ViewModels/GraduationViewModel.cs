using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using Pythontopia.Core.Api;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the 毕业典礼 (graduation) end-game screen. Graduation requires every one of the 7 chapters
/// to hold ≥1★ (i.e. each chapter test passed), using the authoritative mainline overview stars.
/// Until then the page shows the remaining condition instead of the ceremony.
/// </summary>
public sealed partial class GraduationViewModel : ViewModelBase
{
    private const string FinalCue = "镇长 Py 在毕业典礼把镇徽别在你身上，标志你已完成 7 章 65 KC。";

    private readonly IApiClient _api;

    [ObservableProperty] private bool _loaded;
    [ObservableProperty] private bool _graduated;
    [ObservableProperty] private bool _showLocked;
    [ObservableProperty] private string _cueText = FinalCue;
    [ObservableProperty] private string _starsText = "— / 21";
    [ObservableProperty] private string _chaptersText = "— / 7";
    [ObservableProperty] private string _kcsText = "— / 65";
    [ObservableProperty] private string _lockedText = string.Empty;

    public GraduationViewModel(IApiClient api) => _api = api;

    public async Task LoadAsync()
    {
        ClearError();
        IsBusy = true;
        try
        {
            var overview = await _api.GetMainlineOverviewAsync();
            var mastery = await _api.GetMasteryAsync();

            var cleared = overview.Chapters.Count(c => c.Stars >= 1);
            var total = overview.Chapters.Count;
            Graduated = total >= 7 && overview.Chapters.All(c => c.Stars >= 1);
            var masteredKc = mastery.Count(m => (double)m.MasteryScore >= 0.4);

            StarsText = $"{overview.TotalStars} / 21";
            ChaptersText = $"{cleared} / 7";
            KcsText = $"{masteredKc.ToString(CultureInfo.InvariantCulture)} / 65";
            LockedText = Graduated
                ? string.Empty
                : $"毕业条件：7 章全部 ≥1★（通过章末测试）。当前 {cleared}/7 章达标 — 继续闯主线与章测吧！";

            Loaded = true;
            ShowLocked = Loaded && !Graduated;
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
