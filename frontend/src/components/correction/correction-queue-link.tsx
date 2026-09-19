"use client";

import { Badge } from "antd";
import { FileSyncOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { usePendingChangeRequestCount } from "@/hooks/use-change-requests";
import { useMe } from "@/hooks/use-me";

/**
 * Lối vào màn hình đính chính trên thanh đầu trang, kèm số yêu cầu chờ duyệt.
 *
 * <h2>Vì sao phải nằm ở thanh đầu trang</h2>
 * Một hàng đợi duyệt mà chỉ vào được bằng cách gõ đường dẫn là một hàng đợi
 * không ai đụng tới. Trưởng chi phải **thấy** rằng có ba người đang chờ mình
 * quyết, ngay khi mở ứng dụng — nếu không, đề nghị của thành viên nằm đó hàng
 * tuần và người gửi kết luận rằng gửi cũng vô ích.
 *
 * Thanh đầu trang hiện trên cả điện thoại (khác cụm liên kết điều hướng vốn
 * `hidden md:flex`), nên đây là chỗ duy nhất mục này với tới được người dùng
 * di động — vốn là phần đông của cổng này.
 *
 * Khách không thấy gì cả: `useMe()` trả `appUserId: null` và thành phần này
 * biến mất hoàn toàn, không để lại một cái vỏ gợi ý rằng có thứ gì đó bị khoá.
 */
export function CorrectionQueueLink() {
  const t = useTranslations("correction");
  const { data: me } = useMe();

  const hasAccount = Boolean(me?.appUserId);
  const canReview =
    me?.role === "ADMIN" || me?.role === "COUNCIL" || me?.role === "BRANCH_HEAD";

  const pendingCount = usePendingChangeRequestCount({
    enabled: hasAccount && canReview,
  });

  if (!hasAccount) return null;

  return (
    // KHÔNG bọc <Tooltip> nữa. Ba lý do, và cả ba đo được:
    //  1. tooltip không tồn tại trên màn hình cảm ứng, nên nhãn duy nhất của điều
    //     khiển này biến mất với đúng phần đông người dùng của cổng;
    //  2. tooltip của Ant Design mở khi phần tử NHẬN TIÊU ĐIỂM và vẽ đè lên nó —
    //     đó là lỗi C-4.4 "phần tử được tiêu điểm bị lớp khác che khuất";
    //  3. `aria-label` chỉ phục vụ trình đọc màn hình. Nhãn chữ nhìn thấy được là
    //     nguyên tắc 3 của định hướng 00, không phải tuỳ chọn.
    // Phần giải thích dài để ở `title` cho người dùng chuột.
    <Link
      href="/correction-requests"
      title={t("nav.tooltip")}
      aria-label={
        pendingCount > 0 ? t("nav.labelWithCount", { count: pendingCount }) : t("nav.label")
      }
      // `min-h-11` = 44px: trước đợt sửa này đây là thẻ <Link> trần, đo được 18×20px.
      className="flex min-h-11 items-center gap-1.5 rounded px-2 text-text-main no-underline hover:bg-primary-light hover:text-primary"
      data-testid="correction-queue-link"
    >
      <Badge count={canReview ? pendingCount : 0} size="small" offset={[2, -2]}>
        <FileSyncOutlined aria-hidden className="text-lg text-text-main" />
      </Badge>
      <span className="hidden lg:inline">{t("nav.label")}</span>
    </Link>
  );
}
