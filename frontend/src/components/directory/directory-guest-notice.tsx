"use client";

import { Button } from "antd";
import { LoginOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { useAuth } from "@/lib/auth/auth-context";
import { colorVars } from "@/styles/tokens";

/**
 * Khách chưa đăng nhập mở danh bạ.
 *
 * <h2>Không ổ khoá, và cố ý không dùng màu lỗi</h2>
 * Ổ khoá bị cấm trên toàn sản phẩm (xem `optional-field.tsx`) vì nó dạy người
 * đọc rằng "ở đây có dữ liệu bạn không được xem" — và một khi đã học được dấu
 * hiệu ấy ở màn hình này, họ sẽ đọc mọi khoảng trắng trên hồ sơ theo cách đó.
 * Biểu tượng ở đây vì vậy là "đăng nhập", một lối đi, không phải một cánh cửa
 * đóng. Nền hổ phách chứ không phải dải đỏ: đây là hệ thống chạy đúng luật,
 * không phải sự cố — cùng lựa chọn với khối "Xin đăng nhập để xem phả đồ".
 *
 * <h2>Ba việc câu chữ ở đây phải làm, và nó từng làm sai cả ba</h2>
 * Thông báo cũ là "Chưa tải được danh bạ. Vui lòng thử lại." — sai cả về nguyên
 * nhân lẫn về việc cần làm tiếp, và "thử lại" thì thử bao nhiêu lần cũng vậy.
 * Khối này vì thế phải:
 *  <ol>
 *    <li>nói <b>danh bạ là gì</b> (không ai đăng nhập vì một thứ họ không biết
 *        là thứ gì);</li>
 *    <li>nói <b>vì sao</b> cần đăng nhập — luật, không phải sự cố;</li>
 *    <li><b>dẫn tới chỗ đăng nhập</b>, và nói luôn thứ họ xem được ngay bây
 *        giờ mà không cần tài khoản: phả đồ các cụ đã khuất.</li>
 *  </ol>
 *
 * Ranh giới phải giữ chặt: không con số nào ở đây (không "627 người"), không
 * tên ai, không một mảnh dữ liệu nào của người còn sống. Máy chủ trả lời trước
 * khi đếm bất cứ thứ gì, nên trình duyệt cũng không có gì để lỡ tay in ra.
 */
export function DirectoryGuestNotice() {
  const t = useTranslations("directory");
  const tAuth = useTranslations("auth");
  const { login } = useAuth();

  return (
    <section
      role="note"
      data-directory-state="guest"
      aria-labelledby="directory-guest-title"
      className="rounded-lg border px-4 py-6 sm:px-6"
      style={{ background: colorVars.warningBg, borderColor: colorVars.borderDark }}
    >
      <h2
        id="directory-guest-title"
        className="m-0 flex items-center gap-2 font-serif text-de font-semibold text-text-main"
      >
        <LoginOutlined aria-hidden style={{ color: colorVars.accentText }} />
        {t("guest.title")}
      </h2>
      <p className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-muted">
        {t("guest.body")}
      </p>
      <p className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-muted">
        {t("guest.publicHint")}
      </p>
      <div className="mt-4 flex flex-wrap items-center gap-2">
        <Button type="primary" icon={<LoginOutlined />} onClick={login}>
          {tAuth("login")}
        </Button>
        {/* Lối đi thứ hai, quan trọng không kém: người không có tài khoản dòng
            họ vẫn phải rời màn hình này với một việc làm được, chứ không phải
            với một cánh cửa đóng. */}
        <Link href="/tree" className="text-than underline">
          {t("guest.viewTree")}
        </Link>
      </div>
    </section>
  );
}
