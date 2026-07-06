import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils';
import { nextTick, ref } from 'vue';

// 只替换 uploadFile（网络），保留真实纯函数（extOf / 校验 / 标签）。
vi.mock('@/api/upload', async () => {
  const actual = await vi.importActual<typeof import('@/api/upload')>('@/api/upload');
  return { ...actual, uploadFile: vi.fn() };
});

const voiceStartSpy = vi.fn();
const voiceStopSpy = vi.fn();

interface SpeechRecognitionResultLike {
  transcript: string;
}

interface SpeechRecognitionEventLike {
  results: ArrayLike<ArrayLike<SpeechRecognitionResultLike>>;
}

class MockSpeechRecognition {
  lang = '';
  continuous = false;
  interimResults = false;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null = null;
  onend: (() => void) | null = null;

  constructor() {
    speechInstances.push(this);
  }

  start(): void {
    voiceStartSpy();
  }

  stop(): void {
    voiceStopSpy();
    this.onend?.();
  }
}

const speechInstances: MockSpeechRecognition[] = [];

function installSpeechRecognition(): void {
  speechInstances.length = 0;
  Object.defineProperty(window, 'SpeechRecognition', {
    configurable: true,
    writable: true,
    value: MockSpeechRecognition,
  });
  Object.defineProperty(window, 'webkitSpeechRecognition', {
    configurable: true,
    writable: true,
    value: undefined,
  });
}

function uninstallSpeechRecognition(): void {
  speechInstances.length = 0;
  Object.defineProperty(window, 'SpeechRecognition', {
    configurable: true,
    writable: true,
    value: undefined,
  });
  Object.defineProperty(window, 'webkitSpeechRecognition', {
    configurable: true,
    writable: true,
    value: undefined,
  });
}

const recorderSupported = ref(true);
const recorderRecording = ref(false);
const recorderStartSpy = vi.fn();
const recorderStopSpy = vi.fn();

vi.mock('@/composables/useAudioRecorder', () => ({
  useAudioRecorder: () => ({
    supported: recorderSupported,
    recording: recorderRecording,
    start: recorderStartSpy,
    stop: recorderStopSpy,
  }),
}));

const transcribeAudioSpy = vi.fn();

vi.mock('@/api/media', () => ({
  transcribeAudio: (...args: unknown[]) => transcribeAudioSpy(...args),
}));

import ChatComposer, { type ComposerSubmitPayload } from '../ChatComposer.vue';
import { uploadFile, type UploadDto } from '@/api/upload';

const READY: UploadDto = {
  id: 7,
  filename: 'main.py',
  ext: '.py',
  byteSize: 18,
  charCount: 17,
  lineCount: 2,
  status: 'READY',
};

function mountComposer(): VueWrapper {
  return mount(ChatComposer, {
    props: { mentionItems: [] },
    global: { stubs: { CoursewareMentionMenu: true, SlashCommandMenu: true } },
  });
}

function pyFile(name = 'main.py', content = 'print(1)\nprint(2)'): File {
  return new File([content], name, { type: 'text/x-python' });
}

async function pickFiles(w: VueWrapper, files: File[]): Promise<void> {
  const input = w.find('[data-testid="composer-file-input"]');
  Object.defineProperty(input.element, 'files', { value: files, configurable: true });
  await input.trigger('change');
  await nextTick();
}

function lastSubmit(w: VueWrapper): ComposerSubmitPayload {
  const events = w.emitted('submit');
  expect(events).toBeTruthy();
  return events![events!.length - 1]![0] as ComposerSubmitPayload;
}

beforeEach(() => {
  vi.clearAllMocks();
  installSpeechRecognition();
  recorderSupported.value = true;
  recorderRecording.value = false;
  recorderStartSpy.mockImplementation(async () => {
    recorderRecording.value = true;
    return true;
  });
  recorderStopSpy.mockImplementation(async () => {
    recorderRecording.value = false;
    return new Blob(['a'], { type: 'audio/webm' });
  });
  transcribeAudioSpy.mockImplementation(async () => ({
    text: '录音转写的问题',
    degraded: false,
  }));
});

describe('ChatComposer · 上传状态机（uploading→ready→error→retry）', () => {
  it('选择文件：uploading（spinner）→ ready（行/字）；上传中禁止发送', async () => {
    let resolveUpload!: (v: UploadDto) => void;
    vi.mocked(uploadFile).mockReturnValue(
      new Promise<UploadDto>((res) => {
        resolveUpload = res;
      }),
    );
    const w = mountComposer();

    await pickFiles(w, [pyFile()]);
    expect(uploadFile).toHaveBeenCalledTimes(1);
    expect(w.find('[data-testid="pill-spinner"]').exists()).toBe(true);
    expect(w.find('[data-testid="attachment-pill"]').attributes('data-status')).toBe('uploading');

    // 有文本但附件仍在上传 → 发送禁用（避免丢附件）。
    await w.find('[data-testid="composer-input"]').setValue('讲讲这段');
    expect(w.find('[data-testid="composer-submit"]').attributes('disabled')).toBeDefined();

    resolveUpload(READY);
    await flushPromises();

    const pill = w.find('[data-testid="attachment-pill"]');
    expect(pill.attributes('data-status')).toBe('ready');
    expect(w.text()).toContain('2 行');
    expect(w.text()).toContain('17 字');
    expect(w.find('[data-testid="pill-spinner"]').exists()).toBe(false);
    // 就绪后可发送。
    expect(w.find('[data-testid="composer-submit"]').attributes('disabled')).toBeUndefined();
  });

  it('上传失败 → error pill（带错误）+ 重试 → 成功转 ready', async () => {
    vi.mocked(uploadFile).mockRejectedValueOnce(new Error('网络炸了'));
    const w = mountComposer();

    await pickFiles(w, [pyFile()]);
    await flushPromises();
    expect(w.find('[data-testid="attachment-pill"]').attributes('data-status')).toBe('error');
    expect(w.find('[data-testid="pill-error"]').text()).toContain('网络炸了');
    expect(w.find('[data-testid="pill-retry"]').exists()).toBe(true);

    vi.mocked(uploadFile).mockResolvedValueOnce(READY);
    await w.find('[data-testid="pill-retry"]').trigger('click');
    await flushPromises();
    expect(w.find('[data-testid="attachment-pill"]').attributes('data-status')).toBe('ready');
  });

  it('后端返回 status=FAILED → error pill（带后端 error）', async () => {
    vi.mocked(uploadFile).mockResolvedValue({ ...READY, status: 'FAILED', error: 'PDF 解析失败' });
    const w = mountComposer();

    await pickFiles(w, [pyFile('doc.pdf')]);
    await flushPromises();
    expect(w.find('[data-testid="attachment-pill"]').attributes('data-status')).toBe('error');
    expect(w.find('[data-testid="pill-error"]').text()).toContain('PDF 解析失败');
  });

  it('非白名单类型：本地校验直接 error，不发起上传', async () => {
    const w = mountComposer();
    await pickFiles(w, [new File(['x'], 'evil.exe')]);
    await flushPromises();
    expect(uploadFile).not.toHaveBeenCalled();
    expect(w.find('[data-testid="attachment-pill"]').attributes('data-status')).toBe('error');
    expect(w.find('[data-testid="pill-error"]').text()).toContain('不支持');
  });

  it('移除 pill：从列表删除', async () => {
    vi.mocked(uploadFile).mockResolvedValue(READY);
    const w = mountComposer();
    await pickFiles(w, [pyFile()]);
    await flushPromises();
    expect(w.find('[data-testid="attachment-pill"]').exists()).toBe(true);
    await w.find('[data-testid="pill-remove"]').trigger('click');
    await nextTick();
    expect(w.find('[data-testid="attachment-pill"]').exists()).toBe(false);
  });
});

describe('ChatComposer · payload.attachments 注入', () => {
  it('提交：payload.attachments 仅含已就绪文件（id/filename/ext/charCount），发后清空', async () => {
    vi.mocked(uploadFile).mockResolvedValue(READY);
    const w = mountComposer();

    await pickFiles(w, [pyFile()]);
    await flushPromises();
    await w.find('[data-testid="composer-input"]').setValue('讲讲这段代码');
    await w.find('[data-testid="composer-submit"]').trigger('click');

    const payload = lastSubmit(w);
    expect(payload.text).toBe('讲讲这段代码');
    expect(payload.attachments).toEqual([
      { id: 7, filename: 'main.py', ext: '.py', charCount: 17 },
    ]);

    // 发送后附件清空。
    await nextTick();
    expect(w.find('[data-testid="attachment-pills"]').exists()).toBe(false);
  });

  it('提交：失败 / 未就绪附件不进 payload（只保留就绪项）', async () => {
    vi.mocked(uploadFile).mockResolvedValue(READY);
    const w = mountComposer();

    await pickFiles(w, [pyFile('ok.py')]);
    await flushPromises();
    // 第二个文件非白名单 → error，不应进 payload。
    await pickFiles(w, [new File(['x'], 'bad.exe')]);
    await flushPromises();
    expect(w.findAll('[data-testid="attachment-pill"]').length).toBe(2);

    await w.find('[data-testid="composer-input"]').setValue('问题');
    await w.find('[data-testid="composer-submit"]').trigger('click');

    const payload = lastSubmit(w);
    expect(payload.attachments.length).toBe(1);
    expect(payload.attachments[0]!.filename).toBe('ok.py');
  });

  it('无附件提交：attachments 为空数组（向后兼容）', async () => {
    const w = mountComposer();
    await w.find('[data-testid="composer-input"]').setValue('纯文本提问');
    await w.find('[data-testid="composer-submit"]').trigger('click');
    const payload = lastSubmit(w);
    expect(payload.attachments).toEqual([]);
  });
});

describe('ChatComposer · 能力启动器', () => {
  it('默认隐藏能力面板，点击 + 后展示 6 个能力组', async () => {
    const w = mountComposer();

    expect(w.find('[data-testid="composer-capability-menu"]').exists()).toBe(false);
    await w.find('[data-testid="composer-open-capabilities"]').trigger('click');

    expect(w.find('[data-testid="composer-capability-menu"]').exists()).toBe(true);
    expect(w.findAll('.composer__capability-card').length).toBe(6);
    expect(w.text()).toContain('问一问');
    expect(w.text()).toContain('练一练');
  });

  it('选择能力组后只提交能力命名空间，不强制具体 intent', async () => {
    const w = mountComposer();

    await w.find('[data-testid="composer-open-capabilities"]').trigger('click');
    await w.find('[data-testid="composer-capability-practice"]').trigger('click');
    await w.find('[data-testid="composer-input"]').setValue('循环');
    await w.find('[data-testid="composer-submit"]').trigger('click');

    const payload = lastSubmit(w);
    expect(payload.forcedIntent).toBeNull();
    expect(payload.source).toBe('capability_launcher');
    expect(payload.capabilityNamespace).toBe('practice');
    expect(payload.text).toBe('循环');
  });
});

describe('ChatComposer · 语音 / ASR 输入', () => {
  it('Web Speech 结果回填输入框，并在听写结束后进入 payload', async () => {
    const w = mountComposer();

    await w.find('[data-testid="composer-voice"]').trigger('click');
    expect(voiceStartSpy).toHaveBeenCalledTimes(1);
    expect(w.find('[data-testid="composer-voice-status"]').text()).toContain('正在听');
    await w.find('[data-testid="composer-input"]').setValue('先解释');
    expect(w.find('[data-testid="composer-submit"]').attributes('disabled')).toBeDefined();

    expect(speechInstances.length).toBe(1);
    speechInstances[0]!.onresult?.({ results: [[{ transcript: '列表推导式' }]] });
    await nextTick();
    expect((w.find('[data-testid="composer-input"]').element as HTMLTextAreaElement).value).toBe(
      '先解释 列表推导式',
    );

    speechInstances[0]!.onend?.();
    await nextTick();
    await w.find('[data-testid="composer-submit"]').trigger('click');

    const payload = lastSubmit(w);
    expect(payload.text).toBe('先解释 列表推导式');
  });

  it('Web Speech 不可用时录音转写，并回填同一个输入框', async () => {
    uninstallSpeechRecognition();
    const w = mountComposer();

    await w.find('[data-testid="composer-voice"]').trigger('click');
    expect(recorderStartSpy).toHaveBeenCalledTimes(1);
    expect(w.find('[data-testid="composer-voice-status"]').text()).toContain('停止录音');

    await w.find('[data-testid="composer-voice"]').trigger('click');
    await flushPromises();

    expect(recorderStopSpy).toHaveBeenCalledTimes(1);
    expect(transcribeAudioSpy).toHaveBeenCalledTimes(1);
    expect((w.find('[data-testid="composer-input"]').element as HTMLTextAreaElement).value).toBe(
      '录音转写的问题',
    );
  });

  it('ASR 降级时保留错误提示，不伪造文本', async () => {
    uninstallSpeechRecognition();
    transcribeAudioSpy.mockResolvedValueOnce({ text: '', degraded: true });
    const w = mountComposer();

    await w.find('[data-testid="composer-voice"]').trigger('click');
    await w.find('[data-testid="composer-voice"]').trigger('click');
    await flushPromises();

    expect(w.find('[data-testid="composer-voice-error"]').text()).toContain('语音转写暂不可用');
    expect((w.find('[data-testid="composer-input"]').element as HTMLTextAreaElement).value).toBe(
      '',
    );
  });
});

describe('ChatComposer · 拖拽 / 粘贴上传', () => {
  it('拖拽：dragenter 显示覆盖层，drop 落文件生成 pill', async () => {
    vi.mocked(uploadFile).mockReturnValue(new Promise<UploadDto>(() => {}));
    const w = mountComposer();
    const root = w.find('[data-testid="chat-composer"]');

    await root.trigger('dragenter', { dataTransfer: { types: ['Files'] } });
    expect(w.find('[data-testid="composer-dropzone"]').exists()).toBe(true);

    await root.trigger('drop', { dataTransfer: { types: ['Files'], files: [pyFile()] } });
    await nextTick();
    expect(w.find('[data-testid="composer-dropzone"]').exists()).toBe(false);
    expect(w.find('[data-testid="attachment-pill"]').exists()).toBe(true);
    expect(uploadFile).toHaveBeenCalledTimes(1);
  });

  it('拖入非文件（纯文本）：不显示覆盖层', async () => {
    const w = mountComposer();
    const root = w.find('[data-testid="chat-composer"]');
    await root.trigger('dragenter', { dataTransfer: { types: ['text/plain'] } });
    expect(w.find('[data-testid="composer-dropzone"]').exists()).toBe(false);
  });

  it('粘贴：clipboardData.files 生成 pill', async () => {
    vi.mocked(uploadFile).mockReturnValue(new Promise<UploadDto>(() => {}));
    const w = mountComposer();
    await w
      .find('[data-testid="composer-input"]')
      .trigger('paste', { clipboardData: { files: [pyFile()] } });
    await nextTick();
    expect(w.find('[data-testid="attachment-pill"]').exists()).toBe(true);
    expect(uploadFile).toHaveBeenCalledTimes(1);
  });
});
