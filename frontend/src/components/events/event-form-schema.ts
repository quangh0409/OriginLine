import { z } from "zod";
import type { EventCreateRequest, EventDto, EventType, EventUpdateRequest } from "@/types/api";

/**
 * Zod + mappers cho form tạo/sửa việc họ (F7 Đợt 2).
 *
 * Cùng nguyên tắc với `person-form-schema.ts`: ô nhập giữ CHUỖI (không phải
 * `number`) để một ký tự vừa gõ dở không hoá `NaN`, và mọi chuyển đổi kiểu nằm
 * ở `superRefine` + các hàm map cuối tệp — không lẫn vào JSX.
 *
 * <h2>KHÔNG có ô nhập ngày dương để tự quy đổi</h2>
 * Form chỉ nhận ngày ÂM (`lunarDay`/`lunarMonth`/`lunarLeap`/`lunarYear`). Ô
 * `solarDate` bên dưới KHÔNG phải một phép quy đổi hai chiều — nó là một mốc
 * dương tham chiếu, chỉ có nghĩa khi `recurringAnnually === false` (một việc
 * xảy ra một lần), và người dùng gõ nó y như trong sổ họ ghi, không suy ra từ
 * ngày âm. Máy chủ mới là nơi quy đổi âm → dương cho các lần lặp lại kế tiếp
 * (contracts/README §7.8).
 *
 * <h2>Cập nhật sau khi backend chốt hợp đồng thật</h2>
 * Ba trường `personId`/`location`/`lunarDate.year` là bản PO cập nhật SAU
 * đợt đầu, khi một agent backend đã xây xong `/events` thật và siết thêm hai
 * ràng buộc CSDL: `ck_event_gio_has_person` (GIO_THUONG luôn gắn một người)
 * và `ck_event_oneoff_lunar_year` (việc một lần luôn có năm âm). Không trường
 * nào trong bảy trường gốc bị đổi nghĩa hay bỏ.
 */

export type Translate = (key: string, values?: Record<string, string | number>) => string;

export const EVENT_TYPES: readonly EventType[] = [
  "GIO_TO",
  "GIO_HO",
  "GIO_CHI",
  "GIO_THUONG",
  "TIEU_TUONG",
  "DAI_TUONG",
  "CHAP_MA",
  "MUNG_THO",
  "SINH_NHAT",
  "KHANH_THANH",
  "HOP_HO",
  "CUOI_HOI",
  "KHAC",
];

/** `GIO_THUONG` luôn là giỗ của MỘT người — `ck_event_gio_has_person`. */
const EVENT_TYPES_REQUIRING_PERSON = new Set<EventType>(["GIO_THUONG"]);

export function eventTypeRequiresPerson(eventType: EventType): boolean {
  return EVENT_TYPES_REQUIRING_PERSON.has(eventType);
}

/** UI-only stand-in for `clanWide`/`scopeBranchId` — một ô chọn, không hai. */
export type EventScopeKind = "CLAN" | "BRANCH";

/**
 * Loại việc họ mà CHÍNH MÃ đã ấn định phạm vi — khớp từng chữ với
 * `EventTypeApiMapper.toWriteType` ở backend: `GIO_HO` và `GIO_CHI` cùng ánh
 * xạ về một giá trị CSDL (`TE_LE`) và phân biệt nhau **bằng chính cờ cấp dòng
 * họ**, không phải bằng một trường riêng.
 *
 * <h2>Vì sao form phải biết điều này, không chỉ máy chủ</h2>
 * `EventRequestMapper.scopeOf`/`toCommand` ném `IllegalArgumentException` (→
 * `422 VALIDATION_FAILED`) nếu `clanWideFlag` gửi lên khác với giá trị mã đã
 * ấn định. Trước bản sửa này, ô "Loại việc họ" và khối "Phạm vi" là hai điều
 * khiển ĐỘC LẬP trên form — không gì đồng bộ chúng — nên:
 *
 *  - chọn **Giỗ họ** rồi để phạm vi ở **Một chi/ngành** (giá trị mặc định của
 *    form) là hỏng chắc chắn;
 *  - với một Trưởng chi, hỏng ấy là **KHÔNG THỂ SỬA**: ô "Cả dòng họ" bị khoá
 *    theo vai (`canClanWide`), nên họ không có cách nào tự khớp hai ô lại —
 *    chọn Giỗ họ, với một Trưởng chi, luôn luôn thất bại.
 *
 * Xem cách dùng ở `event-form.tsx`: chọn một loại có mặt ở đây thì phạm vi tự
 * khớp theo và bị khoá, còn `GIO_HO` bị lọc khỏi danh sách chọn cho người
 * không có `canClanWide` — thay vì để họ chọn xong rồi nhận một lỗi 422 nói
 * chung chung "kiểm tra lại các trường".
 */
export const EVENT_TYPE_FORCED_SCOPE: Partial<Record<EventType, EventScopeKind>> = {
  GIO_HO: "CLAN",
  GIO_CHI: "BRANCH",
};

export function forcedScopeFor(eventType: EventType): EventScopeKind | null {
  return EVENT_TYPE_FORCED_SCOPE[eventType] ?? null;
}

export interface EventFormValues {
  eventType: EventType;
  title: string;
  description: string;
  location: string;
  /** Bắt buộc khi `eventTypeRequiresPerson(eventType)`. Rỗng = chưa chọn. */
  personId: string;
  lunarDay: string;
  lunarMonth: string;
  /** Bắt buộc khi `!recurringAnnually` (`ck_event_oneoff_lunar_year`). */
  lunarYear: string;
  lunarLeap: boolean;
  recurringAnnually: boolean;
  /** Chỉ có nghĩa khi `!recurringAnnually`. Chuỗi rỗng = chưa nhập. */
  solarDate: string;
  scope: EventScopeKind;
  /** Bắt buộc khi `scope === "BRANCH"`. */
  scopeBranchId: string;
}

export function emptyEventFormValues(): EventFormValues {
  return {
    eventType: "GIO_THUONG",
    title: "",
    description: "",
    location: "",
    personId: "",
    lunarDay: "",
    lunarMonth: "",
    lunarYear: "",
    lunarLeap: false,
    recurringAnnually: true,
    solarDate: "",
    scope: "BRANCH",
    scopeBranchId: "",
  };
}

const SOLAR_DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

export function eventFormSchema(t: Translate) {
  return z
    .object({
      eventType: z.enum(EVENT_TYPES as [EventType, ...EventType[]]),
      title: z.string().trim().min(1, t("errors.titleRequired")),
      description: z.string().default(""),
      location: z.string().default(""),
      personId: z.string().trim().default(""),
      lunarDay: z.string().trim(),
      lunarMonth: z.string().trim(),
      lunarYear: z.string().trim().default(""),
      lunarLeap: z.boolean().default(false),
      recurringAnnually: z.boolean().default(true),
      solarDate: z.string().trim().default(""),
      scope: z.enum(["CLAN", "BRANCH"]),
      scopeBranchId: z.string().trim().default(""),
    })
    .superRefine((value, ctx) => {
      const day = Number(value.lunarDay);
      if (!value.lunarDay || !Number.isInteger(day) || day < 1 || day > 30) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["lunarDay"], message: t("errors.lunarDay") });
      }
      const month = Number(value.lunarMonth);
      if (!value.lunarMonth || !Number.isInteger(month) || month < 1 || month > 12) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["lunarMonth"], message: t("errors.lunarMonth") });
      }
      if (value.solarDate && !SOLAR_DATE_RE.test(value.solarDate)) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["solarDate"], message: t("errors.solarDate") });
      }
      if (value.scope === "BRANCH" && value.scopeBranchId.length === 0) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["scopeBranchId"], message: t("errors.scopeRequired") });
      }
      if (eventTypeRequiresPerson(value.eventType) && value.personId.length === 0) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["personId"], message: t("errors.personRequired") });
      }
      if (!value.recurringAnnually) {
        const year = Number(value.lunarYear);
        if (!value.lunarYear || !Number.isInteger(year) || year < 1) {
          ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["lunarYear"], message: t("errors.lunarYear") });
        }
      }
    });
}

export function toEventRequest(values: EventFormValues): EventCreateRequest {
  const requiresPerson = eventTypeRequiresPerson(values.eventType);
  return {
    eventType: values.eventType,
    title: values.title.trim(),
    description: values.description.trim().length > 0 ? values.description.trim() : null,
    location: values.location.trim().length > 0 ? values.location.trim() : null,
    personId: requiresPerson && values.personId ? values.personId : null,
    lunarDate: {
      day: Number(values.lunarDay),
      month: Number(values.lunarMonth),
      // Bắt buộc khi việc chỉ xảy ra một lần — xem javadoc đầu tệp.
      year: !values.recurringAnnually && values.lunarYear ? Number(values.lunarYear) : undefined,
      leap: values.lunarLeap || undefined,
    },
    // Chỉ gửi mốc dương cho việc KHÔNG lặp lại — xem javadoc đầu tệp.
    solarDate:
      !values.recurringAnnually && values.solarDate.trim().length > 0
        ? values.solarDate.trim()
        : null,
    recurringAnnually: values.recurringAnnually,
    clanWide: values.scope === "CLAN",
    scopeBranchId: values.scope === "BRANCH" ? values.scopeBranchId : null,
  };
}

export const toCreateRequest = toEventRequest;
export const toUpdateRequest: (values: EventFormValues) => EventUpdateRequest = toEventRequest;

/**
 * `EventDto` (đọc) → giá trị form khi sửa.
 *
 * `recurringAnnually`/`solarDate` nay CÓ trên `EventDto` (bản backend chốt
 * sau đợt đầu) nên đọc lại được đúng giá trị đã lưu — không còn phải đoán.
 * Vẫn giữ một phòng hờ: bản ghi cũ (mock dựng trước khi trường này tồn tại)
 * có thể thiếu `recurringAnnually`, khi đó lùi về mặc định `true` và
 * `<EventEditScreen>` hiện cảnh báo — xem `event.recurringAnnually === undefined`
 * ở đó.
 */
export function eventToFormValues(event: EventDto): EventFormValues {
  return {
    eventType: event.eventType,
    title: event.title ?? "",
    description: event.note ?? "",
    location: event.location ?? "",
    personId: event.person?.id ?? "",
    lunarDay: String(event.lunarDate.day),
    lunarMonth: String(event.lunarDate.month),
    lunarYear: event.recurringAnnually === false ? String(event.lunarDate.year) : "",
    lunarLeap: event.lunarDate.leap,
    recurringAnnually: event.recurringAnnually ?? true,
    solarDate: event.solarDate ?? "",
    scope: event.isClanLevel ? "CLAN" : "BRANCH",
    scopeBranchId: event.targetBranch?.id ?? "",
  };
}
