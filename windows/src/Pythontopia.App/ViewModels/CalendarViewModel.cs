using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the 世界日历 (world calendar) page: the in-game date/season/weather snapshot
/// (GET /api/world/calendar/today) plus pending world events (GET /api/world/calendar/events).
/// </summary>
public partial class CalendarViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    [ObservableProperty]
    private string _todayText = "加载中…";

    public ObservableCollection<WorldEventVm> Events { get; } = new();

    public CalendarViewModel(IApiClient api) => _api = api;

    [RelayCommand]
    private Task Refresh() => LoadAsync();

    public async Task LoadAsync()
    {
        ClearError();
        IsBusy = true;
        try
        {
            var today = await _api.GetCalendarTodayAsync();
            TodayText =
                $"第 {today.MonthIndex} 月 · 第 {today.DayIndex} 天 · {today.Weekday} · " +
                $"{today.Season} · {today.TimeOfDay} · {today.Weather}";

            try
            {
                var events = await _api.GetWorldEventsAsync();
                Events.Clear();
                foreach (var evt in events)
                {
                    Events.Add(WorldEventVm.From(evt));
                }
            }
            catch (ApiException)
            {
                Events.Clear();
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

public sealed record WorldEventVm(string Title, string Meta)
{
    public static WorldEventVm From(WorldEventDto d) =>
        new(d.EventType ?? "事件", $"{d.DateIso} · {(d.IsProcessed ? "已处理" : "待处理")}");
}
