"use client";

import type { ReactNode } from "react";
import { KhungTrang } from "@/components/common/khung-trang";
import { AccountStateNotice } from "./account-state-notice";
import { useAccountProblem, type AccountProblem } from "./use-account-problem";

/**
 * Mã lỗi để dán vào một cuộc gọi hỗ trợ. Không hiện ra ngay — xem
 * {@code AccountStateNotice}.
 */
const MA_HO_TRO: Readonly<Record<AccountProblem, string>> = {
  NOT_PROVISIONED: "ACCOUNT_NOT_PROVISIONED",
  NOT_ACTIVE: "ACCOUNT_NOT_ACTIVE",
};

/**
 * Chặn giữa khung ứng dụng và nội dung trang: khi tài khoản chưa dùng được thì
 * **thay** nội dung trang bằng lời giải thích, thay vì để trang tự vẽ "không
 * tìm thấy".
 *
 * <h2>Vì sao thay tại chỗ chứ không điều hướng sang một đường dẫn riêng</h2>
 * Bản thiết kế đề xuất đẩy sang {@code /vi/tai-khoan/chua-noi}. Thay tại chỗ
 * được ba thứ mà điều hướng không có:
 * <ul>
 *   <li><b>giữ nguyên URL</b>, nên khi Hội đồng nối xong tài khoản, đúng một
 *       lượt tải lại là người dùng ở lại chính trang họ đang muốn mở — không
 *       phải tìm đường về;</li>
 *   <li><b>không có vòng lặp điều hướng</b>: trang đích cũng gọi API, cũng nhận
 *       cùng lỗi ấy, và một lần đẩy nữa là một vòng lặp;</li>
 *   <li>không phải thêm một đường dẫn vào bộ định tuyến — tức không đụng vào
 *       vùng tệp của người khác để dựng một màn thuộc về mình.</li>
 * </ul>
 *
 * <h2>Nó KHÔNG phải một lớp phân quyền</h2>
 * Đây thuần tuý là hiển thị. Không có gì ở đây giữ dữ liệu lại: máy chủ đã từ
 * chối trước rồi, và chính lời từ chối ấy là thứ thành phần này đọc được. Gỡ nó
 * đi thì màn hình xấu lại, không hở thêm một trường nào.
 */
export function AccountStateGate({ children }: { children: ReactNode }) {
  const problem = useAccountProblem();

  if (!problem) return <>{children}</>;

  return (
    <KhungTrang beRong="hep">
      <AccountStateNotice problem={problem} technicalCode={MA_HO_TRO[problem]} />
    </KhungTrang>
  );
}
