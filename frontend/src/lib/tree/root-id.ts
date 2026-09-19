/**
 * **Phả đồ bắt đầu từ ai?** — câu hỏi ấy nay do **máy chủ** trả lời.
 *
 * <h2>Chuyện đã xảy ra ở tệp này</h2>
 * `rootId` từng là tham số **bắt buộc** của cả `GET /api/v1/tree` lẫn
 * `GET /api/v1/public/tree`, và máy chủ không có khái niệm "gốc mặc định của
 * dòng họ". Giao diện vá chỗ trống ấy bằng một chuỗi bốn bước ở phía client:
 * `?rootId=` → `NEXT_PUBLIC_DEFAULT_ROOT_ID` → gốc đã xem lần trước
 * (`localStorage`) → hỏi người dùng qua `<TreeRootPicker>`.
 *
 * Trong lúc đó backend giải cùng bài toán theo hướng khác và **hay hơn**: cho
 * `rootId` thành **tuỳ chọn**, thiếu nó thì máy chủ chọn gốc **theo vai và
 * phạm vi chi/ngành** của chính người gọi (openapi `GET /tree`, mục "`rootId`
 * tuỳ chọn — gốc mặc định theo vai"). Quản trị/Hội đồng mở ra thuỷ tổ; Trưởng
 * chi mở ra ông tổ của chi mình được giao; Thành viên mở ra ông tổ chi nhà
 * mình; Khách mở ra thuỷ tổ qua `/public/tree`. Không nhánh nào của trình duyệt
 * biết được ba dữ kiện đó — vai, phạm vi `ltree`, và ai là thuỷ tổ.
 *
 * Nên chuỗi bốn bước bị rút còn **một**:
 *
 * <ol>
 *   <li><b>`?rootId=` trên URL</b> — vẫn thắng tuyệt đối. Đây là cách người
 *       trong họ chia sẻ **một nhánh cụ thể** vào nhóm Zalo, và chính cổng công
 *       khai mô tả lối vào ấy. Một liên kết đã dán đi rồi phải mở ra đúng nhánh
 *       người gửi đang xem, bất kể người nhận là vai gì.</li>
 *   <li><b>Không có gì cả</b> → <b>không gửi `rootId`</b>, để máy chủ chọn. Gốc
 *       nó chọn nằm ở trường `rootId` của phản hồi.</li>
 * </ol>
 *
 * <h2>Ba bước đã bị xoá, và vì sao</h2>
 *
 * <b>`NEXT_PUBLIC_DEFAULT_ROOT_ID`</b> — xoá. Nếu ai đặt nó thì **mọi vai đều
 * mở cùng một gốc**: trưởng chi Ất, trưởng chi Bính và thành viên đều rơi về
 * thuỷ tổ, xoá sạch phần cá nhân hoá máy chủ vừa làm. Một biến môi trường im
 * lặng vô hiệu hoá một tính năng là loại hỏng khó truy nhất.
 *
 * <b>Gốc đã xem lần trước (`localStorage`)</b> — xoá. Nó tiện cho người quay
 * lại, nhưng ba điều làm nó không còn trả giá nổi:
 * <ul>
 *   <li>Nó <b>ghi đè lựa chọn theo vai của máy chủ</b> — chính thứ vừa được
 *       dựng. Trưởng chi Ất mở một liên kết chi Bính bạn bè gửi, từ đó trở đi
 *       phả đồ của họ mặc định mở ra chi Bính, mãi mãi, không ai giải thích.</li>
 *   <li>Nó <b>không phân biệt được "tôi đã chọn" với "tôi vô tình mở"</b>: nó
 *       được ghi sau <i>mọi</i> lần tải gốc thành công, kể cả gốc đến từ một
 *       liên kết dán vào hay từ biến cấu hình.</li>
 *   <li>Nó <b>sống sót qua đăng xuất</b>. Gốc nhớ từ phiên của một thành viên
 *       được phát lại cho phiên sau — và với khách thì đó có thể là một id họ
 *       không có quyền biết là tồn tại, đổi lấy một `404` khó hiểu ngay ở màn
 *       hình chủ lực.</li>
 * </ul>
 * Tiện ích "người quay lại không phải chọn lại" vốn đã có chỗ đứng tốt hơn:
 * mọi gốc người dùng **chủ ý** chọn đều được `<TreeCanvas>` ghi thẳng vào
 * `?rootId=` trên URL, nên lịch sử trình duyệt và dấu trang đã mang sẵn nó —
 * hiện ra, chia sẻ được, và xoá được. `localStorage` chỉ là một bản sao thứ hai
 * của đúng thứ ấy, vô hình và không xoá được.
 *
 * <b>`<TreeRootPicker>` là bước bắt buộc</b> — xoá tư cách bắt buộc, giữ lại
 * component. Nó vẫn là cách **đổi gốc chủ động**, và vẫn là lối thoát khi máy
 * chủ từ chối một `?rootId=` cũ; nó chỉ không còn chắn giữa người dùng và lần
 * xem cây đầu tiên nữa.
 */

/**
 * `?rootId=` sau khi chuẩn hoá. **`null` nghĩa là "để máy chủ chọn"**, không
 * phải lỗi và không phải "hỏi người dùng".
 *
 * Chỉ cắt khoảng trắng và loại chuỗi rỗng — **không** kiểm định dạng UUID.
 * Trước đây có kiểm, và nó âm thầm làm hỏng đúng thứ nó định bảo vệ: một liên
 * kết chia sẻ mang id sai định dạng sẽ bị bỏ ở trình duyệt rồi mở ra **cây mặc
 * định**, tức người nhận nhìn một nhánh khác hẳn nhánh được gửi mà tưởng liên
 * kết chạy đúng. Nay id đi thẳng lên máy chủ: máy chủ là nơi duy nhất biết id
 * nào có thật, và `400`/`404` của nó dẫn người dùng tới bước chọn gốc với đúng
 * một câu — "chưa mở được phả đồ từ điểm bắt đầu này".
 */
export function rootIdFromQuery(value: string | null | undefined): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}
