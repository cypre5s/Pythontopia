using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Pythontopia.Core.Api;

namespace Pythontopia.App.ViewModels;

/// <summary>
/// Drives the NPC 互动 page. The NPC roster is the static town_hub catalog (data/npcs/index.json);
/// greeting (GET /api/npc/{key}/greeting) and dialog (POST /api/npc/{key}/dialog) hit the backend.
/// </summary>
public partial class NpcViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    public IReadOnlyList<NpcOption> Npcs { get; } = new[]
    {
        new NpcOption("mayor_py", "镇长 Py"),
        new NpcOption("foreman_bug", "矿头 Bug"),
        new NpcOption("librarian_doc", "馆长 Doc"),
        new NpcOption("coach_q", "教务 Q"),
        new NpcOption("sage_review", "复盘师 Sage"),
        new NpcOption("professor", "教授"),
        new NpcOption("mine_keeper", "矿洞守矿人"),
        new NpcOption("student_a", "学生 A"),
        new NpcOption("class_president", "班长"),
        new NpcOption("workshop_master", "工坊师傅"),
        new NpcOption("arena_guard", "竞技场守卫"),
    };

    [ObservableProperty]
    private NpcOption? _selectedNpc;

    [ObservableProperty]
    private string _greeting = "选择一个 NPC，点「打招呼」开始对话。";

    [ObservableProperty]
    private string _message = string.Empty;

    public ObservableCollection<NpcTurnVm> Transcript { get; } = new();

    public NpcViewModel(IApiClient api)
    {
        _api = api;
        SelectedNpc = Npcs[0];
    }

    [RelayCommand]
    private async Task Greet()
    {
        if (SelectedNpc is null)
        {
            return;
        }

        ClearError();
        IsBusy = true;
        Transcript.Clear();
        try
        {
            var greeting = await _api.GetNpcGreetingAsync(SelectedNpc.Key);
            Greeting = string.IsNullOrWhiteSpace(greeting.Greeting)
                ? (greeting.Available ? "（无问候内容）" : "该 NPC 当前不可用。")
                : greeting.Greeting!;
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
    private async Task Send()
    {
        if (SelectedNpc is null)
        {
            return;
        }
        if (string.IsNullOrWhiteSpace(Message))
        {
            ErrorMessage = "请输入要说的话";
            return;
        }

        ClearError();
        IsBusy = true;
        try
        {
            var mine = Message.Trim();
            Transcript.Add(new NpcTurnVm("我", mine));
            var reply = await _api.TalkToNpcAsync(SelectedNpc.Key, mine);
            Transcript.Add(new NpcTurnVm(SelectedNpc.Name, reply.ReplyMd ?? string.Empty));
            Message = string.Empty;
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

public sealed record NpcOption(string Key, string Name);

public sealed record NpcTurnVm(string Speaker, string Content);
