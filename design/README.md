# Thiết kế trải nghiệm — Cổng Thông Tin Dòng Họ

Bộ tài liệu thiết kế UX/UI cho toàn dự án: người dùng, hệ thống thị giác, kiến trúc
thông tin, các luồng lõi, và những tính năng chưa làm.

**Mở bằng trình duyệt.** Mọi trang là HTML độc lập, nạp chung `assets/design-doc.css`.
Không cần cài gì, không cần chạy máy chủ.

---

## Đọc theo thứ tự nào

| | Tài liệu | Trả lời câu hỏi |
|---|---|---|
| **00** | [Định hướng thiết kế](00-dinh-huong.html) | **Đọc đầu tiên.** Thiết kế cho ai, theo nguyên tắc nào |
| 01 | [Người dùng &amp; chuẩn tiếp cận](01-nguoi-dung/01-nguoi-dung-va-chuan-tiep-can.html) | Sáu chân dung 18–71 tuổi, 17 yêu cầu tiếp cận kiểm được |
| 02 | [Hệ thống thiết kế](02-he-thong-thiet-ke/index.html) | Màu, chữ, khoảng cách, kho thành phần, ba mật độ |
| 03 | [Kiến trúc thông tin](03-kien-truc-thong-tin/03-kien-truc-thong-tin.html) | 23 FR nằm ở đâu, điều hướng, tìm kiếm, nhập môn |
| 04 | [Luồng trải nghiệm lõi](04-luong-trai-nghiem/04-luong-trai-nghiem.html) | Bảy luồng chính, 13 khung dây, chỗ đang hỏng |
| 05 | [Tính năng tương lai](05-tinh-nang-tuong-lai/index.html) | Giai đoạn 3–4 và tầng AI · [di sản &amp; mộ phần](05-tinh-nang-tuong-lai/01-di-san-mo-phan.html) · [quỹ họ](05-tinh-nang-tuong-lai/02-quy-ho-bao-cao-ban-tin.html) · [nhập liệu &amp; AI](05-tinh-nang-tuong-lai/03-nhap-lieu-va-tang-ai.html) |

**Tài liệu 00 là ràng buộc, không phải gợi ý.** Chỗ nào các tài liệu khác mâu thuẫn với
nó thì nó thắng. Thứ tự ưu tiên chung: **BA v2 → 00 → các tài liệu còn lại**.

---

## Hai luận điểm nền

**Người dùng trung bình không tồn tại.** Dự án phục vụ người 16–70 tuổi, và hai đầu dải
này có nhu cầu *ngược nhau* — không phải hai biến thể của cùng một người. Lấy trung bình
cho ra thứ không phục vụ tốt ai. Nguyên tắc thay thế: **thiết kế cho hai đầu, phần giữa
tự lo được** — ai đọc được 18px thì cũng đọc được 16px, chiều ngược lại thì không.

**Tuổi không phải trục duy nhất.** Người con dâu, con rể không nằm ở đầu nào của dải
tuổi, nhưng **không thuộc huyết thống** — và đó là trục thứ hai. Họ là người *phải xưng
hô đúng nhiều nhất và biết ít nhất*. Chi tiết ở [00 §1b](00-dinh-huong.html).

---

## Trạng thái sửa lỗi — cập nhật sau đợt vá

Bộ tài liệu tìm ra 15 lỗi ở sản phẩm đang chạy. **11 lỗi đã được giao sửa; dưới đây là kết quả
thật, không phải kế hoạch.**

### Đã sửa và đã kiểm chứng

| Lỗi | Bằng chứng |
|---|---|
| Phả đồ **không phân biệt được người sống và đã khuất** — `bg-success` không sinh CSS vì thiếu token | Lớp nay sinh `rgb(21 128 61)`; test phủ toàn bộ token Tailwind |
| **Tra danh xưng hỏi ngược 180°** — hồ sơ nối `?from=` | Đổi sang `?to=`; test khẳng định href không chứa `from=` |
| **Đổi ngôn ngữ làm rơi hết tham số** | Mang theo `searchParams` |
| **11 cặp màu trượt chuẩn tương phản AA** | Thêm `accentText` 5,02:1 · `successText` 6,13:1 · `borderInput` 4,15:1 · vòng tiêu điểm hai lớp 13,58:1 |
| **`colorLinkHover` làm liên kết khó đọc đi 2,6 lần** | 2,95 → 11,07:1 |
| **Ant Design chạy 14px / cao 32px** | Đo thật: nút **16px / 44px**; gỡ `Button.algorithm` vốn đè mất giá trị đã khai |
| **`prefers-reduced-motion` xuất hiện 0 lần** | Chặn ở CSS + guard JS cho `fitView` (React Flow chạy hoạt ảnh bằng JS, CSS không với tới) |
| **Chế độ tối khai `darkMode` nhưng 0 lần dùng `dark:`** | Ba trạng thái chạy thật, đo trên bốn tổ hợp hệ điều hành × lựa chọn |
| **Luồng đính chính không có điểm bắt đầu** | Dựng cả hai phía: gửi và duyệt, có so sánh trước/sau, chặn tự duyệt |
| **Hồ sơ không có mục Quan hệ** | Nhóm theo cha mẹ · vợ/chồng · con · anh chị em, kèm con nuôi, thứ tự vợ, kế tự |
| **`meta.visibleTier` không được đọc** | Thông báo chỉ phụ thuộc `(còn sống, tầng)`, **không bao giờ phụ thuộc bản ghi** |
| **Không có gì giữ dữ liệu chưa lưu** | Chặn ba lối: đóng tab · vuốt lùi · bấm liên kết. Nháp ở `sessionStorage` |
| **Nút bung nhánh 18px** | **60×60px** ở mức phóng mặc định, đo trên cả máy tính lẫn Pixel 5 |
| **Thẻ đếm nói sai** | In số **đang hiển thị** thật; số đã tải chuyển sang `data-loaded-count` |

### Còn đỏ có chủ ý — 4 test, 2 nguyên nhân

**1 · Màu huy hiệu Dâu và Rể** (`src/lib/tree/badges.ts:19-20`). Hai mã màu duy nhất viết ngoài
`tokens.ts`, và là hai sắc **lạnh** duy nhất của sản phẩm — nên trên phả đồ toàn sắc đất, người
không thuộc huyết thống hiện lên thành hai đốm lạ. Đó chính là khung *"trong họ / người ngoài"* mà
[00 §1b](00-dinh-huong.html) cấm, nhưng nói bằng màu. **Chờ Hội đồng Tộc biểu quyết** — đây không
phải lỗi kỹ thuật để lập trình viên tự vá.

**2 · Hổ phách `#d97706` trên nền trang = 2,95:1**, hụt ngưỡng 3:1 cho đồ hoạ mang nghĩa. Đã sửa ở
**mọi chỗ dùng** (thanh hôn phối và vạch sống/khuất trên hồ sơ nay dùng `accentText` 5,02:1), nhưng
test khẳng định ở **tầng bảng màu**, không ở tầng chỗ dùng. Đây là **bất đồng có thật giữa hai
quan điểm**, không phải bug: sửa bảng màu là phá quyết định vật liệu đã chốt ở BA v2; giữ nguyên
thì cặp màu ấy vĩnh viễn không được dùng làm mảng tô mang nghĩa. Cần chốt một hướng rồi thu hẹp
hoặc giữ nguyên phép kiểm.

### Chưa sửa — phát hiện mới, nằm ngoài 11 việc được giao

Bộ kiểm hồi quy mới dựng bắt thêm những thứ không ai ngờ:

- **Liên kết điều hướng trên thanh đầu trang cao 20px**; `"Yêu cầu đính chính"` và `"Cài đặt"` chỉ
  **18×20px** — dưới sàn 44px, đỏ trên cả điện thoại.
- **Màn lịch giỗ: 73/76 điều khiển dưới sàn**, nút ngày 156×18px.
- **Hồ sơ nhân khẩu: 39/61 đoạn chữ dưới 16px**, thẻ `"Tên chính"` chỉ **10px**.
- **Ô `<Select>` của Ant Design không có dấu hiệu tiêu điểm nào thấy được** (1,00:1) — quy tắc vòng
  tiêu điểm trong `globals.css` loại trừ mọi widget AntD, kèm một chú thích nói vòng mặc định của
  chúng "đã tạm chấp nhận được". Đo ra thì không.
- **Không có liên kết "bỏ qua, tới nội dung chính"** — điểm dừng Tab đầu tiên không phải nó.
- **Huy hiệu đếm thông báo**: chữ trắng trên hổ phách = 2,07:1 ở chế độ tối, 3,19:1 ở chế độ sáng.

### Khoảng trống backend chặn tính năng — cần quyết trước khi làm tiếp

- **Duyệt đính chính xong nhưng gia phả không đổi.** `ChangeRequestApprovedEvent` **không có người
  nhận nào** — javadoc của chính lớp đó ghi *"Ở W6 chưa có người nhận"*. Trưởng chi bấm Duyệt, hệ
  thống ghi `APPROVED`, dữ liệu vẫn sai. Tệ hơn không có luồng, vì nó tạo cảm giác đã sửa.
- **Người duyệt có thể không thấy giá trị "trước".** `ChangeRequestView` không mang ảnh chụp dữ
  liệu cũ, nên giao diện phải gọi riêng — mà lời gọi ấy bị lọc theo tầng riêng tư *của chính người
  duyệt*. Với thay đổi Tầng 3 trên người còn sống, Trưởng chi có thể **duyệt mà không nhìn thấy
  giá trị mình sắp ghi đè**.
- **Từ chối không bắt buộc nêu lý do ở máy chủ** — giao diện bắt buộc, backend thì không.
- **`PersonDto.relationships` chỉ mang id**, không tên/đời/chi — không dựng được danh sách quan hệ
  đọc được nếu không gọi thêm một lượt `/tree`.
- **Khách vẫn không xem được gì**: `SecurityConfig` dành sẵn `/api/v1/public/**` nhưng **không
  controller nào ánh xạ vào đó**, trong khi BA v2 §10 nói người đã khuất công khai kể cả với khách.

## Câu hỏi cần Hội đồng Tộc biểu quyết

1. **Huy hiệu Dâu/Rể là hai màu lạnh duy nhất** trên phả đồ toàn sắc đất, và là hai mã
   màu duy nhất viết ngoài `tokens.ts`. Người không thuộc huyết thống hiện lên thành hai
   đốm lạ — đúng khung "trong họ / người ngoài" mà 00 §1b cấm, nhưng nói bằng màu.
2. **Quyền xoá dữ liệu của dâu/rể.** BA v2 §10 chọn ẩn danh hoá thay vì xoá, với lý do
   giữ cây khỏi gãy — lập luận đó *yếu hẳn* ở ca dâu/rể, vì xoá họ không làm gãy đường
   huyết thống nào. Trong khi cơ sở pháp lý để đòi xoá lại mạnh hơn: họ *được nhập vào*
   chứ không tự nguyện gia nhập. Ca ly hôn còn gay gắt hơn.
3. **Di chúc có nên tách khỏi thư viện di sản không?** Nó khác văn bia ở chỗ có thể còn
   hiệu lực pháp lý và nêu tên người đang sống.
4. **Chín tính năng đề nghị cắt hoặc hoãn** — xem bảng ở [tài liệu 05](05-tinh-nang-tuong-lai/index.html).

---

## Quy ước khi viết thêm

- Một trang HTML độc lập cho mỗi chủ đề, nạp `assets/design-doc.css` bằng đường dẫn
  tương đối. **Không sao chép CSS vào trang.**
- Khung dây vẽ bằng **SVG thuần**, dùng lớp `.wf-*` có sẵn. Không nhúng ảnh, không thư viện.
- Quy ước đo: **1 đơn vị SVG = 1 pixel thật**. Khung điện thoại vẽ ở 390 đơn vị, khung máy
  tính ở 1280. Vùng chạm trong khung dây phải ≥44×44 — khung dây vi phạm chính nguyên tắc
  đang viết thì tài liệu tự bác bỏ mình.
- Màu lấy từ biến CSS trong stylesheet chung, **không viết mã hex** — có thế chế độ tối
  mới không vỡ.
- **Nêu cái giá, không chỉ nêu ý tưởng.** Tài liệu thiết kế chỉ toàn ưu điểm là tài liệu
  chưa nghĩ xong.
- **Không bịa số.** Số lấy từ mã nguồn thì ghi đường dẫn; số là đề xuất thì nói rõ.
