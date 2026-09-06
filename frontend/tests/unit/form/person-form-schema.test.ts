import { describe, expect, it } from "vitest";
import {
  emptyPersonFormValues,
  personFormSchema,
  personToFormValues,
  toCreateRequest,
  toUpdateRequest,
  type PersonFormValues,
} from "@/components/person-form/person-form-schema";
import type { PersonDto } from "@/types/api";

/** Stand-in translator: returns the key, so assertions name the rule that fired. */
const t = (key: string) => key;
const schema = personFormSchema(t);

function values(overrides: Partial<PersonFormValues> = {}): PersonFormValues {
  const base = emptyPersonFormValues();
  return { ...base, ...overrides } as PersonFormValues;
}

/** All issue paths a parse produced, dot-joined. */
function issuePaths(input: unknown): string[] {
  const result = schema.safeParse(input);
  if (result.success) return [];
  return result.error.issues.map((i) => i.path.join("."));
}

function issueMessages(input: unknown): string[] {
  const result = schema.safeParse(input);
  if (result.success) return [];
  return result.error.issues.map((i) => i.message);
}

// ===========================================================================
// Validation
// ===========================================================================

describe("personFormSchema - names", () => {
  it("accepts a minimal valid person and rejects a blank one", () => {
    expect(schema.safeParse(values()).success).toBe(false); // empty fullName
    const ok = values({
      names: [{ nameType: "HUY", fullName: "Nguyễn Văn An", nameHanNom: "", isPrimary: true, note: "" }],
    });
    expect(schema.safeParse(ok).success).toBe(true);
  });

  it("rejects an empty full name", () => {
    expect(issueMessages(values())).toContain("errors.nameRequired");
  });

  it("rejects a name longer than 120 characters", () => {
    const long = values({
      names: [{ nameType: "HUY", fullName: "N".repeat(121), nameHanNom: "", isPrimary: true, note: "" }],
    });
    expect(issueMessages(long)).toContain("errors.nameTooLong");
  });

  it("requires exactly one primary layer - zero is invalid", () => {
    const none = values({
      names: [{ nameType: "HUY", fullName: "Nguyễn Văn An", nameHanNom: "", isPrimary: false, note: "" }],
    });
    expect(issueMessages(none)).toContain("errors.exactlyOnePrimary");
  });

  it("requires exactly one primary layer - two is invalid", () => {
    const two = values({
      names: [
        { nameType: "HUY", fullName: "Nguyễn Văn An", nameHanNom: "", isPrimary: true, note: "" },
        { nameType: "TU", fullName: "Đức Thành", nameHanNom: "", isPrimary: true, note: "" },
      ],
    });
    expect(issueMessages(two)).toContain("errors.exactlyOnePrimary");
  });

  it("accepts the full six-layer Vietnamese naming system at once", () => {
    const all = values({
      names: [
        { nameType: "HUY", fullName: "Nguyễn Văn Tổ", nameHanNom: "阮文祖", isPrimary: true, note: "" },
        { nameType: "TU", fullName: "Đức Thành", nameHanNom: "", isPrimary: false, note: "" },
        { nameType: "HIEU", fullName: "Tùng Hiên", nameHanNom: "", isPrimary: false, note: "" },
        { nameType: "THUY", fullName: "Trung Hậu Công", nameHanNom: "", isPrimary: false, note: "" },
        { nameType: "THUONG_GOI", fullName: "Ông Tổ", nameHanNom: "", isPrimary: false, note: "" },
        { nameType: "PHAP_DANH", fullName: "Thích Minh Tâm", nameHanNom: "", isPrimary: false, note: "" },
      ],
    });
    expect(schema.safeParse(all).success).toBe(true);
  });
});

describe("personFormSchema - dual-calendar dates", () => {
  const named = (overrides: Partial<PersonFormValues>) =>
    values({
      names: [{ nameType: "HUY", fullName: "Nguyễn Văn An", nameHanNom: "", isPrimary: true, note: "" }],
      ...overrides,
    });

  it("rejects a lunar day outside 1..30", () => {
    const bad = named({
      birth: { solar: "", precision: "DAY", lunarDay: "31", lunarMonth: "9", lunarYear: "1852", lunarLeap: false },
    });
    expect(issueMessages(bad)).toContain("errors.lunarDay");
    expect(issuePaths(bad)).toContain("birth.lunarDay");
  });

  it("accepts a lunar day of 30, which real lunar months have", () => {
    const ok = named({
      birth: { solar: "", precision: "DAY", lunarDay: "30", lunarMonth: "9", lunarYear: "1852", lunarLeap: false },
    });
    expect(schema.safeParse(ok).success).toBe(true);
  });

  it("rejects a lunar month outside 1..12", () => {
    const bad = named({
      birth: { solar: "", precision: "DAY", lunarDay: "1", lunarMonth: "13", lunarYear: "1852", lunarLeap: false },
    });
    expect(issueMessages(bad)).toContain("errors.lunarMonth");
  });

  it("accepts a leap lunar month, which is a real month number plus a flag", () => {
    // Tháng 9 nhuận is month 9 with leap=true, NOT month 13.
    const ok = named({
      isAlive: false,
      death: { solar: "", precision: "DAY", lunarDay: "22", lunarMonth: "9", lunarYear: "1852", lunarLeap: true },
    });
    expect(schema.safeParse(ok).success).toBe(true);
  });

  it("rejects a lunar day entered without its month - a giỗ needs both", () => {
    const bad = named({
      isAlive: false,
      death: { solar: "", precision: "DAY", lunarDay: "22", lunarMonth: "", lunarYear: "1852", lunarLeap: false },
    });
    expect(issueMessages(bad)).toContain("errors.lunarMonthRequired");
  });

  it("accepts a lunar month with no day - old clan books often record only that", () => {
    const ok = named({
      isAlive: false,
      death: { solar: "", precision: "MONTH", lunarDay: "", lunarMonth: "9", lunarYear: "1852", lunarLeap: false },
    });
    expect(schema.safeParse(ok).success).toBe(true);
  });

  /**
   * Hội đồng đã đảo quyết định: nhập ngày mất cho người đang sống KHÔNG còn là lỗi nhập liệu
   * nữa, hệ thống suy ra người đó đã mất và lưu (backend cũng vậy). Chặn lại ở tầng validate là
   * vô hiệu hoá luôn quyết định ấy. Thứ thay thế là hộp thoại xác nhận trước khi gửi — xem
   * tests/component/person-form-death.test.tsx.
   */
  it("no longer treats a death date on a living person as a validation error", () => {
    const withDeath = named({
      isAlive: true,
      death: { solar: "2020-01-01", precision: "DAY", lunarDay: "", lunarMonth: "", lunarYear: "", lunarLeap: false },
    });
    expect(schema.safeParse(withDeath).success).toBe(true);
  });

  it("accepts a death LUNAR date on a person still marked alive, not just a solar one", () => {
    const withLunarDeath = named({
      isAlive: true,
      death: { solar: "", precision: "DAY", lunarDay: "22", lunarMonth: "9", lunarYear: "2020", lunarLeap: false },
    });
    expect(schema.safeParse(withLunarDeath).success).toBe(true);
  });

  it("rejects a death date earlier than the birth date", () => {
    const bad = named({
      isAlive: false,
      birth: { solar: "1900-01-01", precision: "DAY", lunarDay: "", lunarMonth: "", lunarYear: "", lunarLeap: false },
      death: { solar: "1890-01-01", precision: "DAY", lunarDay: "", lunarMonth: "", lunarYear: "", lunarLeap: false },
    });
    expect(issueMessages(bad)).toContain("errors.deathBeforeBirth");
  });

  it("accepts UNKNOWN precision - an ancestor whose dates were never written down", () => {
    const ok = named({
      birth: { solar: "", precision: "UNKNOWN", lunarDay: "", lunarMonth: "", lunarYear: "", lunarLeap: false },
    });
    expect(schema.safeParse(ok).success).toBe(true);
  });
});

describe("personFormSchema - contact and relationship", () => {
  const named = (overrides: Partial<PersonFormValues>) =>
    values({
      names: [{ nameType: "HUY", fullName: "Nguyễn Văn An", nameHanNom: "", isPrimary: true, note: "" }],
      ...overrides,
    });

  it("accepts an empty contact block - contact is never required", () => {
    expect(schema.safeParse(named({})).success).toBe(true);
  });

  it("rejects a malformed phone number but accepts Vietnamese formats", () => {
    expect(issueMessages(named({ contact: { phone: "abc", email: "", zaloId: "" } })))
      .toContain("errors.phone");
    expect(schema.safeParse(named({ contact: { phone: "+84 912 345 678", email: "", zaloId: "" } })).success)
      .toBe(true);
    expect(schema.safeParse(named({ contact: { phone: "0912345678", email: "", zaloId: "" } })).success)
      .toBe(true);
  });

  it("rejects a malformed email", () => {
    expect(issueMessages(named({ contact: { phone: "", email: "an.nguyen", zaloId: "" } })))
      .toContain("errors.email");
  });

  it("requires a person to be picked once the relationship link is switched on", () => {
    const bad = named({
      relationship: {
        enabled: true,
        relType: "PARENT_BIO",
        otherPersonId: "",
        otherPersonRole: "SOURCE",
        spouseOrder: "",
      },
    });
    expect(issueMessages(bad)).toContain("errors.relationPersonRequired");
  });
});

// ===========================================================================
// Mapping form -> contract
// ===========================================================================

describe("toCreateRequest", () => {
  const filled = values({
    names: [
      { nameType: "HUY", fullName: "Nguyễn Văn Tổ", nameHanNom: "阮文祖", isPrimary: true, note: "" },
      { nameType: "TU", fullName: "Đức Thành", nameHanNom: "", isPrimary: false, note: "" },
    ],
    isAlive: false,
    gender: "MALE",
    nativePlace: "Nam Định",
    death: { solar: "1852-11-03", precision: "DAY", lunarDay: "22", lunarMonth: "9", lunarYear: "1852", lunarLeap: false },
  });

  it("NEVER defaults confirmTabooOverride to true - the first POST must be able to 409", () => {
    // Sending it on the first call silently deletes the kỵ húy feature
    // (FR-1.6, contracts/README section 7.9).
    expect(toCreateRequest(filled).confirmTabooOverride).toBeUndefined();
  });

  it("sets confirmTabooOverride only on the explicit second attempt", () => {
    expect(toCreateRequest(filled, { confirmTabooOverride: true }).confirmTabooOverride).toBe(true);
  });

  it("carries the override reason as the audited note", () => {
    const req = toCreateRequest(filled, {
      confirmTabooOverride: true,
      overrideReason: "Hội đồng Tộc biểu đã chấp thuận ngày 12/3",
    });
    expect(req.note).toBe("Hội đồng Tộc biểu đã chấp thuận ngày 12/3");
  });

  it("keeps every name layer rather than collapsing to one name", () => {
    expect(toCreateRequest(filled).names.map((n) => n.nameType)).toEqual(["HUY", "TU"]);
  });

  it("keeps the lunar death date, which is the giỗ source of truth", () => {
    expect(toCreateRequest(filled).death).toMatchObject({
      solar: "1852-11-03",
      lunar: { year: 1852, month: 9, day: 22, leap: false },
      precision: "DAY",
    });
  });

  it("preserves the leap-month flag on the way out", () => {
    const leap = values({
      ...filled,
      death: { solar: "", precision: "DAY", lunarDay: "22", lunarMonth: "9", lunarYear: "1852", lunarLeap: true },
    });
    expect(toCreateRequest(leap).death?.lunar?.leap).toBe(true);
  });

  it("drops the death date for someone with no death date at all", () => {
    const alive = values({ ...filled, isAlive: true, death: emptyPersonFormValues().death });
    expect(toCreateRequest(alive).death).toBeNull();
    expect(toCreateRequest(alive).isAlive).toBe(true);
  });

  /**
   * Ngày mất suy ra trạng thái đã mất (quyết định của Hội đồng). Nếu để `isAlive` trên form
   * thắng, mapper sẽ vứt luôn ngày mất vừa nhập: người dùng bấm lưu, máy chủ trả 200, mà ngày
   * giỗ thì không có ở đâu cả — mất dữ liệu trong im lặng.
   */
  it("infers 'deceased' from a death date even when the switch still says living", () => {
    const alive = values({ ...filled, isAlive: true });
    const req = toCreateRequest(alive);
    expect(req.isAlive).toBe(false);
    expect(req.death).toMatchObject({ solar: "1852-11-03" });
  });

  it("infers it from a lunar-only death date too - old clan books record nothing else", () => {
    const alive = values({
      ...filled,
      isAlive: true,
      death: { solar: "", precision: "DAY", lunarDay: "22", lunarMonth: "9", lunarYear: "1852", lunarLeap: false },
    });
    const req = toCreateRequest(alive);
    expect(req.isAlive).toBe(false);
    expect(req.death?.lunar).toMatchObject({ month: 9, day: 22, year: 1852 });
  });

  it("omits a lunar date that has no month, rather than inventing one", () => {
    const partial = values({
      ...filled,
      death: { solar: "1852-11-03", precision: "DAY", lunarDay: "22", lunarMonth: "", lunarYear: "", lunarLeap: false },
    });
    expect(toCreateRequest(partial).death?.lunar).toBeNull();
  });

  it("sends null, not an empty string, for untouched optional text fields", () => {
    const req = toCreateRequest(filled);
    expect(req.occupation).toBeNull();
    expect(req.biography).toBeNull();
    expect(req.contact).toBeNull();
  });

  it("never sends generation - the backend derives it from the graph", () => {
    expect(Object.keys(toCreateRequest(filled))).not.toContain("generation");
  });

  it("emits an initial relationship link only when one was actually chosen", () => {
    expect(toCreateRequest(filled).initialRelationships).toBeUndefined();
    const linked = values({
      ...filled,
      relationship: {
        enabled: true,
        relType: "SPOUSE",
        otherPersonId: "p-001",
        otherPersonRole: "SOURCE",
        spouseOrder: "2",
      },
    });
    expect(toCreateRequest(linked).initialRelationships).toEqual([
      {
        relType: "SPOUSE",
        otherPersonId: "p-001",
        otherPersonRole: "SOURCE",
        spouseOrder: 2,
      },
    ]);
  });

  it("only sends spouseOrder for a SPOUSE link", () => {
    const parent = values({
      ...filled,
      relationship: {
        enabled: true,
        relType: "PARENT_BIO",
        otherPersonId: "p-001",
        otherPersonRole: "SOURCE",
        spouseOrder: "2",
      },
    });
    expect(toCreateRequest(parent).initialRelationships?.[0]?.spouseOrder).toBeNull();
  });
});

// ===========================================================================
// Mapping form -> PATCH, the privacy-critical direction
// ===========================================================================

/**
 * What a branch head (Tier 2) receives for a living person: primary name
 * layer only, birth truncated to the year with the lunar half dropped, no
 * contact, no biography. Mirrors src/mocks/privacy.ts, which mirrors the
 * backend's PrivacyTierFilter.
 */
const TIER2_LIVING: PersonDto = {
  id: "p-100",
  isAlive: true,
  generation: 5,
  gender: "MALE",
  displayName: "Nguyễn Văn An",
  names: [{ nameType: "THUONG_GOI", fullName: "Nguyễn Văn An", isPrimary: true }],
  birth: { solar: "1990-01-01", lunar: undefined, precision: "YEAR" },
  occupation: "Kỹ sư phần mềm",
  currentPlaceProvince: "Hà Nội",
  nativePlace: "Nam Định",
  primaryBranch: { id: "b-chi1", name: "Chi Nhất", path: "root.chi_nhat", region: "BAC" },
  version: 3,
  meta: { visibleTier: "T2", canEdit: true, canDelete: false, canRequestCorrection: false },
};

/** Full-fidelity record, as an admin (Tier 3) would receive it. */
const TIER3_LIVING: PersonDto = {
  ...TIER2_LIVING,
  names: [
    { nameType: "HUY", fullName: "Nguyễn Văn An", isPrimary: true },
    { nameType: "TU", fullName: "Đức Thành", isPrimary: false },
    { nameType: "THUONG_GOI", fullName: "Anh An", isPrimary: false },
  ],
  birth: {
    solar: "1990-07-20",
    lunar: { year: 1990, month: 5, day: 28, leap: false },
    precision: "DAY",
  },
  contact: { phone: "+84 912 345 678", email: "an.nguyen@example.com" },
  biography: "Ghi chép trong gia phả.",
  meta: { visibleTier: "T3", canEdit: true, canDelete: true, canRequestCorrection: false },
};

describe("toUpdateRequest - must not destroy what the caller could not see", () => {
  it("clears a field the caller could see and deliberately emptied", () => {
    const form = personToFormValues(TIER3_LIVING);
    const req = toUpdateRequest({ ...form, occupation: "" }, TIER3_LIVING);
    expect(req.clearFields).toContain("occupation");
    expect(req.occupation).toBeUndefined();
  });

  it("does NOT clear a field that was hidden by the caller's tier", () => {
    // biography and contact are Tier 3: absent for a branch head, they seed
    // as "" and must never be reported as "the user deleted it".
    const form = personToFormValues(TIER2_LIVING);
    const req = toUpdateRequest(form, TIER2_LIVING);
    expect(req.clearFields ?? []).not.toContain("biography");
    expect(req.clearFields ?? []).not.toContain("contact");
  });

  it("keeps a value the caller edited", () => {
    const form = personToFormValues(TIER3_LIVING);
    const req = toUpdateRequest({ ...form, occupation: "Giáo viên" }, TIER3_LIVING);
    expect(req.occupation).toBe("Giáo viên");
    expect(req.clearFields ?? []).not.toContain("occupation");
  });

  it("never defaults confirmTabooOverride on an edit either", () => {
    const form = personToFormValues(TIER3_LIVING);
    expect(toUpdateRequest(form, TIER3_LIVING).confirmTabooOverride).toBeUndefined();
    expect(
      toUpdateRequest(form, TIER3_LIVING, { confirmTabooOverride: true }).confirmTabooOverride
    ).toBe(true);
  });

  /**
   * Đường đính chính ngược: một người đang sống bị ghi nhầm là đã mất. Người sửa gạt công tắc về
   * "còn sống", nhưng form thì vẫn còn nguyên ngày mất cũ nạp từ hồ sơ. Nếu ngày mất cứ thắng
   * như trường hợp thêm mới thì phép đính chính này KHÔNG BAO GIỜ chạy, mà lại hỏng trong im
   * lặng — lưu xong vẫn "đã mất". Hồ sơ vốn đã ghi là đã mất thì công tắc phải thắng.
   */
  it("clears the death date when a record is corrected to 'still living'", () => {
    const deceased: PersonDto = {
      ...TIER3_LIVING,
      isAlive: false,
      death: { solar: "2020-01-01", precision: "DAY" },
    };
    const form = personToFormValues(deceased);
    const req = toUpdateRequest({ ...form, isAlive: true }, deceased);
    expect(req.clearFields).toContain("death");
    expect(req.death).toBeUndefined();
  });

  /**
   * BUG WATCH - a Tier-2 edit silently deletes the other name layers.
   *
   * UpdatePersonRequest.names REPLACES the whole list (src/types/api.ts). A
   * branch head has meta.canEdit === true for a living person but only ever
   * receives the PRIMARY name layer. Saving any unrelated change therefore
   * ships a one-element names array and wipes tên húy / tên tự / tên hiệu -
   * the exact class of loss toUpdateRequest's own doc comment ("Absent in,
   * untouched out") promises to prevent.
   */
  it("does not resend the name list when the caller only received part of it", () => {
    const form = personToFormValues(TIER2_LIVING);
    const req = toUpdateRequest({ ...form, occupation: "Giáo viên" }, TIER2_LIVING);
    expect(
      req.names,
      "a caller shown only the primary layer must not overwrite the full name list"
    ).toBeUndefined();
  });

  /**
   * BUG WATCH - a Tier-2 edit overwrites the real birth date with the
   * year-truncated filler.
   *
   * A branch head receives birth = { solar: "1990-01-01", precision: "YEAR" }
   * with the lunar half dropped. The form seeds from that and toUpdateRequest
   * sends it back unconditionally, so the person's true birth day AND their
   * lunar birth date are replaced by a January 1st nobody typed.
   */
  it("does not resend a birth date it only received truncated to the year", () => {
    const form = personToFormValues(TIER2_LIVING);
    const req = toUpdateRequest({ ...form, occupation: "Giáo viên" }, TIER2_LIVING);
    expect(
      req.birth,
      "a year-truncated birth date must not be written back over the full one"
    ).toBeUndefined();
  });
});

describe("personToFormValues", () => {
  it("round-trips a full-fidelity record through the form", () => {
    const form = personToFormValues(TIER3_LIVING);
    expect(form.names).toHaveLength(3);
    expect(form.birth).toMatchObject({
      solar: "1990-07-20",
      lunarDay: "28",
      lunarMonth: "5",
      lunarYear: "1990",
      lunarLeap: false,
    });
    expect(form.contact.phone).toBe("+84 912 345 678");
  });

  it("keeps a default name row when the record somehow carries none", () => {
    const nameless: PersonDto = { ...TIER3_LIVING, names: [] };
    expect(personToFormValues(nameless).names).toHaveLength(1);
  });

  it("seeds hidden fields as empty, which is why toUpdateRequest needs the original", () => {
    const form = personToFormValues(TIER2_LIVING);
    expect(form.biography).toBe("");
    expect(form.contact).toEqual({ phone: "", email: "", zaloId: "" });
  });
});
