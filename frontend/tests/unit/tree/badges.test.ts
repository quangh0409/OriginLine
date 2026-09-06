import { describe, expect, it } from "vitest";
import { BADGE_META, displayBadges } from "@/lib/tree/badges";
import type { PersonBadge } from "@/types/api";

/**
 * Badges are backend-authored (contracts/README §7.6) — dâu/rể in particular
 * are derived from SPOUSE edges plus bloodline on the server and must never
 * be inferred here. These tests guard the display layer only.
 */
const ALL_BADGES: PersonBadge[] = [
  "DICH_TON",
  "THUA_TU",
  "KE_TU",
  "CON_NUOI",
  "DAU",
  "RE",
  "TUYET_TU",
  "TRUONG_CHI",
  "DECEASED",
];

describe("BADGE_META", () => {
  it("has display metadata for every badge except DECEASED", () => {
    // A badge arriving from the backend with no entry here would render as
    // `undefined.color` and crash PersonNode mid-canvas.
    const expected = ALL_BADGES.filter((b) => b !== "DECEASED").sort();
    expect(Object.keys(BADGE_META).sort()).toEqual(expected);
  });

  it("carries both a Vietnamese and an English label for every badge", () => {
    for (const [badge, meta] of Object.entries(BADGE_META)) {
      expect(meta.vi, `vi label for ${badge}`).toBeTruthy();
      expect(meta.en, `en label for ${badge}`).toBeTruthy();
      expect(meta.color, `color for ${badge}`).toMatch(/^#[0-9a-fA-F]{6}$/);
    }
  });

  it("keeps the Vietnamese domain wording rather than translating it away", () => {
    expect(BADGE_META.DICH_TON.vi).toBe("Đích tôn");
    expect(BADGE_META.CON_NUOI.vi).toBe("Con nuôi");
    expect(BADGE_META.DAU.vi).toBe("Dâu");
    expect(BADGE_META.RE.vi).toBe("Rể");
    expect(BADGE_META.TUYET_TU.vi).toBe("Tuyệt tự");
  });

  it("gives dâu and rể visually distinct colours", () => {
    expect(BADGE_META.DAU.color).not.toBe(BADGE_META.RE.color);
  });
});

describe("displayBadges", () => {
  it("drops DECEASED, which drives the whole card's treatment instead of a tag", () => {
    expect(displayBadges(["DECEASED", "DICH_TON"])).toEqual(["DICH_TON"]);
  });

  it("returns an empty list for a person with no badges at all", () => {
    expect(displayBadges(undefined)).toEqual([]);
    expect(displayBadges([])).toEqual([]);
  });

  it("preserves the backend's badge order", () => {
    expect(displayBadges(["RE", "DECEASED", "TRUONG_CHI", "CON_NUOI"])).toEqual([
      "RE",
      "TRUONG_CHI",
      "CON_NUOI",
    ]);
  });

  it("keeps every non-DECEASED badge when a person carries several", () => {
    // Polygamy/adoption/heirship can genuinely stack: an adopted son can also
    // be the designated heir of a branch.
    expect(displayBadges(["CON_NUOI", "KE_TU", "TRUONG_CHI"])).toHaveLength(3);
  });
});
