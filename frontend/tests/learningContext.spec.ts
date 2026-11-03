import { describe, it, expect } from "vitest";
import { learningContext } from "../src/shared/learningContext";
describe("learning material wire context", () => {
  it("sends only actual playback time for a video without synthetic page 1", () => {
    expect(learningContext({ kind: "video", pages: [] }, 1, 12.5)).toEqual({
      video_time: 12.5,
    });
  });
  it("sends the current PDF page and excludes a non-video timestamp", () => {
    expect(
      learningContext(
        {
          kind: "pdf",
          pages: [
            { id: 1, page_no: 1, title: "", text: "" },
            { id: 2, page_no: 2, title: "", text: "" },
          ],
        },
        2,
        0,
      ),
    ).toEqual({ page_no: 2 });
  });
  it("keeps original text page numbers and never invents a missing page", () => {
    const text = {
      kind: "text",
      pages: [{ id: 42, page_no: 42, title: "计算思维", text: "课程文本" }],
    };
    expect(learningContext(text, 42, 100)).toEqual({ page_no: 42 });
    expect(learningContext(text, 1, 0)).toEqual({});
  });
});
