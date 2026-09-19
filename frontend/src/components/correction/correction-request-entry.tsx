"use client";

import { useState } from "react";
import { Button } from "antd";
import { EditOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import type { PersonDto } from "@/types/api";
import { CorrectionRequestDialog } from "./correction-request-dialog";

export interface CorrectionRequestEntryProps {
  person: PersonDto;
  /** `full` kèm câu giải thích (dùng trên trang); `compact` chỉ có nút (dùng trên thanh tiêu đề). */
  variant?: "full" | "compact";
  size?: "small" | "middle" | "large";
  /**
   * Mở sẵn hộp thoại. Dùng ở trang `/persons/{id}/correction`, nơi người dùng
   * đã bấm một lần rồi — bắt bấm lần thứ hai cho cùng một ý định là thừa.
   */
  autoOpen?: boolean;
}

/**
 * Nút **"Đề nghị đính chính"** — cửa vào duy nhất của luồng, đóng gói để chỗ
 * nào cũng thả xuống được (hồ sơ, ngăn kéo trên phả đồ, màn hình sửa).
 *
 * <h2>Hiện hay ẩn là câu trả lời của máy chủ</h2>
 * Chỉ dựa vào `meta.canRequestCorrection`. Đoán theo vai sẽ bỏ qua phạm vi
 * `ltree`: một Trưởng chi sửa thẳng được người trong chi mình nhưng với chi
 * khác thì cũng chỉ đề nghị được như mọi thành viên, và chỉ máy chủ biết ranh
 * giới ấy chạy ở đâu.
 *
 * <h2>Vì sao nút này quan trọng đến thế</h2>
 * Thành viên thường là người **phát hiện sai sót nhiều nhất** — họ biết rõ nhà
 * mình — nhưng lại là người **không có quyền sửa**. Không có nút này thì hai
 * điều đó không bao giờ gặp nhau, và cơ chế thu thập dữ liệu quan trọng nhất
 * của sản phẩm coi như không tồn tại.
 */
export function CorrectionRequestEntry({
  person,
  variant = "full",
  size = "middle",
  autoOpen = false,
}: CorrectionRequestEntryProps) {
  const t = useTranslations("correction");
  const [open, setOpen] = useState(autoOpen && person.meta.canRequestCorrection);

  if (!person.meta.canRequestCorrection) return null;

  return (
    <>
      {variant === "full" ? (
        <div className="rounded-lg border border-border bg-bg-card px-4 py-3">
          <p className="m-0 text-than font-medium text-text-main">{t("entry.title")}</p>
          <p className="m-0 mt-0.5 text-than leading-snug text-text-muted">{t("entry.hint")}</p>
          <Button
            className="mt-2"
            type="primary"
            size={size}
            icon={<EditOutlined aria-hidden />}
            onClick={() => setOpen(true)}
          >
            {t("entry.button")}
          </Button>
        </div>
      ) : (
        <Button size={size} icon={<EditOutlined aria-hidden />} onClick={() => setOpen(true)}>
          {t("entry.button")}
        </Button>
      )}

      <CorrectionRequestDialog person={person} open={open} onClose={() => setOpen(false)} />
    </>
  );
}
