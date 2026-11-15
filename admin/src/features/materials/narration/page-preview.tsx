import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ChevronLeft, ChevronRight } from "lucide-react";
import type { usePageMedia } from "./use-page-media";
import type { useNarrationReview } from "./use-review";
export function NarrationPagePreview({
  review,
  media,
}: {
  review: ReturnType<typeof useNarrationReview>;
  media: ReturnType<typeof usePageMedia>;
  classroomId: number;
  materialId: number;
}) {
  const { data, setPageNo, page, previous, next } = review;
  const { imageFailed, setImageFailed, pageImage } = media;
  if (!page || !data) return null;
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <Button
          size="sm"
          variant="ghost"
          disabled={!previous}
          onClick={() => previous && setPageNo(previous)}
        >
          <ChevronLeft /> 上一页
        </Button>
        <Button
          size="sm"
          variant="ghost"
          disabled={!next}
          onClick={() => next && setPageNo(next)}
        >
          下一页 <ChevronRight />
        </Button>
        <span className="hint">
          第 {page.page_no} / {data.coverage.pages} 页
        </span>
        <Input
          type="number"
          aria-label="跳到页码"
          className="ml-auto w-20"
          min={1}
          max={data.coverage.pages}
          placeholder="跳页"
          onKeyDown={(event) => {
            if (event.key !== "Enter") return;
            const target = Number((event.target as HTMLInputElement).value);
            if (data.pages.some((item) => item.page_no === target))
              setPageNo(target);
          }}
        />
      </div>
      <div className="flex max-h-[62vh] min-h-[280px] items-center justify-center overflow-auto rounded border bg-muted/30 p-2">
        {imageFailed ? (
          <p className="hint px-4 text-center">
            这一页没有可渲染的页面图像（文本资料或转换缺少预览）。
            <br />
            右侧仍可查看原文片段与讲解。
          </p>
        ) : (
          <img
            src={pageImage}
            alt={`第 ${page.page_no} 页`}
            className="max-h-[60vh] w-auto max-w-full"
            onError={() => setImageFailed(true)}
          />
        )}
      </div>
      <details className="text-sm">
        <summary className="cursor-pointer">
          页面原文（{page.source_chars} 字）
        </summary>
        <pre className="mt-1 max-h-32 overflow-y-auto whitespace-pre-wrap rounded border p-2 text-xs">
          {page.source_text || "（无文本层，内容在页面图像里）"}
        </pre>
      </details>
    </div>
  );
}
