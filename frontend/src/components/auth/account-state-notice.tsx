"use client";

import { useTranslations } from "next-intl";
import { InfoCircleOutlined } from "@ant-design/icons";
import { colorVars } from "@/styles/tokens";
import { GuestModeNotice } from "./guest-mode-notice";
import type { AccountProblem } from "./use-account-problem";

export interface AccountStateNoticeProps {
  readonly problem: AccountProblem;
  /**
   * Mã lỗi máy đọc, chỉ để dán vào một cuộc gọi hỗ trợ. **Không hiện ra ngay**
   * — nó nằm trong một khối gập, mặc định đóng.
   */
  readonly technicalCode?: string | null;
}

const KHOA: Readonly<Record<AccountProblem, string>> = {
  NOT_PROVISIONED: "notProvisioned",
  NOT_ACTIVE: "notActive",
};

/**
 * Màn "tài khoản chưa dùng được" — trạng thái 3 và 4 của thiết kế 06 §7.
 *
 * <h2>Cái nó thay thế</h2>
 * Hôm nay người đăng nhập bằng một tài khoản chưa nối nhân khẩu nhận một
 * {@code 404} ở lời gọi API tiếp theo, và giao diện vẽ <i>"không tìm thấy
 * trang"</i>. Ba điều sai cùng lúc: nó nói sai chuyện gì đã xảy ra, nó đổ lỗi
 * cho trang (nên người dùng tải lại, mãi mãi), và nó không nói việc cần làm.
 *
 * <h2>Sáu luật viết câu lỗi của 06 §7.2, và chỗ nào trong tệp này giữ luật nào</h2>
 * <ol>
 *   <li><b>Không in mã lỗi</b> — {@code technicalCode} nằm trong {@code
 *       <details>} mặc định đóng, đúng lối thoát mà chính §7.2 cho phép
 *       ("giấu sau một liên kết Chi tiết kỹ thuật"). Chữ "provisioned" không
 *       xuất hiện ở bất kỳ câu nào người dùng đọc.</li>
 *   <li><b>Nói việc phải làm</b> — khối "Việc cần làm" đứng ngay dưới lời giải
 *       thích, không phải ở cuối trang.</li>
 *   <li><b>Không đổ lỗi</b> — câu chữ nói về trạng thái của <i>hệ thống</i>,
 *       không về thao tác của người dùng. Họ không làm gì sai và không sửa được
 *       nó.</li>
 *   <li><b>Không màu đỏ cho luật chạy đúng</b> — nền hổ phách. Một tài khoản
 *       chờ Hội đồng nối là quy trình đang chạy, không phải sự cố.</li>
 *   <li><b>Ít nhất một lối đi tiếp</b> — khối chế độ khách ở dưới, và nó là lối
 *       đi thật: phần các cụ đã khuất mở sẵn cho người này ngay bây giờ.</li>
 *   <li><b>Không để lộ dữ liệu bị ẩn</b> — không câu nào nói có bao nhiêu người
 *       trong phả, hay tên ai.</li>
 * </ol>
 *
 * <h2>Chỗ phải chấp nhận thua so với bản thiết kế</h2>
 * Hình 8 vẽ đích danh "Ông Nguyễn Văn Bốn · Trưởng chi Giáp · 0903 111 222".
 * Chính §7 ghi rằng dữ kiện "trưởng chi phụ trách của tôi là ai" <b>chưa có
 * trong hợp đồng API Giai đoạn 1</b>, và không có nó thì màn này tụt về lời
 * khuyên chung. Đây đúng là chỗ ấy: câu chữ nói "gọi cho trưởng chi của mình"
 * thay vì gọi tên. Bịa một cái tên hoặc gọi vống lên "liên hệ Hội đồng Tộc
 * biểu" đều tệ hơn — cái thứ nhất sai, cái thứ hai không ai biết gọi vào đâu.
 */
export function AccountStateNotice({ problem, technicalCode }: AccountStateNoticeProps) {
  const t = useTranslations("auth.accountState");
  const nhanh = KHOA[problem];

  return (
    <div className="space-y-4">
      <section
        data-account-state={problem}
        role="status"
        aria-labelledby="account-state-title"
        className="rounded-lg border px-4 py-5 sm:px-6"
        style={{ background: colorVars.warningBg, borderColor: colorVars.borderDark }}
      >
        <h1
          id="account-state-title"
          className="m-0 flex items-center gap-2 font-serif text-de font-bold text-text-main"
        >
          <InfoCircleOutlined aria-hidden style={{ color: colorVars.accentText }} />
          {t(`${nhanh}.title`)}
        </h1>
        <p className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main">
          {t(`${nhanh}.body`)}
        </p>

        <h2 className="m-0 mt-4 text-than font-semibold uppercase" style={{ color: colorVars.textMuted }}>
          {t(`${nhanh}.todoTitle`)}
        </h2>
        <p className="m-0 mt-2 max-w-prose text-dan leading-relaxed text-text-main">
          {t(`${nhanh}.todo`)}
        </p>

        {technicalCode && (
          // `<details>` gốc chứ không phải Collapse của AntD: nó mở được khi
          // JavaScript hỏng, đọc đúng bằng trình đọc màn hình, và không kéo
          // thêm một widget vào một màn hình mà người dùng đang bối rối.
          <details className="mt-4" data-account-state-technical="">
            <summary
              className="cursor-pointer text-than text-text-muted"
              // Vùng chạm 44px cho một phần tử không phải nút — sàn 00 §2.2
              // không có ngoại lệ, kể cả cho thứ hiếm ai bấm.
              style={{ minHeight: 44, display: "flex", alignItems: "center" }}
            >
              {t("technicalTitle")}
            </summary>
            <p className="m-0 mt-2 text-than leading-relaxed text-text-muted">
              {t("technicalHint")}
            </p>
            <p className="m-0 mt-1 text-than text-text-muted">
              <code>{t("technicalCode", { code: technicalCode })}</code>
            </p>
          </details>
        )}
      </section>

      <GuestModeNotice />
    </div>
  );
}
