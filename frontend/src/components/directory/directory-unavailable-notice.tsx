"use client";

import { InfoCircleOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";

/**
 * Máy chủ **không phục vụ** danh bạ (`404`/`501`).
 *
 * <h2>Đây là một trạng thái khác hẳn "tải lỗi", và trộn chúng lại là nói dối</h2>
 * `GET /api/v1/directory` là một hợp đồng do giao diện đề xuất khi dựng màn này
 * trên bộ giả lập; phía máy chủ chưa có controller nào phục vụ đường dẫn ấy, nên
 * **mọi vai** — thành viên, trưởng chi, quản trị — đều nhận `404`. Trước bản vá
 * này màn hình dịch `404` ấy thành "Chưa tải được danh bạ. Vui lòng thử lại.",
 * tức là vừa đổ lỗi cho đường truyền, vừa mời người dùng làm một việc **không
 * bao giờ** đổi kết quả.
 *
 * Ba câu dẫn tới ba hành động khác nhau, và chỉ một câu là đúng:
 *  - "tải lỗi, thử lại" → người dùng bấm lại, mãi mãi;
 *  - "bạn không có quyền" → người dùng đi xin quyền, nhầm người;
 *  - "máy chủ chưa mở phần này" → người dùng đi hỏi Hội đồng / quản trị, và
 *    trong lúc chờ thì dùng những phần đang có.
 *
 * Nền hổ phách chứ không phải dải đỏ: bản cài đặt thiếu một tính năng không
 * phải một sự cố đang diễn ra. Không đếm, không đoán, không dựng một danh sách
 * rỗng trông như dòng họ không còn ai.
 */
export function DirectoryUnavailableNotice() {
  const t = useTranslations("directory");

  return (
    <section
      role="note"
      data-directory-state="unavailable"
      aria-labelledby="directory-unavailable-title"
      className="rounded-lg border px-4 py-6 sm:px-6"
      style={{ background: colorVars.warningBg, borderColor: colorVars.borderDark }}
    >
      <h2
        id="directory-unavailable-title"
        className="m-0 flex items-center gap-2 font-serif text-de font-semibold text-text-main"
      >
        <InfoCircleOutlined aria-hidden style={{ color: colorVars.accentText }} />
        {t("unavailable.title")}
      </h2>
      <p className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-muted">
        {t("unavailable.body")}
      </p>
      <p className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-muted">
        {t("unavailable.whoToAsk")}
      </p>
      <p className="m-0 mt-4">
        <Link href="/tree" className="text-than underline">
          {t("unavailable.viewTree")}
        </Link>
      </p>
    </section>
  );
}
