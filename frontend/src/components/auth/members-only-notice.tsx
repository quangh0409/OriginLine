"use client";

import { Button } from "antd";
import { LoginOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { useAuth } from "@/lib/auth/auth-context";
import { colorVars } from "@/styles/tokens";

/**
 * Lời báo ở chỗ một phần cần đăng nhập, cho người CHƯA đăng nhập.
 *
 * Tách khỏi `home/home-sections.tsx` (26/09/2026) để `/bai-viet` nói đúng cùng
 * một câu với khối "Bài mới" của trang chủ: hai chỗ cùng một luật mà hai giọng
 * khác nhau là hai chỗ để người đọc nghĩ rằng luật khác nhau.
 *
 * Người gọi phải quyết định khách hay thành viên bằng `useSignedIn()` TRƯỚC khi
 * dựng thành phần có truy vấn — khối này không tự chặn lượt gọi nào, nó chỉ là
 * thứ hiện ra ở chỗ lượt gọi đã không được phát. Cách cũ ("cứ gọi rồi bắt
 * 401") để lại một dòng đỏ trong console và một câu "chưa tải được" sai nguyên
 * nhân — đo thật trên `/bai-viet` ngày 26/09.
 *
 * Nền hổ phách chứ không phải màu lỗi: đây là hệ thống chạy đúng luật, không
 * phải sự cố — cùng lựa chọn với `directory/directory-guest-notice.tsx`.
 */
export function MembersOnlyNotice({ testId = "members-only-notice" }: { testId?: string }) {
  const t = useTranslations("home");
  const { login } = useAuth();
  return (
    <div
      className="rounded-lg border px-4 py-3"
      style={{
        background: colorVars.warningBg,
        borderColor: colorVars.borderDark,
      }}
      data-testid={testId}
    >
      <p className="m-0 max-w-prose text-than leading-relaxed text-text-main">
        {t("membersOnly.body")}
      </p>
      <Button
        icon={<LoginOutlined aria-hidden />}
        onClick={() => login()}
        className="mt-2 min-h-[44px] text-than font-semibold"
      >
        {t("membersOnly.login")}
      </Button>
    </div>
  );
}
