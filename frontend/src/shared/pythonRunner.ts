/** Disposable, opaque-origin sandbox. No app token, cookies or storage are passed in. */
export interface PythonResult { stdout: string; stderr: string }
const runtime = "https://cdn.jsdelivr.net/pyodide/v0.27.7/full/";
export function runPython(code: string, input: string, signal: AbortSignal): Promise<PythonResult> {
  if (signal.aborted) return Promise.reject(new Error("已停止运行"));
  return new Promise((resolve, reject) => {
    const frame = document.createElement("iframe");
    frame.hidden = true;
    frame.sandbox.add("allow-scripts");
    frame.setAttribute("credentialless", "");
    frame.referrerPolicy = "no-referrer";
    const workerCode = `
      importScripts('${runtime}pyodide.js');
      onmessage = async ({data}) => {
        try {
          const py = await loadPyodide({indexURL:'${runtime}'});
          let stdout='', stderr='';
          const append = (old, value) => { if(old.length + value.length > 16000) throw Error('输出超过16000字符'); return old + value + '\\n'; };
          py.setStdout({batched: s => {stdout=append(stdout,s);}});
          py.setStderr({batched: s => {stderr=append(stderr,s);}});
          py.globals.set('_exercise_input',data.input);
          py.runPython('import sys, io\\nsys.stdin = io.StringIO(_exercise_input)');
          postMessage({ready:true});
          try { py.runPython(data.code); } catch(e) { stderr=String(e).slice(0,16000); }
          postMessage({stdout,stderr});
        } catch(e) { postMessage({error:String(e).slice(0,2000)}); }
      };`;
    // CSP is inherited by the blob worker. Only the pinned runtime CDN can be read;
    // application endpoints and arbitrary network destinations are inaccessible.
    frame.srcdoc = `<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' ${runtime}; worker-src blob:; connect-src ${runtime}"><script>
      const worker=new Worker(URL.createObjectURL(new Blob([${JSON.stringify(workerCode)}],{type:'text/javascript'})));
      worker.onmessage=e=>parent.postMessage(e.data,'*');
      worker.onerror=()=>parent.postMessage({error:'Python运行环境加载失败'},'*');
      onmessage=e=>{if(e.source===parent)worker.postMessage(e.data);};
      parent.postMessage({loaded:true},'*');
    </script>`;
    let started = false;
    let timer: ReturnType<typeof setTimeout>;
    const cleanup = () => { clearTimeout(timer); window.removeEventListener("message", receive); signal.removeEventListener("abort", abort); frame.remove(); };
    const fail = (message: string) => { cleanup(); reject(new Error(message)); };
    const abort = () => fail("已停止运行");
    const receive = (event: MessageEvent) => {
      if (event.source !== frame.contentWindow) return;
      const data = event.data;
      if (!data || typeof data !== "object") return;
      if (data.loaded) frame.contentWindow?.postMessage({code, input}, "*");
      else if (data.ready && !started) { started = true; clearTimeout(timer); timer = setTimeout(() => fail("运行超过5秒，已停止"), 5000); }
      else if (typeof data.error === "string") fail(data.error.slice(0,2000));
      else if (typeof data.stdout === "string" && typeof data.stderr === "string") {
        cleanup(); resolve({stdout:data.stdout.slice(0,16000),stderr:data.stderr.slice(0,16000)});
      }
    };
    window.addEventListener("message", receive);
    signal.addEventListener("abort", abort, {once:true});
    timer = setTimeout(() => fail("Python环境加载超时，请检查网络后重试"), 60000);
    if (signal.aborted) abort(); else document.body.append(frame);
  });
}

export function sameOutput(actual: string, expected: string): boolean {
  return actual.replace(/\r\n/g,"\n").trimEnd() === expected.replace(/\r\n/g,"\n").trimEnd();
}
