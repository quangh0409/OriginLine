"use client";

import { Button } from "antd";
import { EyeOutlined, LoginOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { useAuth } from "@/lib/auth/auth-context";
import { colorVars } from "@/styles/tokens";

/**
 * **Lối vào chế độ khách** — thiết kế 06 §8, hình 9.
 *
 * <h2>Việc của khối này: trả lời "xem được gì", không phải "xin mời đăng nhập"</h2>
 * 00 §2.7 đòi quyền riêng tư phải nhìn thấy được <i>bằng giao diện</i>, không
 * phải bằng trang chính sách. Một nút "Đăng nhập" trơ trọi không trả lời được
 * câu hỏi duy nhất người mới tới đang có — <b>ở đây có gì cho tôi?</b> — nên
 * nó đẩy người ta đi thay vì mời vào.
 *
 * Hai cột <b>cân nhau</b>, và đó là một quyết định về nghĩa chứ không phải về
 * bố cục: cột trái không phải lời an ủi cho cột phải, và cột phải không phải
 * lời xin lỗi cho cột trái. Người đã khuất công khai là <i>một nửa lý do sản
 * phẩm tồn tại</i> (BA v2 §10), không phải phần thừa còn lại sau khi trừ đi
 * phần bị khoá.
 *
 * <h2>Ba điều tuyệt đối không được có ở đây</h2>
 * <ol>
 *   <li><b>Không một con số nào</b> về phần bị ẩn. "Có 320 người đang sống bị
 *       ẩn" tự nó đã là phép đếm dân số dòng họ — thiết kế 06 §8 gọi đích danh,
 *       và {@code tests/component/privacy-tier-notice.test.tsx} ghim cùng bất
 *       biến ấy cho nhánh {@code privacy.*}. Khối này nằm ở nhánh {@code auth.*}
 *       nên không bị phép quét kia với tới; vì vậy nó có bài kiểm riêng ở
 *       {@code tests/component/guest-mode-notice.test.tsx}, cùng một luật.</li>
 *   <li><b>Không ổ khoá</b>, không dải đỏ. Đây là hệ thống chạy <i>đúng luật</i>,
 *       không phải sự cố — nền hổ phách, cùng lựa chọn với
 *       {@code directory-guest-notice.tsx} và khối "Xin đăng nhập để xem phả đồ".</li>
 *   <li><b>Không hứa thứ chưa chạy.</b> Ba dòng cột trái đều đã có đường đi
 *       thật: {@code /api/v1/public/tree} và {@code GET /persons/{id}} của một
 *       người đã khuất. Cố ý <b>bỏ</b> "tìm kiếm tên các cụ" khỏi danh sách: màn
 *       tìm kiếm hiện gọi bản thành viên, nên hứa ở đây là in sẵn một lời nói
 *       dối lên màn hình đầu tiên (thiết kế 06 §8 nêu đúng rủi ro này).</li>
 * </ol>
 */
export function GuestModeNotice() {
  const t = useTranslations("auth.guestMode");
  const tAuth = useTranslations("auth");
  const { isAuthenticated, login } = useAuth();

  return (
    <section
      data-guest-mode="notice"
      aria-labelledby="guest-mode-title"
      className="rounded-lg border px-4 py-5 sm:px-6"
      style={{ background: colorVars.warningBg, borderColor: colorVars.borderDark }}
    >
      <h2
        id="guest-mode-title"
        className="m-0 flex items-center gap-2 font-serif text-de font-semibold text-text-main"
      >
        {/* Biểu tượng chỉ để tăng tốc nhận diện; chữ mới mang nghĩa — 00 §2.3. */}
        <EyeOutlined aria-hidden style={{ color: colorVars.accentText }} />
        {t("title")}
      </h2>

      {/* `grid-cols-1 sm:grid-cols-2`: tiện ích có số của Tailwind sinh
          `minmax(0,1fr)` nên hai cột co được; `grid-cols-[1fr_1fr]` thì không
          (xem tests/unit/a11y/layout-containment.test.ts, luật L3). */}
      <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="min-w-0">
          <h3 className="m-0 text-than font-semibold text-text-main">{t("openTitle")}</h3>
          <ul className="m-0 mt-2 list-disc space-y-1 pl-5 text-than leading-relaxed text-text-main">
            <li>{t("open1")}</li>
            <li>{t("open2")}</li>
            <li>{t("open3")}</li>
          </ul>
        </div>

        <div className="min-w-0">
          <h3 className="m-0 text-than font-semibold text-text-main">{t("closedTitle")}</h3>
          <ul className="m-0 mt-2 list-disc space-y-1 pl-5 text-than leading-relaxed text-text-main">
            <li>{t("closed1")}</li>
            <li>{t("closed2")}</li>
            <li>{t("closed3")}</li>
          </ul>
        </div>
      </div>

      <p className="m-0 mt-4 max-w-prose text-than leading-relaxed text-text-muted">
        {t("notYourSetting")}
      </p>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Link
          href="/tree"
          className="inline-flex min-h-[44px] items-center gap-2 rounded-lg border px-4 text-than font-semibold no-underline"
          style={{
            borderColor: colorVars.primary,
            color: colorVars.primary,
            background: colorVars.bgCard,
          }}
        >
          <EyeOutlined aria-hidden />
          {t("viewTree")}
        </Link>

        {/* Người đã đăng nhập vẫn có thể gặp khối này (ví dụ ở màn "chưa nối
            với ai trong phả"), và với họ một nút "Đăng nhập" nữa là một ngõ
            cụt có màu. */}
        {!isAuthenticated && (
          <Button icon={<LoginOutlined />} onClick={login}>
            {tAuth("login")}
          </Button>
        )}
      </div>
    </section>
  );
}
