import { responseError, token } from "@/lib/api";

export interface TransferBytes {
  transferred: number;
  total: number | null;
}

function transferred(event: ProgressEvent): TransferBytes {
  return {
    transferred: event.loaded,
    total: event.lengthComputable && event.total > 0 ? event.total : null,
  };
}

function transfer<T>(
  path: string,
  signal: AbortSignal,
  options:
    | {
        body: FormData;
        onBytes: (bytes: TransferBytes) => void;
        onSent: () => void;
      }
    | { onBytes: (bytes: TransferBytes) => void },
) {
  const captured = token();
  return new Promise<T>((resolve, reject) => {
    signal.throwIfAborted();
    const xhr = new XMLHttpRequest();
    const upload = "body" in options;

    if (upload) {
      xhr.upload.onprogress = (event) => options.onBytes(transferred(event));
      xhr.upload.onload = options.onSent;
    } else {
      const successful = () => xhr.status >= 200 && xhr.status < 300;
      xhr.onreadystatechange = () => {
        if (xhr.readyState !== XMLHttpRequest.HEADERS_RECEIVED || !successful())
          return;
        const length = Number(xhr.getResponseHeader("Content-Length"));
        options.onBytes({ transferred: 0, total: length > 0 ? length : null });
      };
      xhr.onprogress = (event) => {
        if (successful()) options.onBytes(transferred(event));
      };
    }

    async function readResponse() {
      signal.throwIfAborted();
      if (captured !== token())
        throw new Error("账号状态已改变，请重新发起文件请求");
      if (xhr.status < 200 || xhr.status >= 300) {
        const data = upload
          ? xhr.response
          : await new Response(xhr.response).json().catch(() => null);
        signal.throwIfAborted();
        throw responseError(xhr.status, data, captured, "课程包请求失败，请重试");
      }
      if (!xhr.response) throw new Error("课程包响应无效，请重试");
      return xhr.response as T;
    }
    const abort = () => xhr.abort();
    xhr.onload = () => void readResponse().then(resolve, reject);
    xhr.onerror = () => reject(new Error("课程包传输中断，请重试"));
    xhr.onabort = () =>
      reject(signal.reason ?? new Error("课程包传输已取消"));
    xhr.onloadend = () => signal.removeEventListener("abort", abort);
    xhr.open(upload ? "POST" : "GET", `/api${path}`);
    xhr.responseType = upload ? "json" : "blob";
    if (captured) xhr.setRequestHeader("Authorization", `Bearer ${captured}`);
    signal.addEventListener("abort", abort, { once: true });
    try {
      xhr.send(upload ? options.body : null);
    } catch (error) {
      signal.removeEventListener("abort", abort);
      reject(error);
    }
  });
}

export function uploadPackage<T>(
  path: string,
  file: File,
  signal: AbortSignal,
  onBytes: (bytes: TransferBytes) => void,
  onSent: () => void,
) {
  const body = new FormData();
  body.append("file", file);
  return transfer<T>(path, signal, { body, onBytes, onSent });
}

export function receivePackage(
  path: string,
  signal: AbortSignal,
  onBytes: (bytes: TransferBytes) => void,
) {
  return transfer<Blob>(path, signal, { onBytes });
}
