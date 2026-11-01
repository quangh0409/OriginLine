import { z } from "zod";
import type { ClanProfileDto, ClanProfileWriteBody, ClanRegion } from "@/lib/api/clan-profile";

/**
 * Zod + mappers cho **Bước 1 — Hồ sơ dòng họ** của `/quan-tri`.
 *
 * Cùng nguyên tắc `event-form-schema.ts`: ô nhập giữ CHUỖI (kể cả ngày/tháng
 * âm), mọi chuyển đổi kiểu nằm ở `superRefine` và các hàm map cuối tệp — JSX
 * không tự làm phép biến đổi nào.
 *
 * <h2>`rootBranchPath` — khoá theo NHÂN KHẨU đã có, không khoá theo "đã lưu"</h2>
 * Form không tự quyết định khoá; nơi gọi (`ClanProfileStep`) truyền `locked`
 * vào từ `readiness.persons.count > 0`, và schema không thêm ràng buộc nào cho
 * riêng ca khoá — ô bị `disabled` ở tầng UI, giá trị đọc lại y nguyên từ hồ sơ
 * hiện có nên vòng lặp lưu vẫn gửi đúng gốc cũ, không đổi.
 */

export type Translate = (key: string, values?: Record<string, string | number>) => string;

export interface ClanProfileFormValues {
  name: string;
  shortName: string;
  rootBranchPath: string;
  region: ClanRegion | "";
  ancestralHallName: string;
  ancestralHallAddress: string;
  ancestorName: string;
  /** Chuỗi rỗng = chưa nhập. Phải đi kèm `gioToMonth`, cả hai hoặc không cái nào. */
  gioToDay: string;
  gioToMonth: string;
  foundedNote: string;
}

export function emptyClanProfileFormValues(): ClanProfileFormValues {
  return {
    name: "",
    shortName: "",
    rootBranchPath: "",
    region: "",
    ancestralHallName: "",
    ancestralHallAddress: "",
    ancestorName: "",
    gioToDay: "",
    gioToMonth: "",
    foundedNote: "",
  };
}

/** `ClanProfileDto` (đọc) → giá trị form khi sửa một hồ sơ đã có. */
export function clanProfileToFormValues(dto: ClanProfileDto): ClanProfileFormValues {
  return {
    name: dto.name ?? "",
    shortName: dto.shortName ?? "",
    rootBranchPath: dto.rootBranchPath ?? "",
    region: dto.region ?? "",
    ancestralHallName: dto.ancestralHall?.name ?? "",
    ancestralHallAddress: dto.ancestralHall?.address ?? "",
    ancestorName: dto.ancestorName ?? "",
    gioToDay: dto.gioTo ? String(dto.gioTo.lunarDay) : "",
    gioToMonth: dto.gioTo ? String(dto.gioTo.lunarMonth) : "",
    foundedNote: dto.foundedNote ?? "",
  };
}

/**
 * Nhãn `ltree` hợp lệ: các đoạn chữ-số-gạch-dưới, phân cách bằng dấu chấm.
 * `rootBranchPath` là MỘT nhãn (không có dấu chấm) theo mô tả hợp đồng
 * ("một nhãn ltree, vd `ho_nguyen_dinh`") — nhưng luật ký tự dưới đây chấp
 * nhận cả dạng có dấu chấm phòng khi máy chủ cho phép một gốc lồng sẵn.
 */
const LTREE_LABEL_RE = /^[a-z0-9_]+(\.[a-z0-9_]+)*$/;

export function clanProfileFormSchema(t: Translate) {
  return z
    .object({
      name: z.string().trim().min(1, t("validation.nameRequired")).max(120, t("validation.nameTooLong")),
      shortName: z.string().trim().default(""),
      rootBranchPath: z.string().trim().min(1, t("validation.rootBranchRequired")),
      region: z.enum(["BAC", "TRUNG", "NAM", ""]),
      ancestralHallName: z.string().trim().default(""),
      ancestralHallAddress: z.string().trim().default(""),
      ancestorName: z.string().trim().default(""),
      gioToDay: z.string().trim().default(""),
      gioToMonth: z.string().trim().default(""),
      foundedNote: z.string().trim().max(2000, t("validation.foundedNoteTooLong")).default(""),
    })
    .superRefine((value, ctx) => {
      if (value.rootBranchPath && !LTREE_LABEL_RE.test(value.rootBranchPath)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["rootBranchPath"],
          message: t("validation.rootBranchInvalid"),
        });
      }
      if (!value.region) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["region"],
          message: t("validation.regionRequired"),
        });
      }
      // Ngày/tháng âm: cả hai hoặc không cái nào — một cặp thiếu một nửa
      // không dựng được một `GioToDto` có nghĩa.
      const hasDay = value.gioToDay.length > 0;
      const hasMonth = value.gioToMonth.length > 0;
      if (hasDay || hasMonth) {
        const day = Number(value.gioToDay);
        if (!hasDay || !Number.isInteger(day) || day < 1 || day > 30) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ["gioToDay"],
            message: t("validation.lunarDayRange"),
          });
        }
        const month = Number(value.gioToMonth);
        if (!hasMonth || !Number.isInteger(month) || month < 1 || month > 12) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ["gioToMonth"],
            message: t("validation.lunarMonthRange"),
          });
        }
      }
      // `AncestralHallDto.name` bắt buộc trong CHÍNH khối đó — địa chỉ không
      // đứng một mình được nếu chưa có tên từ đường.
      if (value.ancestralHallAddress.trim().length > 0 && value.ancestralHallName.trim().length === 0) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["ancestralHallName"],
          message: t("validation.ancestralHallNameRequired"),
        });
      }
    });
}

export function toClanProfileWriteBody(values: ClanProfileFormValues): ClanProfileWriteBody {
  const hasHall = values.ancestralHallName.trim().length > 0;
  const hasGioTo = values.gioToDay.length > 0 && values.gioToMonth.length > 0;

  return {
    name: values.name.trim(),
    shortName: values.shortName.trim() || undefined,
    // `region` đã được `superRefine` bảo đảm khác rỗng trước khi form submit được.
    region: values.region as ClanRegion,
    rootBranchPath: values.rootBranchPath.trim(),
    ancestralHall: hasHall
      ? {
          name: values.ancestralHallName.trim(),
          address: values.ancestralHallAddress.trim() || undefined,
        }
      : undefined,
    ancestorName: values.ancestorName.trim() || undefined,
    foundedNote: values.foundedNote.trim() || undefined,
    gioTo: hasGioTo
      ? { lunarDay: Number(values.gioToDay), lunarMonth: Number(values.gioToMonth) }
      : undefined,
  };
}
