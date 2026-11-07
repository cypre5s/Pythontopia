import { afterEach, describe, expect, it } from "vitest";
import { migrateReadingPosition, pdfResumePage } from "../src/shared/readingPosition";

afterEach(() => sessionStorage.clear());

describe("reading position cache migration", () => {
  it("reuses the text reader's actual page when the same material becomes PDF", () => {
    const pages = [{ page_no: 34 }, { page_no: 42 }, { page_no: 43 }];
    expect(pdfResumePage(pages, undefined, null, 1)).toBe(42);
    expect(pdfResumePage(pages, 43, "34", 1)).toBe(43);
    expect(pdfResumePage(pages, undefined, "34", 1)).toBe(34);
    expect(pdfResumePage(pages, undefined, "NaN", 2)).toBe(43);
    expect(pdfResumePage(pages, undefined, "999", 0)).toBe(34);
  });
  it("keeps the most recent saved position and media offsets under the current material ID", () => {
    sessionStorage.setItem("read:1:2:material-186-1", "3");
    sessionStorage.setItem("read:1:2:material-186-2", "41");
    sessionStorage.setItem("read:1:2:material-186-1:pdf", "4");
    sessionStorage.setItem("read:1:2:material-186-2:pdf", "42");
    sessionStorage.setItem("read:1:2:material-186-2:video", "123.5");
    migrateReadingPosition(sessionStorage, 1, 2, 186);
    expect(sessionStorage.getItem("read:1:2:material-186")).toBe("41");
    expect(sessionStorage.getItem("read:1:2:material-186:pdf")).toBe("42");
    expect(sessionStorage.getItem("read:1:2:material-186:video")).toBe("123.5");
    expect(sessionStorage.length).toBe(3);
  });

  it("preserves current positions and leaves other users, classrooms and materials untouched", () => {
    const untouched = ["read:9:2:material-186-2", "read:1:9:material-186-2", "read:1:2:material-187-2", "read:1:2:material-186-not-a-number"];
    for (const key of untouched) sessionStorage.setItem(key, "77");
    sessionStorage.setItem("read:1:2:material-186", "43");
    sessionStorage.setItem("read:1:2:material-186-2", "41");
    migrateReadingPosition(sessionStorage, 1, 2, 186);
    migrateReadingPosition(sessionStorage, 1, 2, 186);
    expect(sessionStorage.getItem("read:1:2:material-186")).toBe("43");
    expect(sessionStorage.getItem("read:1:2:material-186-2")).toBeNull();
    for (const key of untouched) expect(sessionStorage.getItem(key)).toBe("77");
  });
});
