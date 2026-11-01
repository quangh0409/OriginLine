"use client";

import { Badge } from "antd";
import { ToolOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { useMe } from "@/hooks/use-me";
import { useClanProfile } from "@/hooks/use-clan-profile";

/**
 * Lối vào **`/quan-tri`** (Khởi tạo dòng họ) trên thanh đầu trang máy tính —
 * quyết định 26/09/2026 của chủ dự án: không có màn hình nào để dựng một dòng
 * họ mới, mọi thứ làm tay dưới CSDL. Đây là lối vào cho bước 1.
 *
 * <h2>CHỈ `ADMIN`, không phải mọi vai quản trị</h2>
 * Khác `ManagementQueueLink` (Trưởng chi/Hội đồng/Quản trị): khởi tạo dòng họ
 * là việc kỹ thuật một lần, không phải việc điều hành thường xuyên của Hội
 * đồng Tộc biểu hay Trưởng chi — CLAUDE.md tách rõ "danh xưng dòng họ (Tộc
 * trưởng, Trưởng chi) khác với vai quản trị kỹ thuật". Ẩn với mọi vai khác,
 * kể cả Hội đồng, để không mời một Trưởng chi bấm vào một màn chắc chắn `403`.
 *
 * <h2>Chấm đỏ khi CHƯA khởi tạo</h2>
 * `useClanProfile` là bề mặt CÔNG KHAI (Khách gọi được), nên gọi nó ở đây
 * không mở thêm lỗ quyền nào — chỉ đọc lại đúng dữ liệu ai cũng thấy. Chấm này
 * là tín hiệu duy nhất trên toàn giao diện nhắc Quản trị "còn việc chưa xong",
 * cùng vai trò với banner ở trang chủ (`HomeHero`) nhưng ở nơi luôn hiện diện.
 */
export function SystemAdminLink() {
  const t = useTranslations("nav");
  const { data: me } = useMe();
  const clanProfile = useClanProfile();

  const isAdmin = Boolean(me?.appUserId) && me?.role === "ADMIN";
  if (!isAdmin) return null;

  const chuaKhoiTao = clanProfile.data?.initialized === false;

  return (
    <Link
      href="/quan-tri"
      title={t("systemAdmin")}
      aria-label={t("systemAdmin")}
      className="flex min-h-11 items-center gap-1.5 rounded px-2 text-text-main no-underline hover:bg-primary-light hover:text-primary"
      data-testid="system-admin-link"
    >
      <Badge dot={chuaKhoiTao} offset={[-2, 2]}>
        <ToolOutlined aria-hidden className="text-lg text-text-main" />
      </Badge>
      <span className="hidden lg:inline">{t("systemAdmin")}</span>
    </Link>
  );
}
