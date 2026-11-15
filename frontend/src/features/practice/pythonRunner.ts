/** Fresh opaque-origin workers: no app token, cookies, storage or previous code. */
export interface PythonResult { stdout: string; stderr: string }
const runtimePath = '/python-runtime/0.27.7/';
let idleRuntimeCount = 0;

function createContext() {
  const runtime = new URL(runtimePath, location.origin).href;
  const frame = document.createElement('iframe');
  frame.hidden = true;
  frame.sandbox.add('allow-scripts');
  frame.setAttribute('credentialless', '');
  frame.referrerPolicy = 'no-referrer';
  let readyResolve!: () => void, readyReject!: (error: Error) => void;
  const ready = new Promise<void>((resolve, reject) => { readyResolve = resolve; readyReject = reject; });
  // Prewarming can fail before the user presses Run; surface that failure on use.
  void ready.catch(() => {});
  let disposed = false, used = false;
  let finish: ((result: PythonResult) => void) | undefined;
  let rejectRun: ((error: Error) => void) | undefined;
  let timer: ReturnType<typeof setTimeout>;
  const dispose = () => {
    if (disposed) return;
    disposed = true;
    clearTimeout(timer);
    window.removeEventListener('message', receive);
    frame.contentWindow?.postMessage({dispose:true}, '*');
    frame.remove();
    readyReject(new Error('已停止运行'));
    rejectRun?.(new Error('已停止运行'));
  };
  const fail = (message: string) => {
    const error = new Error(message);
    readyReject(error); rejectRun?.(error); dispose();
  };
  const receive = (event: MessageEvent) => {
    if (event.source !== frame.contentWindow || disposed) return;
    const data = event.data;
    if (!data || typeof data !== 'object') return;
    if (data.ready && !used) { clearTimeout(timer); readyResolve(); }
    else if (typeof data.error === 'string') fail(data.error.slice(0, 2000));
    else if (used && typeof data.stdout === 'string' && typeof data.stderr === 'string') {
      finish?.({stdout:data.stdout.slice(0,16000), stderr:data.stderr.slice(0,16000)});
      dispose();
    }
  };
  const workerCode = `
    (async () => {
      try {
        importScripts('${runtime}pyodide.js');
        const py = await loadPyodide({indexURL:'${runtime}'});
        onmessage = ({data}) => {
          // This worker executes exactly one sample and is then destroyed.
          onmessage = null;
          let stdout='', stderr='';
          const outDecoder=new TextDecoder(), errDecoder=new TextDecoder();
          const append=(old,value)=>{if(old.length+value.length>16000)throw Error('输出超过16000字符');return old+value;};
          py.setStdout({write:bytes=>{stdout=append(stdout,outDecoder.decode(bytes,{stream:true}));return bytes.length;}});
          py.setStderr({write:bytes=>{stderr=append(stderr,errDecoder.decode(bytes,{stream:true}));return bytes.length;}});
          py.globals.set('_exercise_input',data.input);
          py.runPython('import sys, io\\nsys.stdin = io.StringIO(_exercise_input)');
          try { py.runPython(data.code); } catch(e) { stderr=String(e).slice(0,16000); }
          try { py.runPython('import sys; sys.stdout.flush(); sys.stderr.flush()'); } catch(e) { stderr=String(e).slice(0,16000); }
          postMessage({stdout,stderr});
          close();
        };
        postMessage({ready:true});
      } catch(e) { postMessage({error:String(e).slice(0,2000)}); }
    })();`;
  // Only public static runtime files are allowed. /api, arbitrary destinations,
  // cookies, parent DOM and storage remain inaccessible to student code.
  frame.srcdoc = `<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' ${runtime}; worker-src blob:; connect-src ${runtime}"><script>
    const url=URL.createObjectURL(new Blob([${JSON.stringify(workerCode)}],{type:'text/javascript'}));
    const worker=new Worker(url); URL.revokeObjectURL(url);
    worker.onmessage=e=>parent.postMessage(e.data,'*');
    worker.onerror=()=>parent.postMessage({error:'Python运行环境加载失败'},'*');
    onmessage=e=>{if(e.source===parent){if(e.data.dispose)worker.terminate();else worker.postMessage(e.data);}};
  </script>`;
  window.addEventListener('message', receive);
  timer = setTimeout(() => fail('Python环境加载超时，请重试'), 60000);
  document.body.append(frame);
  return {
    ready, dispose,
    async run(code: string, input: string, signal: AbortSignal): Promise<PythonResult> {
      const abort = () => fail('已停止运行');
      signal.addEventListener('abort', abort, {once:true});
      try {
        if (signal.aborted) { abort(); throw new Error('已停止运行'); }
        await ready;
        if (disposed || signal.aborted || used) throw new Error('已停止运行');
        used = true;
        return await new Promise<PythonResult>((resolve,reject) => {
          finish = resolve; rejectRun = reject;
          timer = setTimeout(() => fail('运行超过5秒，已停止'), 5000);
          frame.contentWindow?.postMessage({code,input}, '*');
        });
      } finally { signal.removeEventListener('abort', abort); dispose(); }
    },
  };
}

/** Per-workspace pool, bounded to two idle runtimes; never reuse executed Python. */
export function createPythonRunner(sampleCount = 2) {
  const size = Math.max(0, Math.min(2, sampleCount));
  const idle: ReturnType<typeof createContext>[] = [];
  const active = new Set<ReturnType<typeof createContext>>();
  let disposed = false;
  const prewarm = () => {
    // A full assessment may mount many questions. Bound all idle runtimes together.
    if (!disposed) while (idle.length < size && idleRuntimeCount < 2) { idle.push(createContext()); idleRuntimeCount++; }
  };
  prewarm();
  return {
    ready: () => Promise.all(idle.map(context => context.ready)),
    async run(code: string, input: string, signal: AbortSignal) {
      if (disposed || signal.aborted) throw new Error('已停止运行');
      const warmed = idle.shift();
      if (warmed) idleRuntimeCount--;
      const context = warmed ?? createContext();
      active.add(context);
      try { return await context.run(code, input, signal); }
      finally { active.delete(context); prewarm(); }
    },
    dispose() { disposed = true; idleRuntimeCount-=idle.length; idle.splice(0).forEach(context => context.dispose()); active.forEach(context => context.dispose()); active.clear(); },
  };
}

export function runPython(code: string, input: string, signal: AbortSignal): Promise<PythonResult> {
  if (signal.aborted) return Promise.reject(new Error('已停止运行'));
  return createContext().run(code, input, signal);
}
export function sameOutput(actual: string, expected: string): boolean {
  return actual.replace(/\r\n/g,'\n').trimEnd() === expected.replace(/\r\n/g,'\n').trimEnd();
}
