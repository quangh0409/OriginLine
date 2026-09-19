import { z } from "zod";
import type {
  CreatePersonRequest,
  DateDual,
  DatePrecision,
  PersonDto,
  UpdatePersonRequest,
} from "@/types/api";

/**
 * Validation for the add/edit nhân khẩu form.
 *
 * Two shapes are deliberately kept apart:
 *  - `PersonFormValues` — what the inputs hold. Every date part is a STRING,
 *    because a half-typed year must not become `NaN` mid-keystroke.
 *  - `CreatePersonRequest` / `UpdatePersonRequest` — the contract shapes,
 *    produced by the mappers at the bottom of this file.
 *
 * Error text arrives as a translator, so messages are bilingual like the rest
 * of the UI rather than hard-coded Vietnamese inside the schema.
 *
 * Deliberately NOT validated here, because they are server truths:
 *  - `generation` — derived from the graph, the client never sends it
 *    (contracts/README §5.7);
 *  - kỵ húy collisions — the backend owns the ancestor scan and answers
 *    `409 KY_HUY_CONFLICT` (FR-1.6);
 *  - lunar <-> solar agreement — the two dates are entered from the clan book
 *    as recorded; converting or cross-checking them here would need the Hồ
 *    Ngọc Đức algorithm, which is a backend service.
 */

export type Translate = (key: string, values?: Record<string, string | number>) => string;

const DATE_PRECISIONS = ["DAY", "MONTH", "YEAR", "UNKNOWN"] as const;

function partialDateSchema(t: Translate) {
  return z
    .object({
      solar: z.string().trim().default(""),
      precision: z.enum(DATE_PRECISIONS).default("DAY"),
      lunarDay: z.string().trim().default(""),
      lunarMonth: z.string().trim().default(""),
      lunarYear: z.string().trim().default(""),
      lunarLeap: z.boolean().default(false),
    })
    .superRefine((value, ctx) => {
      const num = (raw: string) => (raw === "" ? null : Number(raw));
      const day = num(value.lunarDay);
      const month = num(value.lunarMonth);
      const year = num(value.lunarYear);

      if (day !== null && (!Number.isInteger(day) || day < 1 || day > 30)) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["lunarDay"], message: t("errors.lunarDay") });
      }
      if (month !== null && (!Number.isInteger(month) || month < 1 || month > 12)) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["lunarMonth"], message: t("errors.lunarMonth") });
      }
      if (year !== null && (!Number.isInteger(year) || year < 1 || year > 3000)) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["lunarYear"], message: t("errors.lunarYear") });
      }
      // A lunar day without its month is not a date anyone can compute a giỗ
      // from — the pair is what `death.lunar` needs to be usable.
      if (day !== null && month === null) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["lunarMonth"], message: t("errors.lunarMonthRequired") });
      }
    });
}

export function personFormSchema(t: Translate) {
  const dateSchema = partialDateSchema(t);

  return z
    .object({
      names: z
        .array(
          z.object({
            nameType: z.enum(["HUY", "TU", "HIEU", "THUY", "THUONG_GOI", "PHAP_DANH"]),
            fullName: z.string().trim().min(1, t("errors.nameRequired")).max(120, t("errors.nameTooLong")),
            nameHanNom: z.string().trim().max(60, t("errors.hanNomTooLong")).default(""),
            isPrimary: z.boolean().default(false),
            note: z.string().trim().max(200, t("errors.noteTooLong")).default(""),
          })
        )
        .min(1, t("errors.atLeastOneName")),
      gender: z.enum(["MALE", "FEMALE", "UNKNOWN"]),
      isAlive: z.boolean(),
      birth: dateSchema,
      death: dateSchema,
      nativePlace: z.string().trim().max(160).default(""),
      currentPlaceProvince: z.string().trim().max(160).default(""),
      currentPlaceFull: z.string().trim().max(300).default(""),
      occupation: z.string().trim().max(160).default(""),
      biography: z.string().trim().max(5000, t("errors.biographyTooLong")).default(""),
      contact: z.object({
        phone: z
          .string()
          .trim()
          .max(24)
          .refine((v) => v === "" || /^[+0-9][0-9 ().-]{5,23}$/.test(v), t("errors.phone"))
          .default(""),
        email: z
          .string()
          .trim()
          .max(160)
          .refine((v) => v === "" || z.string().email().safeParse(v).success, t("errors.email"))
          .default(""),
        zaloId: z.string().trim().max(64).default(""),
      }),
      primaryBranchId: z.string().trim().default(""),
      relationship: z.object({
        enabled: z.boolean().default(false),
        relType: z.enum(["PARENT_BIO", "PARENT_ADOPT", "SPOUSE", "HEIR"]),
        otherPersonId: z.string().trim().default(""),
        /** Which end of `from -> to` the ALREADY-EXISTING person occupies. */
        otherPersonRole: z.enum(["SOURCE", "TARGET"]),
        spouseOrder: z.string().trim().default(""),
      }),
      note: z.string().trim().max(500, t("errors.noteTooLong")).default(""),
    })
    .superRefine((value, ctx) => {
      const primaryCount = value.names.filter((n) => n.isPrimary).length;
      if (primaryCount !== 1) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["names"],
          message: t("errors.exactlyOnePrimary"),
        });
      }

      // KHÔNG còn coi "còn sống + có ngày mất" là lỗi.
      //
      // Hội đồng đã chốt: nhập ngày mất tức là suy ra người đó đã mất, hệ thống lưu luôn thay vì
      // từ chối (backend suy diễn y hệt). Chặn ở đây sẽ vô hiệu hoá cả quyết định đó. Thứ thay thế
      // không phải là "im lặng cho qua" mà là <DeathConfirmDialog>: một hộp thoại nêu rõ hệ quả
      // (hồ sơ chuyển sang công khai với cả Khách chưa đăng nhập theo BA v2 §10, và sinh lịch nhắc
      // giỗ) trước khi gửi — xem `effectiveIsAlive` bên dưới và `usePersonSubmit`.

      if (value.birth.solar !== "" && value.death.solar !== "" && value.death.solar < value.birth.solar) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["death", "solar"],
          message: t("errors.deathBeforeBirth"),
        });
      }

      if (value.relationship.enabled && value.relationship.otherPersonId === "") {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["relationship", "otherPersonId"],
          message: t("errors.relationPersonRequired"),
        });
      }
    });
}

export type PersonFormValues = z.infer<ReturnType<typeof personFormSchema>>;

/**
 * Người dùng có nhập gì vào phần ngày mất hay không (dương lịch hoặc bất kỳ ô âm lịch nào).
 *
 * Đây là dữ kiện, không phải lỗi: theo quyết định của Hội đồng, có ngày mất nghĩa là người đó đã
 * mất. Hàm này vừa quyết định cờ `isAlive` gửi lên máy chủ, vừa là điều kiện để form bật hộp
 * thoại xác nhận.
 */
export function hasDeathInput(death: PersonFormValues["death"]): boolean {
  return (
    death.solar !== "" ||
    death.lunarDay !== "" ||
    death.lunarMonth !== "" ||
    death.lunarYear !== ""
  );
}

/**
 * Trạng thái sống/mất THỰC SỰ được gửi đi khi công tắc và ngày mất nói hai điều khác nhau.
 *
 * <h2>Mặc định: ngày mất thắng</h2>
 * Nếu cứ gửi nguyên `values.isAlive` thì hai mapper bên dưới sẽ lẳng lặng vứt ngày mất vừa nhập
 * (`death: values.isAlive ? null : ...`) — người dùng bấm lưu, máy chủ trả 200, mà ngày giỗ thì
 * không có ở đâu cả. Đúng theo quyết định của Hội đồng: nhập ngày mất tức là người đó đã mất.
 *
 * <h2>Ngoại lệ: hồ sơ VỐN đã ghi là đã mất</h2>
 * Lúc ấy công tắc mới là tiếng nói duy nhất. Có người ghi nhầm một người đang sống là đã mất, và
 * đường sửa lại là gạt công tắc về "còn sống" — nếu ngày mất cũ (form nạp sẵn từ hồ sơ) vẫn thắng
 * thì phép đính chính đó KHÔNG BAO GIỜ chạy được, mà lại chạy im lặng: lưu xong vẫn "đã mất".
 * Ở chiều này cũng chẳng có suy diễn nào để làm — hồ sơ đã công khai từ trước, không có gì lộ thêm.
 *
 * @param original hồ sơ đã lưu (chỉ có ở chế độ sửa); bỏ trống khi thêm mới.
 */
export function effectiveIsAlive(values: PersonFormValues, original?: PersonDto | null): boolean {
  if (original && !original.isAlive) return values.isAlive;
  return values.isAlive && !hasDeathInput(values.death);
}

// ---------------------------------------------------------------------------
// Mapping: form values <-> contract shapes
// ---------------------------------------------------------------------------

const EMPTY_DATE: PersonFormValues["birth"] = {
  solar: "",
  precision: "DAY",
  lunarDay: "",
  lunarMonth: "",
  lunarYear: "",
  lunarLeap: false,
};

export function emptyPersonFormValues(): PersonFormValues {
  return {
    names: [{ nameType: "HUY", fullName: "", nameHanNom: "", isPrimary: true, note: "" }],
    gender: "MALE",
    isAlive: true,
    birth: { ...EMPTY_DATE },
    death: { ...EMPTY_DATE },
    nativePlace: "",
    currentPlaceProvince: "",
    currentPlaceFull: "",
    occupation: "",
    biography: "",
    contact: { phone: "", email: "", zaloId: "" },
    primaryBranchId: "",
    relationship: {
      enabled: false,
      relType: "PARENT_BIO",
      otherPersonId: "",
      otherPersonRole: "SOURCE",
      spouseOrder: "",
    },
    note: "",
  };
}

function dateToForm(date: DateDual | null | undefined): PersonFormValues["birth"] {
  if (!date) return { ...EMPTY_DATE };
  return {
    solar: date.solar ?? "",
    precision: date.precision ?? "DAY",
    lunarDay: date.lunar?.day != null ? String(date.lunar.day) : "",
    lunarMonth: date.lunar?.month != null ? String(date.lunar.month) : "",
    lunarYear: date.lunar?.year != null ? String(date.lunar.year) : "",
    lunarLeap: date.lunar?.leap ?? false,
  };
}

/**
 * Seed the edit form from a fetched profile.
 *
 * Note what this cannot know: fields the caller's tier hid come back absent
 * and therefore seed as empty. That is why the edit screen is gated on
 * `meta.canEdit` — an editor who cannot see Tier-3 data must not be able to
 * submit a form that would blank it.
 */
export function personToFormValues(person: PersonDto): PersonFormValues {
  const base = emptyPersonFormValues();
  return {
    ...base,
    names:
      person.names.length > 0
        ? person.names.map((n) => ({
            nameType: n.nameType,
            fullName: n.fullName,
            nameHanNom: n.nameHanNom ?? "",
            isPrimary: n.isPrimary,
            note: n.note ?? "",
          }))
        : base.names,
    gender: person.gender ?? "UNKNOWN",
    isAlive: person.isAlive,
    birth: dateToForm(person.birth),
    death: dateToForm(person.death),
    nativePlace: person.nativePlace ?? "",
    currentPlaceProvince: person.currentPlaceProvince ?? "",
    currentPlaceFull: person.currentPlaceFull ?? "",
    occupation: person.occupation ?? "",
    biography: person.biography ?? "",
    contact: {
      phone: person.contact?.phone ?? "",
      email: person.contact?.email ?? "",
      zaloId: person.contact?.zaloId ?? "",
    },
    primaryBranchId: person.primaryBranch?.id ?? "",
  };
}

/** Dựng mốc song lịch từ các ô nhập. Được xuất ra để hộp thoại xác nhận hiển thị lại
 * đúng ngày mất người dùng vừa gõ, bằng cùng một bộ định dạng như hồ sơ. */
export function formToDate(form: PersonFormValues["birth"]): DateDual | undefined {
  const hasLunar = form.lunarMonth !== "" && form.lunarYear !== "";
  if (form.solar === "" && !hasLunar) return undefined;

  return {
    solar: form.solar === "" ? null : form.solar,
    lunar: hasLunar
      ? {
          year: Number(form.lunarYear),
          month: Number(form.lunarMonth),
          day: form.lunarDay === "" ? 1 : Number(form.lunarDay),
          leap: form.lunarLeap,
        }
      : null,
    precision: form.precision as DatePrecision,
  };
}

const orNull = (value: string) => (value === "" ? null : value);

export function toCreateRequest(
  values: PersonFormValues,
  options: {
    confirmTabooOverride?: boolean;
    /** Cờ của `409 DUPLICATE_PERSON_SUSPECTED` — chỉ có ở `POST`, không có ở `PATCH`. */
    confirmDuplicateOverride?: boolean;
    overrideReason?: string;
  } = {}
): CreatePersonRequest {
  const contact =
    values.contact.phone || values.contact.email || values.contact.zaloId
      ? {
          phone: orNull(values.contact.phone),
          email: orNull(values.contact.email),
          zaloId: orNull(values.contact.zaloId),
        }
      : null;

  // Ngày mất suy ra trạng thái đã mất — xem effectiveIsAlive.
  const isAlive = effectiveIsAlive(values);

  return {
    names: values.names.map((n) => ({
      nameType: n.nameType,
      fullName: n.fullName,
      nameHanNom: orNull(n.nameHanNom),
      isPrimary: n.isPrimary,
      note: orNull(n.note),
    })),
    gender: values.gender,
    isAlive,
    birth: formToDate(values.birth) ?? null,
    death: isAlive ? null : formToDate(values.death) ?? null,
    nativePlace: orNull(values.nativePlace),
    currentPlaceProvince: orNull(values.currentPlaceProvince),
    currentPlaceFull: orNull(values.currentPlaceFull),
    occupation: orNull(values.occupation),
    biography: orNull(values.biography),
    primaryBranchId: orNull(values.primaryBranchId),
    contact,
    initialRelationships:
      values.relationship.enabled && values.relationship.otherPersonId
        ? [
            {
              relType: values.relationship.relType,
              otherPersonId: values.relationship.otherPersonId,
              otherPersonRole: values.relationship.otherPersonRole,
              spouseOrder:
                values.relationship.relType === "SPOUSE" && values.relationship.spouseOrder !== ""
                  ? Number(values.relationship.spouseOrder)
                  : null,
            },
          ]
        : undefined,
    // Never defaulted to true: the first POST must go WITHOUT it so the
    // backend gets the chance to raise a kỵ húy conflict (FR-1.6).
    confirmTabooOverride: options.confirmTabooOverride ? true : undefined,
    confirmDuplicateOverride: options.confirmDuplicateOverride ? true : undefined,
    note: options.overrideReason?.trim() || orNull(values.note),
  };
}

/**
 * `clearFields` rather than `null` is how the contract spells "erase this"
 * (contracts/README §5.2) — a `null` in a PATCH means nothing at all, so a
 * field the user emptied has to be named explicitly or it silently keeps its
 * old value.
 *
 * PRIVACY-CRITICAL: `original` is required, and a field is only ever added to
 * `clearFields` if it was PRESENT in `original`. A field hidden from this
 * caller by the tier filter arrives absent, seeds the input as empty, and
 * would otherwise look exactly like "the user deleted it" — a branch head
 * editing a living cousin's occupation would silently wipe the phone number
 * they were never allowed to see. Absent in, untouched out.
 */
/**
 * Danh sách tên như form ĐÃ ĐƯỢC NẠP từ `original`, chuẩn hoá y hệt lúc gửi đi.
 * Có nó mới so được "người dùng có thực sự sửa gì không".
 */
function seededNames(original: PersonDto) {
  return (original.names ?? []).map((n) => ({
    nameType: n.nameType,
    fullName: n.fullName,
    // `?? ""` trước khi qua orNull: DTO cho phép null/undefined, còn form luôn nạp thành chuỗi
    // rỗng. Chuẩn hoá cả hai về cùng một dạng, nếu không thì null vs undefined sẽ bị coi là
    // "đã sửa" và ta lại gửi `names` đi — đúng cái phép ghi đè cần tránh.
    nameHanNom: orNull(n.nameHanNom ?? ""),
    isPrimary: n.isPrimary,
    note: orNull(n.note ?? ""),
  }));
}

/** So sánh sâu theo giá trị. Thứ tự phần tử có ý nghĩa (form giữ nguyên thứ tự đã nạp). */
function unchanged(next: unknown, previous: unknown): boolean {
  return JSON.stringify(next ?? null) === JSON.stringify(previous ?? null);
}

/**
 * So một mốc song lịch do form dựng lại với mốc đã nhận từ máy chủ.
 *
 * <p>Không so thẳng bằng {@link unchanged} được: {@code formToDate} luôn trả đủ ba khoá theo một
 * thứ tự cố định và dùng {@code null} cho phần khuyết, còn DTO từ máy chủ có thể dùng
 * {@code undefined}, thiếu hẳn khoá, hoặc kèm {@code precision} mà form không hề chỉnh. Nếu để
 * những khác biệt hình thức đó bị tính là "đã sửa" thì ta lại gửi {@code birth} đi — đúng phép ghi
 * đè cần tránh, và mốc bị cắt còn năm sẽ đè lên ngày sinh thật.</p>
 */
function sameDate(next: DateDual | undefined, previous: DateDual | null | undefined): boolean {
  const normalize = (d: DateDual | null | undefined) =>
    d ? { solar: d.solar ?? null, lunar: d.lunar ?? null } : null;
  return JSON.stringify(normalize(next)) === JSON.stringify(normalize(previous));
}

/**
 * Dựng lệnh cập nhật, <b>chỉ gửi những gì thực sự đổi</b>.
 *
 * <h2>Vì sao không gửi hết cho gọn</h2>
 * `names`, `birth`, `death` trong `UpdatePersonRequest` có ngữ nghĩa <b>thay thế trọn vẹn</b>, và
 * người sửa <b>không phải lúc nào cũng thấy đủ dữ liệu</b> — phân tầng riêng tư cắt bớt trước khi
 * hồ sơ tới tay họ. Hai hệ quả đã được test bắt được:
 *
 * <ul>
 *   <li>Trưởng chi sửa một lỗi chính tả ở nghề nghiệp của người còn sống. Họ chỉ nhận được lớp tên
 *       chính, nên form gửi lại một mảng `names` một phần tử — <b>xoá sạch tên húy, tên tự, tên
 *       hiệu, tên thụy</b> của người đó. Với một hệ thống gia phả thì đây là mất mát không khôi
 *       phục được từ phía người dùng.</li>
 *   <li>Cũng người đó nhận `birth` đã bị cắt còn năm (`1990-01-01`, `precision: YEAR`). Gửi lại
 *       nguyên xi sẽ <b>ghi đè ngày sinh thật</b> và xoá luôn ngày sinh âm lịch.</li>
 * </ul>
 *
 * Cả hai đều im lặng: API trả 200, giao diện báo lưu thành công. Vì vậy quy tắc ở đây là bỏ qua
 * trường không đổi, chứ không phải "gửi hết rồi để backend lo".
 */
export function toUpdateRequest(
  values: PersonFormValues,
  original: PersonDto,
  options: { confirmTabooOverride?: boolean; overrideReason?: string } = {}
): UpdatePersonRequest {
  const clearFields: string[] = [];
  // Ngày mất suy ra trạng thái đã mất — trừ khi hồ sơ vốn đã ghi là đã mất, xem effectiveIsAlive.
  const isAlive = effectiveIsAlive(values, original);

  /** Send a value, clear it only if the caller could see the old one, else skip. */
  const diff = (field: keyof PersonDto, next: string): string | undefined => {
    if (next !== "") return next;
    const previous = original[field];
    if (previous !== undefined && previous !== null && previous !== "") {
      clearFields.push(field);
    }
    return undefined;
  };

  const nextNames = values.names.map((n) => ({
    nameType: n.nameType,
    fullName: n.fullName,
    nameHanNom: orNull(n.nameHanNom),
    isPrimary: n.isPrimary,
    note: orNull(n.note),
  }));

  const request: UpdatePersonRequest = {
    // `names` CHỈ được gửi khi người dùng thực sự sửa nó — xem ghi chú "mất dữ liệu âm thầm"
    // ở cuối hàm. `UpdatePersonRequest.names` thay thế TRỌN VẸN danh sách, nên gửi lại y nguyên
    // thứ mình nhận được vẫn là một phép ghi đè, và với người chỉ thấy một lớp tên thì phép ghi
    // đè ấy xoá sạch các lớp còn lại.
    names: unchanged(nextNames, seededNames(original)) ? undefined : nextNames,
    gender: values.gender,
    isAlive,
    nativePlace: diff("nativePlace", values.nativePlace),
    currentPlaceProvince: diff("currentPlaceProvince", values.currentPlaceProvince),
    currentPlaceFull: diff("currentPlaceFull", values.currentPlaceFull),
    occupation: diff("occupation", values.occupation),
    biography: diff("biography", values.biography),
    confirmTabooOverride: options.confirmTabooOverride ? true : undefined,
    note: options.overrideReason?.trim() || orNull(values.note) || undefined,
  };

  const birth = formToDate(values.birth);
  if (birth && !sameDate(birth, original.birth)) request.birth = birth;
  else if (!birth && original.birth) clearFields.push("birth");

  const death = isAlive ? undefined : formToDate(values.death);
  if (death && !sameDate(death, original.death)) request.death = death;
  else if (!death && original.death) clearFields.push("death");

  const contactFilled =
    values.contact.phone !== "" || values.contact.email !== "" || values.contact.zaloId !== "";
  if (contactFilled) {
    request.contact = {
      phone: orNull(values.contact.phone),
      email: orNull(values.contact.email),
      zaloId: orNull(values.contact.zaloId),
    };
  } else if (original.contact) {
    clearFields.push("contact");
  }

  if (values.primaryBranchId !== "") request.primaryBranchId = values.primaryBranchId;
  if (clearFields.length > 0) request.clearFields = clearFields;

  return request;
}
