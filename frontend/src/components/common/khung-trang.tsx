import type { ReactNode } from "react";

/** Bề rộng đọc được của một trang nội dung. */
export type BeRongKhung = "vua" | "hep";

const BE_RONG: Record<BeRongKhung, string> = {
  /** Mặc định — hồ sơ, danh sách, màn nhập liệu. */
  vua: "max-w-3xl",
  /** Biểu mẫu một cột và trang cài đặt: hẹp hơn thì mắt đỡ phải quét ngang. */
  hep: "max-w-2xl",
};

export interface KhungTrangProps {
  readonly children: ReactNode;
  readonly beRong?: BeRongKhung;
  readonly className?: string;
}

/**
 * **Khung một trang nội dung** — lề, bề rộng tối đa và khoảng thở dọc.
 *
 * <h2>Vì sao gom lại một chỗ</h2>
 * Chuỗi `mx-auto w-full max-w-3xl px-3 py-4 sm:px-4 sm:py-6` từng được chép tay
 * ở **17 tệp trang**. Một quy ước bố cục sống bằng cách chép tay thì nó không
 * phải quy ước — nó là 17 bản sao sẽ lệch nhau, và lệch rồi thì không có gì bắt
 * được. Sửa lề ở đây là sửa cho cả sản phẩm.
 *
 * <h2>Lề ngang là 16px, ở MỌI bề rộng — `px-4`, không phải `px-3`</h2>
 * Cả 17 bản sao đều mở đầu bằng `px-3`, tức **12px** trên điện thoại rồi mới
 * lên 16px từ `sm:`. Nhưng điện thoại mới là dạng máy chính của cổng này, nên
 * bản sao ấy đặt lề mỏng nhất đúng vào nơi cần lề nhất: ở 12px, viền thẻ gần
 * như dính mép màn hình, và trên máy có màn cong thì phần bo tròn ăn mất một
 * phần viền. Sàn 16px không có biến thể theo bề rộng.
 *
 * <h2>`w-full` đi cùng `max-w-*`, không thay được cho nhau</h2>
 * `max-w-*` chỉ đặt trần; thiếu `w-full` thì khối này là con của một `<main>`
 * dạng flex và có thể co theo nội dung, làm `mx-auto` canh giữa một khối hẹp
 * hơn trang. Hai lớp này luôn đi đôi.
 */
export function KhungTrang({ children, beRong = "vua", className }: KhungTrangProps) {
  return (
    // `data-khung-trang` là mốc để phép đo hình học trên trình duyệt thật
    // (e2e/layout-containment.spec.ts) tìm đúng khung trang mà đo lề, thay vì
    // đoán theo cấu trúc DOM — thứ sẽ trôi ngay lần đổi bố cục tới.
    <div
      data-khung-trang={beRong}
      className={`mx-auto w-full ${BE_RONG[beRong]} px-4 py-4 sm:py-6${
        className ? ` ${className}` : ""
      }`}
    >
      {children}
    </div>
  );
}
