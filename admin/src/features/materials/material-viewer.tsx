import { Empty, ErrorNotice, Loading } from "@/components/common";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { errorMessage, fileBlob } from "@/lib/api";
import { useEffect, useState } from "react";
import { MaterialPreview } from "./types";
export function MaterialViewer({
  material,
  onClose,
}: {
  material: MaterialPreview;
  onClose: () => void;
}) {
  const [url, setUrl] = useState(""),
    [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    let objectUrl = "";
    if (
      material.media_url &&
      (material.kind === "pdf" ||
        material.kind === "presentation" ||
        material.kind === "video")
    ) {
      fileBlob(material.media_url.replace(/^\/api/, ""))
        .then((blob) => {
          if (active) {
            objectUrl = URL.createObjectURL(blob);
            setUrl(objectUrl);
          }
        })
        .catch((e) => {
          if (active) setError(errorMessage(e));
        });
    }
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [material]);
  return (
    <Dialog
      open
      onOpenChange={(v) => {
        if (!v) onClose();
      }}
    >
      <DialogContent className="sm:max-w-5xl">
        <DialogHeader>
          <DialogTitle>{material.title}</DialogTitle>
          <DialogDescription>
            资料预览 · {material.kind.toUpperCase()}
          </DialogDescription>
        </DialogHeader>
        <ErrorNotice message={error} />
        {material.notice && <p className="hint">{material.notice}</p>}
        {url ? (
          material.kind === "video" ? (
            <video controls src={url} className="w-full max-h-[65vh]" />
          ) : (
            <iframe
              title={material.title}
              src={url}
              className="w-full h-[65vh] rounded border"
            />
          )
        ) : material.pages?.length ? (
          <div className="max-h-[65vh] overflow-y-auto space-y-6">
            {material.pages.map((p, i) => (
              <section key={p.id ?? i}>
                <h3 className="font-semibold">
                  {p.title || `第 ${p.page_no ?? i + 1} 页`}
                </h3>
                <p className="whitespace-pre-wrap leading-7 text-sm mt-3">
                  {p.text ?? p.content ?? "暂无提取文字"}
                </p>
              </section>
            ))}
          </div>
        ) : error ? null : material.media_url &&
          (material.kind === "pdf" ||
            material.kind === "presentation" ||
            material.kind === "video") ? (
          <Loading />
        ) : (
          <Empty title="此资料暂无可预览内容" />
        )}
      </DialogContent>
    </Dialog>
  );
}
