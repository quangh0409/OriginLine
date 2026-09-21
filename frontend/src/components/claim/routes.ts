/**
 * **Bề mặt nối của luồng "tôi là ai trong phả"** — địa chỉ mà những màn khác
 * (trước hết là phả đồ) dẫn tới.
 *
 * <h2>Đây là hợp đồng giữa hai vùng tệp, nên nó ở một chỗ và chỉ một chỗ</h2>
 * Ô tìm người trên canvas phả đồ do người khác dựng; nút "Đây là tôi" và nút
 * "Tôi chưa có trong phả" nằm cạnh ô ấy, tức <b>trong vùng tệp của họ</b>.
 * Thứ duy nhất hai bên phải đồng ý là <b>địa chỉ</b>. Chép tay chuỗi
 * `"/nhan-dien?nguoi=…"` ở cả hai bên là cách chắc chắn nhất để một bên đổi và
 * bên kia im lặng dẫn tới trang trắng — nên bên phả đồ gọi {@link claimRoutes}
 * chứ không tự ghép.
 *
 * <h2>Vì sao `?nguoi=` mà không phải `/nhan-dien/<id>`</h2>
 * Đoạn đường dẫn trông gọn hơn nhưng đụng ngay vào hai đường tĩnh cùng cấp
 * (`/nhan-dien/chua-co`, `/nhan-dien/cho-duyet`): Next.js ưu tiên đường tĩnh
 * nên hôm nay nó chạy, và nó sẽ hỏng <em>lặng lẽ</em> vào ngày một mã nhân khẩu
 * trùng một trong hai chữ ấy. Chuỗi truy vấn không có lớp va chạm đó. Cái giá
 * phải trả là {@code useSearchParams()} — phải bọc trong {@code <Suspense>},
 * nếu không {@code next build} đỏ và <b>báo tên một trang vô can</b>; đó là cái
 * bẫy đã ghi trong CLAUDE.md và các trang ở đây đều đã bọc.
 *
 * <h2>Đường dẫn giữ nguyên tiếng Việt ở cả hai ngôn ngữ</h2>
 * Cùng lý do với `/moi` và `/danh-ba`: một người bác gửi đường dẫn này qua Zalo
 * cho cháu mình, và nó phải mở ra đúng một chỗ bất kể hai người đang để giao
 * diện ở ngôn ngữ nào. `next-intl` tự thêm tiền tố `/en` khi cần.
 */

/** Tên tham số mang mã nhân khẩu được chọn. Một chỗ duy nhất khai nó. */
export const THAM_SO_NGUOI = "nguoi";

export const claimRoutes = {
  /** Màn mở đầu — chưa chọn ai. Giải thích ba bước và mở hai lối đi. */
  start: "/nhan-dien",

  /**
   * Màn "tôi là ai trong phả" với một ô **đã chọn sẵn**.
   *
   * Đây là địa chỉ mà nút "Đây là tôi" trên phả đồ phải dẫn tới.
   */
  forPerson: (personId: string) =>
    `/nhan-dien?${THAM_SO_NGUOI}=${encodeURIComponent(personId)}`,

  /** Lối "Tôi chưa có trong phả" — nút thứ hai ngay cạnh ô tìm. */
  newPerson: "/nhan-dien/chua-co",

  /** Màn "đang chờ duyệt". */
  pending: "/nhan-dien/cho-duyet",
} as const;
