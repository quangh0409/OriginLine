"use client";

import type { ReactNode } from "react";

export interface VungCuonNgangProps {
  /**
   * Nhãn của vùng cuộn, đọc lên cho người dùng trình đọc màn hình.
   *
   * Bắt buộc, không có giá trị mặc định: một `role="region"` không tên là một
   * mốc trống trong danh sách landmark — trình đọc màn hình đọc ra "vùng" và
   * hết. Nơi gọi biết nội dung là bảng gì, chỗ này thì không.
   */
  readonly nhan: string;
  readonly children: ReactNode;
  readonly className?: string;
  /** Để `data-testid` của nơi gọi đi xuyên xuống mà không phải bọc thêm một lớp. */
  readonly "data-testid"?: string;
}

/**
 * **Vùng cuộn ngang riêng** — nơi duy nhất trong sản phẩm được phép cuộn ngang.
 *
 * <h2>Vì sao phải có một thành phần riêng thay vì rắc `overflow-x-auto`</h2>
 * Ràng buộc bố cục của sản phẩm có hai vế, và vế thứ hai hay bị bỏ quên:
 * *thân trang không bao giờ được cuộn ngang; chỉ bảng, sơ đồ và khối mã được
 * cuộn, **mỗi thứ trong vùng cuộn riêng***. Một `<table>` đặt trần trong thẻ
 * không co xuống dưới bề rộng nội tại của nó (`table-layout: auto` lấy tổng
 * min-content của các cột), nên nó đẩy thẻ rộng ra và thẻ tràn khỏi viền cha —
 * rồi CẢ TRANG cuộn ngang. `overflow-x: auto` ở đây cắt chuỗi lan truyền đó
 * ngay tại bảng.
 *
 * <h2>`tabIndex={0}` là phần tiếp cận, không phải trang trí</h2>
 * Một vùng cuộn được mà không lấy được tiêu điểm thì người chỉ dùng bàn phím
 * không có cách nào cuộn nó (WCAG 2.1.1; đúng luật `scrollable-region-focusable`
 * của axe). Có tiêu điểm thì vòng tiêu điểm hai lớp khai ở `globals.css` tự áp
 * vào qua bộ chọn `[tabindex]:focus-visible` — không cần thêm CSS nào ở đây.
 *
 * <h2>`overscroll-x-contain`</h2>
 * Cuộn hết bảng thì dừng, không đẩy tiếp cử chỉ vuốt sang lịch sử trình duyệt.
 * Trên điện thoại — dạng máy chính của cổng này — vuốt ngang trong bảng mà bị
 * "quay lại trang trước" là mất luôn cả biểu mẫu đang dở.
 *
 * <h2>`max-w-full` chứ không `w-full`</h2>
 * `w-full` ép bề rộng bằng cha kể cả khi nội dung hẹp hơn, làm viền/nền kéo dài
 * quá nội dung ở những bảng ngắn. `max-w-full` chỉ đặt TRẦN — đúng thứ cần.
 */
export function VungCuonNgang({
  nhan,
  children,
  className,
  "data-testid": testId,
}: VungCuonNgangProps) {
  return (
    <div
      role="region"
      aria-label={nhan}
      tabIndex={0}
      data-vung-cuon="ngang"
      data-testid={testId}
      className={`max-w-full overflow-x-auto overscroll-x-contain${className ? ` ${className}` : ""}`}
    >
      {children}
    </div>
  );
}
