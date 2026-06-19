using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Pythontopia.Core.Api;
using Pythontopia.Core.Dto;
using Windows.Media.Core;
using Windows.Media.Playback;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the 多模态媒体 page: generate an illustration (POST /api/media/image) and composite a
/// storyboard short video (POST /api/media/storyboard/video). Both display via the anonymous asset
/// URL and degrade gracefully when the server lacks a provider key / ffmpeg.
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

    // ── storyboard short video ──
    [ObservableProperty]
    private string _storyboardStyle = "像素风格、暖色、Python 教学插画";

    [ObservableProperty]
    private string _storyboardScript = "镇长 Py 欢迎你来到 Python 小镇\n变量就像贴了标签的盒子\n用 print() 把结果喊出来";

    [ObservableProperty]
    private string _storyboardStatus = "每行写一镜旁白，合成图序列 + 配音短视频。";

    [ObservableProperty]
    private bool _storyboardBusy;

    [ObservableProperty]
    private string _storyboardProgress = string.Empty;

    [ObservableProperty]
    private IMediaPlaybackSource? _storyboardVideo;

    // ── 语音听写 (ASR) ──
    [ObservableProperty]
    private string _asrText = string.Empty;

    [ObservableProperty]
    private string _asrStatus = "选择一个音频文件（webm / mp3 / wav / m4a），转写为文字。";

    [ObservableProperty]
    private bool _asrBusy;

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

    [RelayCommand]
    private async Task ComposeStoryboard()
    {
        var lines = (StoryboardScript ?? string.Empty)
            .Split('\n')
            .Select(l => l.Trim())
            .Where(l => l.Length > 0)
            .ToList();
        if (lines.Count == 0)
        {
            ErrorMessage = "请至少写一行镜头旁白";
            return;
        }

        var style = string.IsNullOrWhiteSpace(StoryboardStyle) ? string.Empty : StoryboardStyle.Trim() + "：";
        var scenes = lines
            .Select(line => new StoryboardSceneSpec(ImagePrompt: style + line, Narration: line))
            .ToList();

        ClearError();
        StoryboardBusy = true;
        StoryboardVideo = null;
        StoryboardProgress = string.Empty;
        StoryboardStatus = $"正在排队 {scenes.Count} 镜短视频合成作业…";
        try
        {
            // Async media job (queued + idempotent + progress) — the "video studio" path; cached
            // specs return the asset directly, otherwise we poll the job for live progress.
            var job = await _api.CreateStoryboardVideoJobAsync(scenes);
            if (job.Cached && job.AssetId is { } cachedId)
            {
                PlayStoryboard(cachedId);
                StoryboardStatus = "命中缓存，直接播放。";
                return;
            }
            if (!await PollStoryboardJobAsync(job.JobId))
            {
                StoryboardStatus = "合成仍在进行，请稍后在媒资中查看。";
            }
        }
        catch (Exception ex)
        {
            // VIDEO_COMPOSE_UNAVAILABLE (no ffmpeg) surfaces as an ApiException; show it inline.
            SetError(ex);
            StoryboardStatus = "合成失败或服务端未启用视频合成。";
        }
        finally
        {
            StoryboardBusy = false;
        }
    }

    private async Task<bool> PollStoryboardJobAsync(long jobId)
    {
        for (var i = 0; i < 60; i++)
        {
            await Task.Delay(1200);
            MediaJobDto job;
            try
            {
                job = await _api.GetMediaJobAsync(jobId);
            }
            catch
            {
                continue; // transient; keep polling
            }

            if (job.Progress is { } p)
            {
                var percent = p.Percent.HasValue ? $"{p.Percent}% " : string.Empty;
                StoryboardProgress = $"{p.Phase} {percent}{p.Message}".Trim();
            }

            if (string.Equals(job.Status, "SUCCESS", StringComparison.OrdinalIgnoreCase))
            {
                if (!string.IsNullOrWhiteSpace(job.Url))
                {
                    StoryboardVideo = MediaSource.CreateFromUri(_api.GetMediaAssetUri(job.Url!));
                }
                else if (job.AssetId is { } assetId)
                {
                    PlayStoryboard(assetId);
                }
                StoryboardStatus = "已合成短视频。";
                StoryboardProgress = string.Empty;
                return true;
            }
            if (string.Equals(job.Status, "FAILED", StringComparison.OrdinalIgnoreCase))
            {
                StoryboardStatus = $"合成失败（{job.ErrorCode ?? "未知错误"}）。";
                StoryboardProgress = string.Empty;
                return true;
            }
        }
        return false;
    }

    private void PlayStoryboard(long assetId) =>
        StoryboardVideo = MediaSource.CreateFromUri(_api.GetMediaAssetUri($"/api/media/assets/{assetId}"));

    /// <summary>Called by the page after the user picks an audio file: upload it for transcription.</summary>
    public async Task TranscribeAsync(byte[] audio, string fileName, string contentType)
    {
        ClearError();
        AsrBusy = true;
        AsrStatus = "识别中…";
        try
        {
            var res = await _api.TranscribeAudioAsync(
                audio,
                string.IsNullOrWhiteSpace(fileName) ? "speech.webm" : fileName,
                string.IsNullOrWhiteSpace(contentType) ? "application/octet-stream" : contentType);
            AsrText = res.Text ?? string.Empty;
            AsrStatus = res.Degraded
                ? "识别完成（降级：服务端未配置语音听写）。"
                : "识别完成。";
        }
        catch (Exception ex)
        {
            SetError(ex);
            AsrStatus = "识别失败。";
        }
        finally
        {
            AsrBusy = false;
        }
    }
}
