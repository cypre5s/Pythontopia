using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Pythontopia.Core.Api;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the AI 配图 page: generate an illustration from a prompt (POST /api/media/image) and
/// display it via the anonymous asset URL. Degrades to a placeholder when no provider key is set.
/// </summary>
public partial class MediaViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    [ObservableProperty]
    private string _prompt = "像素风格的 Python 学习小镇";

    [ObservableProperty]
    private ImageSource? _image;

    [ObservableProperty]
    private string _status = "输入描述，生成配图。";

    public MediaViewModel(IApiClient api) => _api = api;

    [RelayCommand]
    private async Task Generate()
    {
        if (string.IsNullOrWhiteSpace(Prompt))
        {
            ErrorMessage = "请输入图片描述";
            return;
        }

        ClearError();
        IsBusy = true;
        try
        {
            var asset = await _api.GenerateImageAsync(Prompt.Trim());
            if (!string.IsNullOrWhiteSpace(asset.Url))
            {
                Image = new BitmapImage(_api.GetMediaAssetUri(asset.Url!));
            }
            Status = asset.Degraded
                ? "已生成（降级占位图：服务端未配置图像 API key）。"
                : $"已生成（{asset.Source} · {asset.MimeType}）。";
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
