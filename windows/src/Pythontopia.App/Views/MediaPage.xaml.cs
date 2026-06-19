using System.Runtime.InteropServices.WindowsRuntime;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Pythontopia.App.ViewModels;
using Windows.Storage;
using Windows.Storage.Pickers;

namespace Pythontopia.App.Views;

public sealed partial class MediaPage : Page
{
    public MediaViewModel ViewModel { get; }

    public MediaPage()
    {
        ViewModel = App.GetService<MediaViewModel>();
        InitializeComponent();
        DataContext = ViewModel;
    }

    private async void OnPickAudio(object sender, RoutedEventArgs e)
    {
        var picker = new FileOpenPicker { SuggestedStartLocation = PickerLocationId.MusicLibrary };
        picker.FileTypeFilter.Add(".webm");
        picker.FileTypeFilter.Add(".mp3");
        picker.FileTypeFilter.Add(".wav");
        picker.FileTypeFilter.Add(".m4a");
        picker.FileTypeFilter.Add(".ogg");

        // Unpackaged WinUI: the picker must be associated with the window HWND.
        if (App.RootWindow is { } window)
        {
            var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(window);
            WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);
        }

        var file = await picker.PickSingleFileAsync();
        if (file is null)
        {
            return;
        }

        var buffer = await FileIO.ReadBufferAsync(file);
        var bytes = buffer.ToArray();
        await ViewModel.TranscribeAsync(bytes, file.Name, file.ContentType);
    }
}
