import { describe, expect, it } from "vitest";
import { anyPresent, isPresent, presentOrUndefined } from "@/lib/privacy/present";

/**
 * `isPresent` is the single gate every optional API field passes through
 * before it can reach the screen (BA v2 §10). If it answers `true` for
 * something the backend actually withheld, a blank row appears — and a blank
 * row is exactly the "there is data here you may not see" signal the privacy
 * tiering exists to suppress.
 */
describe("isPresent — the privacy presence gate", () => {
  it("treats null and undefined as absent", () => {
    expect(isPresent(null)).toBe(false);
    expect(isPresent(undefined)).toBe(false);
  });

  it("treats an empty or whitespace-only string as absent", () => {
    expect(isPresent("")).toBe(false);
    expect(isPresent("   ")).toBe(false);
    expect(isPresent("\n\t ")).toBe(false);
  });

  it("treats a non-empty string as present, preserving leading/trailing spaces", () => {
    expect(isPresent("Nguyễn Văn An")).toBe(true);
    expect(isPresent(" x ")).toBe(true);
  });

  it("treats an empty array as absent and a populated one as present", () => {
    expect(isPresent([])).toBe(false);
    expect(isPresent(["HUY"])).toBe(true);
  });

  it("treats an object whose every leaf was filtered out as absent", () => {
    // A ContactInfo where phone, email and zaloId were all stripped by the
    // tier filter must not render a "Liên hệ" section.
    expect(isPresent({ phone: null, email: null, zaloId: undefined })).toBe(false);
    expect(isPresent({})).toBe(false);
  });

  it("treats an object as present when at least one leaf survived", () => {
    expect(isPresent({ phone: null, email: "an@example.com", zaloId: null })).toBe(true);
  });

  it("recurses into nested objects rather than trusting the top level", () => {
    expect(isPresent({ inner: { deeper: null } })).toBe(false);
    expect(isPresent({ inner: { deeper: "x" } })).toBe(true);
  });

  it("treats zero as a real value, not as absent", () => {
    // `generation: 0` and `collateralDegree: 0` (trực hệ) are meaningful —
    // dropping them would hide a fact the user is entitled to.
    expect(isPresent(0)).toBe(true);
  });

  it("treats false as a real value, not as absent", () => {
    // Callers that want "false means nothing to show" must coerce it
    // themselves (see PersonLifeDates: `value={showBirth || undefined}`).
    expect(isPresent(false)).toBe(true);
  });
});

describe("presentOrUndefined", () => {
  it("passes through a present value and erases an absent one", () => {
    expect(presentOrUndefined("Hà Nội")).toBe("Hà Nội");
    expect(presentOrUndefined("")).toBeUndefined();
    expect(presentOrUndefined(null)).toBeUndefined();
  });
});

describe("anyPresent — decides whether a whole section may render", () => {
  it("is false when every candidate field was filtered out", () => {
    expect(anyPresent(undefined, null, "", [], { a: null })).toBe(false);
  });

  it("is true as soon as one field survived", () => {
    expect(anyPresent(undefined, null, "Nam Định")).toBe(true);
  });

  it("is false with no arguments at all", () => {
    expect(anyPresent()).toBe(false);
  });
});
