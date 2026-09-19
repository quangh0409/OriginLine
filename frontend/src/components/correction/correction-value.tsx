"use client";

import { useTranslations } from "next-intl";
import { DualDate } from "@/components/person/dual-date";
import type { DateDual, Gender } from "@/types/api";
import { fieldSpec, isCorrectableField } from "./correctable-fields";

export interface CorrectionValueProps {
  /** Khoá trường trong `payload` (trùng tên trường `UpdatePersonRequest`). */
  fieldKey: string;
  value: unknown;
}

/**
 * Một giá trị đề nghị, hiển thị theo đúng kiểu của trường.
 *
 * Ngày tháng đi qua `<DualDate>` chứ không qua `JSON.stringify`: người duyệt
 * phải đọc được "ngày 18 tháng Chạp" chứ không phải một khối JSON, và **tháng
 * nhuận** phải hiện ra — bỏ sót nó là lỗi lệch giỗ trọn một tháng.
 *
 * Trả `null` khi không có gì để hiện. Nơi gọi phải chịu được `null` bằng cách
 * bỏ luôn cả hàng, chứ không được thay bằng gạch ngang: theo hợp đồng REST,
 * "không có dữ liệu" và "không được phép thấy" là hai thứ cố ý không phân biệt
 * được, và một gạch ngang là lời khẳng định rằng chỗ ấy trống.
 */
export function CorrectionValue({ fieldKey, value }: CorrectionValueProps) {
  const t = useTranslations("correction");
  const tForm = useTranslations("personForm");

  if (value === null || value === undefined || value === "") return null;

  const spec = isCorrectableField(fieldKey) ? fieldSpec(fieldKey) : undefined;

  if (spec?.kind === "date") {
    return <DualDate date={value as DateDual} />;
  }

  if (spec?.kind === "gender") {
    const gender = value as Gender;
    if (gender !== "MALE" && gender !== "FEMALE" && gender !== "UNKNOWN") return null;
    return <span>{tForm(`genderOption.${gender}`)}</span>;
  }

  if (typeof value === "string" || typeof value === "number") {
    return <span className="break-words">{String(value)}</span>;
  }

  if (typeof value === "boolean") {
    return <span>{value ? t("value.yes") : t("value.no")}</span>;
  }

  // Trường ngoài danh mục (một đề nghị do phiên bản sau gửi lên). Hiện được gì
  // thì hiện, còn hơn im lặng nuốt mất nội dung người ta đã viết.
  return <span className="break-all font-mono text-than">{JSON.stringify(value)}</span>;
}
