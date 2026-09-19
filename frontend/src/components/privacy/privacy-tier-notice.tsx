"use client";

import { InfoCircleOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { colorVars } from "@/styles/tokens";
import type { PersonAccessMeta } from "@/types/api";

export interface PrivacyTierNoticeProps {
  /** Người đã khuất là công khai — không có gì để giải thích. */
  isAlive: boolean;
  /** `meta` do máy chủ trả về; chỉ đọc `visibleTier`. */
  meta: Pick<PersonAccessMeta, "visibleTier">;
  className?: string;
}

/**
 * Câu giải thích ngắn cho `meta.visibleTier` (BA v2 §10 · Nghị định 13/2023).
 *
 * ## Vì sao cần
 *
 * Mọi trường bị lọc đều biến mất hoàn toàn khỏi hồ sơ (xem `optional-field.tsx`)
 * — đúng về mặt bảo mật, nhưng để lại một hệ quả xấu: người dùng không phân
 * biệt được "dòng họ chưa ai ghi" với "bạn không được xem", và kết luận rằng
 * gia phả sơ sài. `visibleTier` là thứ máy chủ gửi kèm mọi phản hồi hồ sơ
 * chính để trả lời câu đó, và trước đây không nơi nào trong `src/` đọc nó.
 *
 * ## Ranh giới an toàn — đây là chỗ dễ hỏng nhất
 *
 * Câu chữ ở đây **chỉ được phụ thuộc vào hai biến**: người này còn sống hay
 * đã khuất, và người gọi đang ở tầng nào. Nó **không bao giờ** được đọc dữ
 * liệu của chính hồ sơ. Nhờ vậy mọi hồ sơ người còn sống cùng tầng đều hiện
 * đúng một câu như nhau, và câu đó không nói được điều gì riêng về ai cả.
 *
 * Cụ thể, những thứ TUYỆT ĐỐI không được xuất hiện:
 *  - số lượng ("3 số điện thoại"), vì đếm đã là tiết lộ;
 *  - tên trường cụ thể đang trống trên hồ sơ này;
 *  - bất kỳ dấu hiệu nào cho biết hồ sơ NÀY có dữ liệu ở tầng cao hơn.
 *
 * Câu chữ vì thế viết theo hướng khẳng định — *ai được xem gì* — chứ không
 * theo hướng phủ định *cái gì đang bị giấu*.
 *
 * ## Khi nào im lặng
 *
 * - Người đã khuất: công khai theo mặc định, một lời cảnh báo ở đây chỉ là
 *   nhiễu và làm loãng cảnh báo ở chỗ thật sự cần.
 * - Tầng `PUBLIC` hoặc `T3`: người gọi đã thấy trọn hồ sơ, không còn gì để nói.
 */
export function PrivacyTierNotice({ isAlive, meta, className }: PrivacyTierNoticeProps) {
  const t = useTranslations("privacy");

  if (!isAlive) return null;
  if (meta.visibleTier === "PUBLIC" || meta.visibleTier === "T3") return null;

  const message = meta.visibleTier === "T2" ? t("livingT2") : t("livingT1");

  return (
    <aside
      role="note"
      data-privacy-notice="tier"
      aria-label={t("noticeLabel")}
      className={`flex items-start gap-2 rounded-lg border px-3 py-2 text-than leading-relaxed ${className ?? ""}`}
      style={{
        background: colorVars.warningBg,
        borderColor: colorVars.borderDark,
        color: colorVars.textMuted,
      }}
    >
      <InfoCircleOutlined
        aria-hidden
        className="mt-0.5 shrink-0"
        style={{ color: colorVars.accentText }}
      />
      <p className="m-0">{message}</p>
    </aside>
  );
}
