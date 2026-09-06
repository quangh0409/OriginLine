import { describe, expect, it } from "vitest";
import {
  formatSolar,
  hasDisplayableDate,
  lunarParts,
  solarYear,
} from "@/lib/format/date-dual";
import type { DateDual } from "@/types/api";

const dual = (overrides: Partial<DateDual>): DateDual => ({
  precision: "DAY",
  ...overrides,
});

describe("formatSolar — precision decides how much may reach the screen", () => {
  it("renders a full day-precision date day-first, Vietnamese convention", () => {
    expect(formatSolar(dual({ solar: "1990-07-20", precision: "DAY" }))).toBe("20/07/1990");
  });

  it("renders month precision as MM/YYYY", () => {
    expect(formatSolar(dual({ solar: "1990-07-20", precision: "MONTH" }))).toBe("07/1990");
  });

  it("renders year precision as the year alone even when the wire carried a full date", () => {
    // Tier 2 truncation sends `1990-01-01` as filler. Printing "01/01/1990"
    // would be both false and a privacy regression.
    expect(formatSolar(dual({ solar: "1990-01-01", precision: "YEAR" }))).toBe("1990");
  });

  it("renders nothing at all for UNKNOWN precision", () => {
    expect(formatSolar(dual({ solar: "1990-07-20", precision: "UNKNOWN" }))).toBeUndefined();
  });

  it("degrades to the year when a DAY-precision record only carries a year", () => {
    expect(formatSolar(dual({ solar: "1852", precision: "DAY" }))).toBe("1852");
  });

  it("degrades to the year when a MONTH-precision record only carries a year", () => {
    expect(formatSolar(dual({ solar: "1852", precision: "MONTH" }))).toBe("1852");
  });

  it("is undefined when the solar half was filtered out or is unusable", () => {
    expect(formatSolar(undefined)).toBeUndefined();
    expect(formatSolar(null)).toBeUndefined();
    expect(formatSolar(dual({ solar: null }))).toBeUndefined();
    expect(formatSolar(dual({ solar: "" }))).toBeUndefined();
    expect(formatSolar(dual({ solar: "không rõ" }))).toBeUndefined();
  });

  it("tolerates surrounding whitespace from the wire", () => {
    expect(formatSolar(dual({ solar: " 1990-07-20 " }))).toBe("20/07/1990");
  });
});

describe("solarYear — compact contexts (cards, pickers)", () => {
  it("extracts the Gregorian year", () => {
    expect(solarYear(dual({ solar: "1852-11-03" }))).toBe("1852");
  });

  it("ignores precision, because a year is all it ever shows", () => {
    expect(solarYear(dual({ solar: "1852-11-03", precision: "UNKNOWN" }))).toBe("1852");
  });

  it("is undefined when there is no solar half", () => {
    expect(solarYear(dual({ solar: null, lunar: { year: 1852, month: 9, day: 22, leap: false } })))
      .toBeUndefined();
  });
});

describe("lunarParts — the giỗ half of a dual date", () => {
  const lunar = { year: 1852, month: 9, day: 22, leap: false };

  it("surfaces day, month and year at DAY precision", () => {
    expect(lunarParts(dual({ lunar, precision: "DAY" }))).toMatchObject({
      day: 22,
      month: 9,
      year: 1852,
      granularity: "DAY",
    });
  });

  it("never drops the leap-month flag — that is the giỗ-off-by-a-month bug", () => {
    const parts = lunarParts(dual({ lunar: { ...lunar, leap: true } }));
    expect(parts?.leap).toBe(true);
  });

  it("narrows granularity to MONTH and YEAR with the precision", () => {
    expect(lunarParts(dual({ lunar, precision: "MONTH" }))?.granularity).toBe("MONTH");
    expect(lunarParts(dual({ lunar, precision: "YEAR" }))?.granularity).toBe("YEAR");
  });

  it("renders nothing at UNKNOWN precision", () => {
    expect(lunarParts(dual({ lunar, precision: "UNKNOWN" }))).toBeUndefined();
  });

  it("is undefined when the lunar half was not sent", () => {
    expect(lunarParts(dual({ solar: "1990-07-20", lunar: null }))).toBeUndefined();
    expect(lunarParts(undefined)).toBeUndefined();
  });

  it("passes can chi and year label through only when present", () => {
    expect(lunarParts(dual({ lunar: { ...lunar, canChi: "Nhâm Tý" } }))?.canChi).toBe("Nhâm Tý");
    expect(lunarParts(dual({ lunar: { ...lunar, canChi: null } }))?.canChi).toBeUndefined();
    expect(lunarParts(dual({ lunar: { ...lunar, canChi: "  " } }))?.canChi).toBeUndefined();
  });
});

describe("hasDisplayableDate — gates the whole 'ngày sinh – ngày mất' section", () => {
  it("is true when only the lunar half survives (old clan books record just that)", () => {
    expect(
      hasDisplayableDate(dual({ solar: null, lunar: { year: 1852, month: 9, day: 22, leap: false } }))
    ).toBe(true);
  });

  it("is true when only the solar half survives", () => {
    expect(hasDisplayableDate(dual({ solar: "1852-11-03", lunar: null }))).toBe(true);
  });

  it("is false when the record carries nothing renderable", () => {
    expect(hasDisplayableDate(undefined)).toBe(false);
    expect(hasDisplayableDate(null)).toBe(false);
    expect(hasDisplayableDate(dual({ solar: null, lunar: null }))).toBe(false);
    expect(
      hasDisplayableDate(
        dual({ solar: "1852-11-03", lunar: { year: 1852, month: 9, day: 22, leap: false }, precision: "UNKNOWN" })
      )
    ).toBe(false);
  });
});
