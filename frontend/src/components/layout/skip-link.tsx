"use client";

import { useTranslations } from "next-intl";

/** Id của mốc nội dung chính. Dùng chung với <AppShell> — đừng viết chuỗi rời. */
export const MAIN_CONTENT_ID = "noi-dung-chinh";

/**
 * "Bỏ qua, tới nội dung chính" — C-4.5.
 *
 * <h2>Vì sao nó phải là điểm dừng Tab ĐẦU TIÊN</h2>
 * Người dùng bàn phím và người dùng trình đọc màn hình phải đi qua toàn bộ thanh
 * đầu trang — bốn liên kết điều hướng, chuông thông báo, ô chuyển ngôn ngữ, cụm
 * tài khoản — trước khi chạm được vào nội dung, và phải làm lại ở MỌI trang. Một
 * liên kết bỏ qua nằm ở vị trí thứ hai trở đi thì không cứu được ai; nó phải là
 * thứ đầu tiên Tab chạm tới, nên component này đứng ngay trên <Header>.
 *
 * <h2>Hai cái bẫy đã biết, và cách né</h2>
 * <ol>
 *   <li><b>Đích phải nhận được tiêu điểm.</b> Một liên kết trỏ tới phần tử không
 *       có {@code tabIndex={-1}} trông y như đang hoạt động — trình duyệt cuộn
 *       tới đó — nhưng tiêu điểm bàn phím vẫn ở đầu trang, nên phím Tab tiếp theo
 *       lại rơi vào liên kết điều hướng thứ hai. Hỏng một cách vô hình. Vì vậy
 *       {@code <AppShell>} đặt {@code tabIndex={-1}} lên {@code <main>}.</li>
 *   <li><b>Không dùng {@code sr-only}.</b> Tiện ích ấy của Tailwind co phần tử
 *       xuống {@code 1px × 1px} — nghĩa là liên kết bỏ qua trở thành một điều
 *       khiển 1×1px, tức chính nó vi phạm sàn vùng chạm 44px mà nó sinh ra để
 *       phục vụ. Đo được: bộ thu thập C-3.1 nhặt nó lên và báo {@code 1×1px}.
 *
 *       Cách dùng ở đây là mẫu cổ điển và đúng hơn: phần tử LUÔN mang đủ kích
 *       thước thật, chỉ nằm NGOÀI khung nhìn ({@code -top-24}) khi chưa được
 *       tiêu điểm, rồi trượt vào chỗ ({@code focus:top-3}). Vùng chạm luôn là
 *       44px, trình đọc màn hình vẫn đọc được, và người dùng bàn phím có mắt
 *       thấy nó xuất hiện ở phím Tab đầu tiên.</li>
 * </ol>
 *
 * Dùng {@code <a href>} thuần chứ không phải {@code <Link>} của next-intl: đích
 * là một mảnh neo trong CHÍNH trang đang mở, không phải một điều hướng. Cho
 * {@code <Link>} xử lý sẽ thành một lượt định tuyến phía client, làm mất luôn
 * hành vi cuộn-tới-neo của trình duyệt.
 */
export function SkipLink() {
  const t = useTranslations("a11y");

  return (
    <a
      href={`#${MAIN_CONTENT_ID}`}
      data-testid="skip-to-content"
      className="absolute -top-24 left-3 z-50 flex min-h-11 items-center rounded border border-border-dark bg-bg-card px-4 text-than font-medium text-primary no-underline focus:top-3"
      onClick={() => {
        // Đặt tiêu điểm bằng tay thay vì trông chờ vào hành vi mặc định của mảnh
        // neo: Safari và Firefox cuộn tới đích nhưng KHÔNG chuyển tiêu điểm bàn
        // phím sang đó, kể cả khi đích có tabindex="-1".
        document.getElementById(MAIN_CONTENT_ID)?.focus();
      }}
    >
      {t("skipToContent")}
    </a>
  );
}
