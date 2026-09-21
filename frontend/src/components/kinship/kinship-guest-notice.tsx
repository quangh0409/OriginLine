"use client";

import { Button } from "antd";
import { LoginOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { useAuth } from "@/lib/auth/auth-context";
import { colorVars } from "@/styles/tokens";

/**
 * Khách chưa đăng nhập mở `/kinship`.
 *
 * <h2>Vì sao khối này tồn tại — lỗi thật, không phải phòng xa</h2>
 * `header.tsx` từng hiện mục "Tra danh xưng" cho **mọi người, kể cả khách**,
 * nhưng `<PersonPicker>` — ô chọn người của màn này — gọi thẳng
 * `usePersonSearch("member", …)`, một audience CỐ ĐỊNH trỏ tới
 * `/api/v1/persons/search`, endpoint đòi phiên đăng nhập. Với khách, mọi lượt
 * gõ chữ vào ô chọn đều nhận `401` — và {@code notFoundContent} của
 * `<PersonPicker>` không phân biệt "không có ai khớp" với "không hỏi được ai
 * cả", nên khách chỉ thấy một ô chọn <b>lúc nào cũng báo "không tìm thấy"</b>,
 * gõ gì cũng vậy. Còn tệ hơn một lỗi rõ ràng: nó trông như tính năng chạy
 * đúng và dòng họ này không ai tên như thế, chứ không trông như một cánh cửa
 * đóng.
 *
 * <h2>Vì sao KHÔNG nối sang lối tìm công khai</h2>
 * Khác ba lần trước cùng mẫu hình này (danh bạ, tìm kiếm, nhảy tới người trên
 * phả đồ) — lần này tầng công khai <b>không có gì để nối vào</b>.
 * `SecurityConfig` chỉ mở `GET /api/v1/public/**`; phép giải danh xưng
 * (`GET /api/v1/kinship`) không nằm dưới tiền tố ấy và rơi vào
 * `anyRequest().authenticated()`. Dựng lại `<PersonPicker>` để tìm được người
 * đã khuất qua lối công khai vẫn không cứu được màn này: bước tiếp theo
 * (`useKinship` → `/api/v1/kinship`) sẽ `401` ngay sau đó — tệ hơn nữa, vì lúc
 * ấy khách đã chọn xong hai người rồi mới bị chặn. Nối một nửa đường ống thì
 * tệ hơn nói thẳng ngay từ đầu.
 *
 * <h2>Cùng khuôn với `<DirectoryGuestNotice>`</h2>
 * Không ổ khoá, nền hổ phách chứ không dải đỏ (luật chạy đúng, không phải sự
 * cố), và luôn có một việc làm được ngay: xem phả đồ của người đã khuất mà
 * không cần tài khoản.
 */
export function KinshipGuestNotice() {
  const t = useTranslations("kinship");
  const tAuth = useTranslations("auth");
  const { login } = useAuth();

  return (
    <section
      role="note"
      data-kinship-state="guest"
      aria-labelledby="kinship-guest-title"
      className="rounded-lg border px-4 py-6 sm:px-6"
      style={{ background: colorVars.warningBg, borderColor: colorVars.borderDark }}
    >
      <h2
        id="kinship-guest-title"
        className="m-0 flex items-center gap-2 font-serif text-de font-semibold text-text-main"
      >
        <LoginOutlined aria-hidden style={{ color: colorVars.accentText }} />
        {t("guest.title")}
      </h2>
      <p className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-muted">
        {t("guest.body")}
      </p>
      <div className="mt-4 flex flex-wrap items-center gap-2">
        <Button type="primary" icon={<LoginOutlined />} onClick={login}>
          {tAuth("login")}
        </Button>
        <Link href="/tree" className="text-than underline">
          {t("guest.viewTree")}
        </Link>
      </div>
    </section>
  );
}
