"use client";

import { useTranslations } from "next-intl";
import { colorVars } from "@/styles/tokens";
import { PRIVACY_GROUPS, type PrivacyGroup, type PrivacySettings } from "@/types/api";

/** Bốn hạng người xem mà bảng này nói tới, theo thứ tự từ xa tới gần. */
const VIEWERS = ["GUEST", "CLAN_MEMBER", "SAME_BRANCH", "COUNCIL"] as const;
type Viewer = (typeof VIEWERS)[number];

export interface PrivacyAudiencePreviewProps {
  /** Bản đồng thuận của CHÍNH người đang đọc — không bao giờ của người khác. */
  settings: PrivacySettings;
  /** Trẻ vị thành niên: ẩn tối đa, mọi lựa chọn đều bị luật này thắng. */
  locked?: boolean;
}

/**
 * "Ai xem được gì về tôi".
 *
 * <h2>Bảng DUY NHẤT trong sản phẩm được phép nói cụ thể về dữ liệu một người</h2>
 * Mọi chỗ khác, nói về nội dung hồ sơ là rò rỉ ("có 3 trường bị giấu" là rò rỉ;
 * đếm đã là tiết lộ). Ở đây thì không, vì **người đọc chính là chủ thể** — họ
 * không học được gì về mình mà họ chưa biết.
 *
 * <h2>Vì sao suy ở trình duyệt ở ĐÂY là an toàn, trong khi ở chỗ khác thì không</h2>
 * Tài liệu thiết kế cảnh báo đúng: dựng bảng này bằng cách lấy hồ sơ đầy đủ rồi
 * che bớt ở trình duyệt sẽ biến chính tính năng giải thích quyền riêng tư thành
 * lỗ hổng. Cái bẫy ấy nằm ở chỗ **phải cầm dữ liệu đầy đủ để suy**.
 *
 * Bảng này không cầm dữ liệu của ai cả. Nó chỉ đọc năm công tắc trong
 * `person.privacy` — mà contract chỉ trả về cho **chính chủ và ADMIN** — rồi
 * phát biểu lại chính lựa chọn ấy bằng tiếng Việt. Không có hồ sơ người khác
 * nào đi vào phép tính, nên không có gì để rò. Nếu một ngày bảng này cần nói về
 * một người KHÁC, nó phải chuyển sang máy chủ ngay lập tức.
 *
 * <h2>Dòng "Khách chưa đăng nhập" luôn đứng đầu và luôn là "không thấy gì"</h2>
 * Đó là dòng trả lời nỗi lo đầu tiên của người sắp điền — "người ngoài có đọc
 * được không" — và câu trả lời của nó **không phụ thuộc vào lựa chọn nào của
 * họ**: `ShareScope` nói thẳng rằng khách không thấy người còn sống nào kể cả
 * khi chọn `CLAN`. Vì vậy nó không bao giờ bị ẩn đi, kể cả khi mọi mức đều ở
 * Riêng tư.
 */
export function PrivacyAudiencePreview({
  settings,
  locked = false,
}: PrivacyAudiencePreviewProps) {
  const t = useTranslations("privacy");

  const visibleFor = (viewer: Viewer): PrivacyGroup[] => {
    if (viewer === "GUEST" || locked) return [];
    // Hội đồng Tộc biểu / ADMIN đọc được cả nhóm `PRIVATE` — đó chính là định
    // nghĩa của mức ấy, và bảng phải nói đúng sự thật thay vì hứa với chủ thể
    // một mức kín mà hệ thống không giữ.
    if (viewer === "COUNCIL") return [...PRIVACY_GROUPS];
    return PRIVACY_GROUPS.filter((group) =>
      settings[group] === "CLAN" || (settings[group] === "BRANCH" && viewer === "SAME_BRANCH")
    );
  };

  return (
    <section aria-labelledby="privacy-preview-title" className="mt-2">
      <h3
        id="privacy-preview-title"
        className="m-0 font-serif text-de font-semibold text-text-main"
      >
        {t("preview.title")}
      </h3>
      <p className="m-0 mt-1 text-than leading-relaxed text-text-muted">{t("preview.lead")}</p>

      <ul className="m-0 mt-3 list-none space-y-2 p-0">
        {VIEWERS.map((viewer) => {
          const groups = visibleFor(viewer);
          const summary =
            viewer === "GUEST"
              ? t("preview.nothingAtAll")
              : groups.length === 0
                ? t("preview.baselineOnly")
                : t("preview.baselinePlus", {
                    groups: groups.map((g) => t(`groupShort.${g}`)).join(" · "),
                  });

          return (
            <li
              key={viewer}
              className="rounded-lg border px-3 py-2"
              style={{
                // Dòng "không thấy gì" dùng nền xanh-thành-công chứ không phải
                // nền cảnh báo: với chủ thể, việc khách KHÔNG thấy gì là tin
                // tốt, không phải một cảnh báo cần chú ý.
                background: viewer === "GUEST" ? colorVars.successBg : colorVars.bgCard,
                borderColor: colorVars.border,
              }}
            >
              <p className="m-0 text-than font-semibold text-text-main">{t(`viewer.${viewer}`)}</p>
              <p className="m-0 mt-0.5 text-than leading-relaxed text-text-muted">{summary}</p>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
