import { narrationApi } from "@/features/materials/narration/api";
import { fileBlob } from "@/lib/api";
import { useEffect, useRef, useState } from "react";

export function usePageMedia(
  classroomId: number,
  materialId: number,
  pageNo: number | null,
  saved: string,
) {
  const [clip, setClip] = useState("");
  const [imageFailed, setImageFailed] = useState(false);
  const [pageImage, setPageImage] = useState("");
  const audioGeneration = useRef(0);
  useEffect(() => {
    // 切页必须停掉上一页：否则点过试听后翻页，旧音频会继续播放，看起来像"这一页在放"。
    ++audioGeneration.current;
    setClip("");
    return () => {
      ++audioGeneration.current;
    };
  }, [pageNo]);
  useEffect(() => {
    if (pageNo === null) return;
    let dropped = false;
    setPageImage((previous) => {
      if (previous) URL.revokeObjectURL(previous);
      return "";
    });
    void (async () => {
      try {
        const blob = await fileBlob(
          narrationApi.pageImage(classroomId, materialId, pageNo),
        );
        if (dropped) return;
        setPageImage(URL.createObjectURL(blob));
      } catch {
        if (!dropped) setImageFailed(true);
      }
    })();
    return () => {
      dropped = true;
    };
  }, [classroomId, materialId, pageNo]);
  useEffect(
    () => () => {
      setPageImage((previous) => {
        if (previous) URL.revokeObjectURL(previous);
        return "";
      });
    },
    [],
  );
  useEffect(() => setImageFailed(false), [pageNo, saved]);
  useEffect(
    () => () => {
      if (clip) URL.revokeObjectURL(clip);
    },
    [clip],
  );
  return {
    clip,
    setClip,
    imageFailed,
    setImageFailed,
    pageImage,
    setPageImage,
    audioGeneration,
  };
}
