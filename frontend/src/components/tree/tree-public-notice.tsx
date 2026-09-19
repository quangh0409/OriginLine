"use client";

import { Button } from "antd";
import { LoginOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { colorVars } from "@/styles/tokens";

export interface TreePublicNoticeProps {
  onLogin: () => void;
}

/**
 * Khách đang xem **bản công khai** của phả đồ: chỉ người đã khuất.
 *
 * <h2>Ranh giới — đây là chỗ dễ rò rỉ nhất của cả màn hình</h2>
 * Cây mà khách thấy thưa hơn cây thật, và điều đó phải **trông có chủ đích**.
 * Nhưng câu giải thích chỉ được phụ thuộc vào đúng một biến: *bản đang xem là
 * bản công khai*. Nó lấy từ `meta.guestFiltered` — một **hằng số** máy chủ luôn
 * đặt `true` cho bản công khai, không phải một phép đếm.
 *
 * Những thứ TUYỆT ĐỐI không được xuất hiện ở đây, cùng lý do với
 * `privacy-tier-notice.tsx` và `life-dates.ts`:
 *  - số người đang bị ẩn ("còn 812 người"), kể cả dạng ước lượng hay "hơn N";
 *  - hiệu số giữa `nodeCount` bản công khai và bất kỳ con số nào khác;
 *  - vị trí của một người bị ẩn ("cụ này còn con cháu bạn không thấy").
 *
 * Vì vậy câu chữ viết theo hướng khẳng định — *bản này gồm những ai* — chứ
 * không theo hướng phủ định *ai đang bị thiếu*. Hai phả đồ khác nhau của hai
 * dòng họ khác nhau phải đọc ra **cùng một câu**.
 *
 * Nền hổ phách, không phải dải đỏ: đây là hệ thống chạy đúng luật (BA v2 §10 ·
 * Nghị định 13/2023), không phải sự cố.
 */
export function TreePublicNotice({ onLogin }: TreePublicNoticeProps) {
  const t = useTranslations("tree.publicView");
  const tAuth = useTranslations("auth");

  return (
    <aside
      role="note"
      data-tree-notice="public-view"
      aria-label={t("label")}
      className="flex flex-wrap items-center gap-x-3 gap-y-1 border-b px-3 py-2 text-than leading-relaxed"
      style={{
        background: colorVars.warningBg,
        borderColor: colorVars.borderDark,
        color: colorVars.textMuted,
      }}
    >
      <LoginOutlined aria-hidden style={{ color: colorVars.accentText }} />
      <p className="m-0 flex-1 min-w-[16rem]">{t("body")}</p>
      <Button type="primary" size="small" onClick={onLogin}>
        {tAuth("login")}
      </Button>
    </aside>
  );
}
