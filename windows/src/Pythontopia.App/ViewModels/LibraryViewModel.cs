using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the 课件库 page: fetches a course-document PDF with the bearer token
/// (GET /api/course/documents/{id}/pdf) to a temp file, then renders it in WebView2.
/// </summary>
public partial class LibraryViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    // Course pack 43 ships 7 documents (ids 186-192), one per chapter.
    public IReadOnlyList<DocOption> Documents { get; } =
        Enumerable.Range(1, 7).Select(i => new DocOption(185 + i, $"第 {i} 章课件 (doc {185 + i})")).ToList();

    [ObservableProperty]
    private DocOption? _selectedDocument;

    [ObservableProperty]
    private Uri? _pdfSource;

    [ObservableProperty]
    private string _status = "选择课件文档并加载 PDF。";

    public LibraryViewModel(IApiClient api)
    {
        _api = api;
        SelectedDocument = Documents[0];
    }

    [RelayCommand]
    private async Task Open()
    {
        if (SelectedDocument is null)
        {
            ErrorMessage = "请选择文档";
            return;
        }

        ClearError();
        IsBusy = true;
        try
        {
            var bytes = await _api.GetCourseDocumentPdfAsync(SelectedDocument.DocId);
            var path = Path.Combine(Path.GetTempPath(), $"pyt-doc-{SelectedDocument.DocId}.pdf");
            await File.WriteAllBytesAsync(path, bytes);
            PdfSource = new Uri(path);
            Status = $"已加载 {SelectedDocument.Title}（{bytes.Length / 1024} KB）";
        }
        catch (Exception ex)
        {
            SetError(ex);
            Status = "加载失败（PDF 可能未在服务端配置，或需要 WebView2 运行时）。";
        }
        finally
        {
            IsBusy = false;
        }
    }
}

public sealed record DocOption(long DocId, string Title);
