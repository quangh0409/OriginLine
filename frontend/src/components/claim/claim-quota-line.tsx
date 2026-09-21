"use client";

import { useTranslations } from "next-intl";
import { ExclamationCircleOutlined } from "@ant-design/icons";
import type { ClaimQuotaDto } from "@/lib/api/claim";
import { colorVars } from "@/styles/tokens";

/**
 * "Ông/bà còn {n} lần gửi đơn."
 *
 * <h2>Vì sao nó phải hiện ra TRƯỚC khi người dùng gõ</h2>
 * Checklist §1.4 đòi: đơn bị từ chối thì gửi lại được, <b>có giới hạn số lần,
 * và phải nói rõ còn mấy lần</b>. Biết sau khi đã viết xong một bài tự giới
 * thiệu là biết quá muộn — và người bị từ chối một lần chính là người dễ bỏ
 * cuộc nhất.
 *
 * <h2>`quota === null` nghĩa là KHÔNG BIẾT, không phải "còn 0 lần"</h2>
 * Ngưỡng sống trong cấu hình máy chủ
 * ({@code giapha.membership.claim.max-rejected}) và hôm nay <b>không lối đọc
 * nào trả nó ra</b> — xem {@code ClaimQuotaDto}. Khi chưa có, thành phần này
 * <b>không vẽ gì</b>: đoán một con số là dựng một bản sao thứ hai của một chính
 * sách, và bản sao thứ hai là bản sẽ lệch. Im lặng thì người dùng thiếu một câu
 * hữu ích; bịa thì người dùng nhận một câu <em>sai</em>, và sẽ tin nó.
 *
 * <h2>Vì sao KHÔNG hiện với người chưa bị từ chối lần nào</h2>
 * Người mở màn này lần đầu chưa làm gì sai. Một dòng "ông/bà còn 3 lần" chào họ
 * ngay ở trên đầu đọc như một lời cảnh cáo, và tài liệu 00 §2.4 nói rõ nỗi sợ
 * làm hỏng là thứ ngăn người ta đóng góp dữ liệu. Bộ đếm chỉ có nghĩa khi nó đã
 * bắt đầu đếm — hoặc khi nó sắp hết.
 *
 * Hết lượt thì cũng không vẽ gì: lúc ấy cả màn đã là {@code ClaimBlockNotice}
 * với mã {@code CLAIM_LIMIT_REACHED}, và hai lời nhắc chồng lên nhau chỉ làm
 * loãng.
 */
export interface ClaimQuotaLineProps {
  readonly quota: ClaimQuotaDto | null;
  /** Kèm một câu giải thích vì sao có giới hạn. Bật ở màn biểu mẫu. */
  readonly showWhy?: boolean;
}

export function ClaimQuotaLine({ quota, showWhy }: ClaimQuotaLineProps) {
  const t = useTranslations("claim");

  if (quota === null) return null;
  if (quota.remaining <= 0) return null;
  if (quota.rejected === 0 && quota.remaining > 1) return null;

  const laLanCuoi = quota.remaining === 1;

  return (
    <div
      data-claim-quota={quota.remaining}
      className="flex items-start gap-2 rounded-lg border px-4 py-3"
      style={{ background: colorVars.warningBg, borderColor: colorVars.borderDark }}
    >
      <span aria-hidden className="mt-0.5 shrink-0" style={{ color: colorVars.accentText }}>
        <ExclamationCircleOutlined />
      </span>
      <div className="space-y-1">
        <p className="m-0 max-w-prose text-than font-semibold text-text-main">
          {laLanCuoi ? t("quota.lastOne") : t("quota.remaining", { n: quota.remaining })}
        </p>
        {showWhy && (
          <p className="m-0 max-w-prose text-than leading-snug text-text-muted">
            {t("quota.why")}
          </p>
        )}
      </div>
    </div>
  );
}
