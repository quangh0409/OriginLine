import { describe, expect, it } from "vitest";
import {
  NAME_TYPE_ORDER,
  headlineHanNom,
  headlineName,
  sortNameLayers,
  tabooName,
} from "@/lib/format/name-layers";
import type { PersonName } from "@/types/api";

const layer = (
  nameType: PersonName["nameType"],
  fullName: string,
  overrides: Partial<PersonName> = {}
): PersonName => ({ nameType, fullName, isPrimary: false, ...overrides });

describe("NAME_TYPE_ORDER — ritual weight, not alphabetical", () => {
  it("puts tên húy first and pháp danh last", () => {
    expect(NAME_TYPE_ORDER).toEqual([
      "HUY",
      "TU",
      "HIEU",
      "THUY",
      "THUONG_GOI",
      "PHAP_DANH",
    ]);
  });
});

describe("sortNameLayers", () => {
  it("orders layers by ritual weight regardless of input order", () => {
    const sorted = sortNameLayers([
      layer("PHAP_DANH", "Thích Minh Tâm"),
      layer("THUONG_GOI", "Ông Tổ"),
      layer("HUY", "Nguyễn Văn Tổ"),
      layer("THUY", "Trung Hậu Công"),
      layer("TU", "Đức Thành"),
      layer("HIEU", "Tùng Hiên"),
    ]);
    expect(sorted.map((n) => n.nameType)).toEqual(NAME_TYPE_ORDER);
  });

  it("floats the primary layer to the top of its own type", () => {
    const sorted = sortNameLayers([
      layer("TU", "Đức Thành"),
      layer("TU", "Minh Đạo", { isPrimary: true }),
    ]);
    expect(sorted[0]?.fullName).toBe("Minh Đạo");
  });

  it("does not mutate the caller's array", () => {
    const input = [layer("PHAP_DANH", "Diệu Thiện"), layer("HUY", "Nguyễn Văn Tổ")];
    const snapshot = input.map((n) => n.nameType);
    sortNameLayers(input);
    expect(input.map((n) => n.nameType)).toEqual(snapshot);
  });

  it("sinks an unrecognised name type to the end instead of throwing", () => {
    const sorted = sortNameLayers([
      { nameType: "XYZ" as PersonName["nameType"], fullName: "?", isPrimary: false },
      layer("HUY", "Nguyễn Văn Tổ"),
    ]);
    expect(sorted[0]?.nameType).toBe("HUY");
  });
});

describe("headlineName — the one name a heading may show", () => {
  it("prefers the server-rendered displayName because it is already tier-aware", () => {
    expect(
      headlineName({
        displayName: "Nguyễn Văn Thủy Tổ",
        names: [layer("HUY", "Nguyễn Văn Tổ", { isPrimary: true })],
      })
    ).toBe("Nguyễn Văn Thủy Tổ");
  });

  it("falls back to the primary layer when displayName was filtered out", () => {
    expect(
      headlineName({
        displayName: undefined,
        names: [layer("TU", "Đức Thành"), layer("HUY", "Nguyễn Văn Tổ", { isPrimary: true })],
      })
    ).toBe("Nguyễn Văn Tổ");
  });

  it("falls back to any surviving layer when no layer is marked primary", () => {
    expect(
      headlineName({ displayName: "", names: [layer("TU", "Đức Thành")] })
    ).toBe("Đức Thành");
  });

  it("returns undefined rather than inventing a placeholder when nothing survived", () => {
    expect(headlineName({ displayName: undefined, names: [] })).toBeUndefined();
    expect(
      headlineName({ displayName: "  ", names: [layer("HUY", "   ", { isPrimary: true })] })
    ).toBeUndefined();
  });
});

describe("headlineHanNom", () => {
  it("prefers the primary layer's Hán-Nôm", () => {
    expect(
      headlineHanNom({
        names: [
          layer("TU", "Đức Thành", { nameHanNom: "德成" }),
          layer("HUY", "Nguyễn Văn Tổ", { isPrimary: true, nameHanNom: "阮文祖" }),
        ],
      })
    ).toBe("阮文祖");
  });

  it("falls back to any layer carrying Hán-Nôm", () => {
    expect(
      headlineHanNom({
        names: [
          layer("HUY", "Nguyễn Văn Tổ", { isPrimary: true, nameHanNom: null }),
          layer("TU", "Đức Thành", { nameHanNom: "德成" }),
        ],
      })
    ).toBe("德成");
  });

  it("returns undefined when no layer carries Hán-Nôm", () => {
    expect(headlineHanNom({ names: [layer("HUY", "Nguyễn Văn Tổ", { isPrimary: true })] }))
      .toBeUndefined();
  });
});

describe("tabooName — tên húy, the name the kỵ húy check keys on", () => {
  it("returns only the HUY layer, never another layer's name", () => {
    const names = [
      layer("THUONG_GOI", "Ông Tổ", { isPrimary: true }),
      layer("HUY", "Nguyễn Văn Tổ"),
    ];
    expect(tabooName({ names })).toBe("Nguyễn Văn Tổ");
  });

  it("is undefined when the tier withheld the húy layer", () => {
    // At Tier 1 the backend returns only the primary layer; a living person's
    // tên húy simply is not on the wire.
    expect(tabooName({ names: [layer("THUONG_GOI", "Nguyễn Văn An", { isPrimary: true })] }))
      .toBeUndefined();
  });
});
