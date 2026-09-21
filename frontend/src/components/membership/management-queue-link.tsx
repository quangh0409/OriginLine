"use client";

import { Badge } from "antd";
import { TeamOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { useMe } from "@/hooks/use-me";
import { usePendingClaimCount } from "./queries";

/**
 * Lối vào **`/quan-ly`** trên thanh đầu trang máy tính — trước bản sửa này,
 * đường dẫn ấy chỉ có MỘT nơi tham chiếu trong toàn bộ mã nguồn:
 * `<MoreMenu>`, và nút mở ngăn kéo ấy tự khai `md:hidden` (chỉ hiện trên
 * điện thoại). Nghĩa là một Trưởng chi hay Hội đồng mở cổng bằng máy tính —
 * hoàn toàn hợp lý cho người đang gõ danh sách nhập liệu hay đối chiếu đơn
 * bằng bàn phím — **không có nút, liên kết hay lối vào nào** dẫn tới hàng chờ
 * đơn tự nhận (`/quan-ly/don-gia-nhap`) hay màn phát mã (`/quan-ly/phat-ma`).
 * Gõ tay đường dẫn là cách duy nhất, và đó đúng là mẫu hình design 07 §4 đã
 * gọi tên: "khi một tính năng đã xong mà người dùng không biết, vấn đề nằm ở
 * lối vào, không phải ở tính năng".
 *
 * <h2>Vì sao đặt cạnh `<CorrectionQueueLink>` chứ không thay nó</h2>
 * Hai hàng chờ khác nhau, hai phạm vi khác nhau (đính chính theo `ltree` của
 * nhân khẩu bị sửa; đơn tự nhận theo `ltree` của chi được chỉ ra trong đơn),
 * và `/quan-ly` còn dẫn tới màn phát mã — thứ không phải một hàng chờ. Gộp
 * chúng vào một liên kết sẽ giấu một trong hai.
 *
 * <h2>Số trên huy hiệu là đơn tự nhận, KHÔNG PHẢI tổng cả năm hàng chờ</h2>
 * `usePendingClaimCount` đã có sẵn trong tầng server-state từ trước — được
 * viết, được xuất ra, và không ai gọi tới cho tới bản sửa này. Bài viết chờ
 * duyệt, vinh danh chờ duyệt và báo cáo ảnh chờ xử lý CHƯA có API đếm riêng
 * (`GET /posts`, `GET /honours`, báo cáo ảnh hiện chỉ trả danh sách đầy đủ),
 * nên một huy hiệu "tổng cả năm hàng chờ" sẽ phải tải cả năm danh sách chỉ để
 * đếm — cái giá không đáng cho một con số trên thanh đầu trang. Đơn tự nhận
 * là hàng chờ có hệ quả nặng nhất trong năm (khoá hẳn một người khỏi mọi thứ
 * cần tài khoản), nên nó là số duy nhất đáng trả giá gọi thêm một API mỗi khi
 * mở trang.
 */
export function ManagementQueueLink() {
  const t = useTranslations("membership");
  const { data: me } = useMe();

  const hasAccount = Boolean(me?.appUserId);
  const canReview =
    me?.role === "ADMIN" || me?.role === "COUNCIL" || me?.role === "BRANCH_HEAD";

  const pendingCount = usePendingClaimCount({ enabled: hasAccount && canReview });

  if (!hasAccount || !canReview) return null;

  return (
    <Link
      href="/quan-ly"
      title={t("nav.tooltip")}
      aria-label={
        pendingCount > 0 ? t("nav.labelWithCount", { count: pendingCount }) : t("nav.label")
      }
      className="flex min-h-11 items-center gap-1.5 rounded px-2 text-text-main no-underline hover:bg-primary-light hover:text-primary"
      data-testid="management-queue-link"
    >
      <Badge count={pendingCount} size="small" offset={[2, -2]}>
        <TeamOutlined aria-hidden className="text-lg text-text-main" />
      </Badge>
      <span className="hidden lg:inline">{t("nav.label")}</span>
    </Link>
  );
}
