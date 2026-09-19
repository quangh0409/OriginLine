# Kế hoạch triển khai phần còn lại

Sáu tài liệu, mở bằng trình duyệt. Bắt đầu từ **00 — bản chốt phạm vi**; năm tài liệu
con chỉ chi tiết hoá nó, không được mâu thuẫn với nó.

| | Tài liệu | Trả lời |
|---|---|---|
| **00** | [Bản chốt phạm vi](00-tong-the.html) | **Đọc đầu tiên.** Năm đợt A–E, tám mục đã cắt, ràng buộc chung |
| 01 | [Đóng Giai đoạn 1](01-dong-gd1/01-dong-giai-doan-1.html) | Zalo · bộ áp dụng đính chính · endpoint công khai · Web Push · tiếp cận |
| 02 | [Nhập liệu ban đầu](02-nhap-lieu/index.html) | Mẫu Excel · đường ống nhập · đối soát · gộp trùng |
| 03 | [Giai đoạn 3](03-giai-doan-3/index.html) | [heritage](03-giai-doan-3/01-heritage.html) · [fund &amp; reporting](03-giai-doan-3/02-fund-reporting.html) · [rủi ro](03-giai-doan-3/03-rui-ro-lo-trinh.html) |
| 04 | [Frontend còn lại](04-frontend/index.html) | 17 màn đề xuất · nợ tiếp cận · chỗ chặn vì backend |
| 05 | [Lịch trình &amp; rủi ro](05-lich-trinh/05-lich-trinh-rui-ro.html) | Đường găng · sổ rủi ro · tiêu chí ra từng đợt · điểm dừng an toàn |

---

## Kết luận quan trọng nhất

**Giai đoạn 1 chưa đạt tiêu chí ra**, dù 1.827 test backend xanh. Thiếu ba thứ, đo từ mã
nguồn: **không có provider Zalo** (`notification/infrastructure/` chỉ có `inapp` và
`webpush`), **Web Push chưa từng gửi tới thiết bị thật** và **không một test nào** chạm
tầng gửi của nó, và **luồng đính chính duyệt xong không đổi gì trong phả**.

**Đường găng không đi qua phần khó nhất về kỹ thuật.** Ba context rỗng là đợt nặng mã
nhất nhưng nằm *sau* đường găng. Đường găng đi qua **cử người nhập liệu → nhập liệu thật
bốn chi → mọi thứ còn lại**, với nhánh thứ hai là **thủ tục duyệt Zalo OA**.

Hệ quả: ước **72 người-tuần chia 4 người = 18 tuần làm việc, nhưng lịch dài 28 tuần.**
Mười tuần chênh là thời gian **chờ** — chờ duyệt hồ sơ, chờ người nhập liệu, chờ nội
dung. **Thêm lập trình viên không rút ngắn được lịch.**

---

## Bốn việc phải khởi động ngay, không đợi dòng mã nào

1. **Cử người phụ trách nhập liệu từng chi** — nằm thẳng trên đường găng. Bốn chi mà chỉ
   ba người thì thời gian cho chi thứ tư không phải "×4/3", nó là **vô hạn**. Cần cả
   người dự phòng.
2. **Nộp hồ sơ Zalo OA cùng ba mẫu tin ZNS một lượt** (7/3/1 ngày — đừng nộp lần lượt).
   Câu hỏi chặn đầu tiên: **dòng họ không có tư cách pháp nhân thì ai đứng tên OA?**
3. **Cử bốn vai nội dung riêng biệt**: chụp ảnh · đo toạ độ · phiên âm Hán-Nôm · viết bài.
   Không có thì Giai đoạn 3 ra bốn màn hình đẹp và trống.
4. **Cài JDK 21 và dựng CI.** Đo trực tiếp: `java -version` ra `1.8.0_202`, và **không có
   `.github/workflows`, không có cấu hình CI nào**. Chừng nào chưa có CI, mọi con số
   "1.827 test xanh" là **lời kể lại, không phải bằng chứng**.

---

## Điểm dừng an toàn

- **Dừng sau đợt A** — phần mềm nghiệm thu được nhưng **chưa có dữ liệu thật của dòng họ
  nào**. Đây là phần mềm nằm trên kệ.
- **Cổng B là điểm dừng an toàn đầu tiên.** Đề nghị lấy **cổng B, không phải cổng A**, làm
  mốc "Giai đoạn 1 xong" trong hợp đồng.
- **Dừng sau D mà bỏ E là điểm dừng tệ nhất** — nhiều dữ liệu cá nhân nhất, kiểm chứng ít
  nhất. Ngân sách chỉ tới đó thì nên dừng ở **C**.

---

## Mười phát hiện từ mã nguồn làm đổi kế hoạch

Tất cả **đếm từ mã nguồn**, không suy đoán.

| Phát hiện | Hệ quả |
|---|---|
| **Toàn hệ thống có 0 `@EventListener` thật** — hai chỗ duy nhất chứa chuỗi đó đều nằm trong javadoc | Cơ chế domain event **chưa có người tiêu thụ nào**, không riêng gì đính chính |
| **0 test cho tầng gửi Web Push** — không tệp nào chạm `WebPushAdapter`/`VapidSigner`/`WebPushCipher` | "1.827 test xanh" **không nói gì** về tiêu chí ra số 7 |
| **`PrivacyLevel` là enum 4 giá trị áp cho cả con người** | Điều khiển riêng tư **từng mục** trong bản thiết kế **không làm được**; toàn bộ đợt B đứng chờ |
| **Không nơi nào truy vấn được số điện thoại** — số chôn trong `person.attributes`, `app_user` chỉ có email | ZNS gửi theo số ⇒ đợt A cần thêm đường lấy số **và** bản ghi đồng ý |
| **`native_place`/`current_place` là `VARCHAR(255)` tự do** | FR-4.3 không dựng được; mã tỉnh phải vào **đợt B**, quyết muộn thì cả họ nhập lại |
| **`confirmDuplicateOverride` chảy đủ ba tầng rồi không ai đọc**; `DUPLICATE_PERSON_SUSPECTED` chưa từng ném | Hợp đồng **hứa một tính năng chưa tồn tại**: không có phép dò trùng nào |
| **Ba biến `--tree-line-*` chưa từng được khai** | Đường nối phả đồ **vẽ màu chế độ sáng trên nền tối** |
| **Mã loại sự kiện gộp mất thông tin** — họp họ, đám cưới, khánh thành đều thành `KHAC`; `SINH_NHAT → MUNG_THO` | Màn lịch và màn sinh nhật của đợt C **hôm nay dựng không đúng được** |
| **Yêu cầu đính chính không mang số phiên bản**, trong khi mọi lệnh ghi bắt buộc có ETag | Đề nghị chờ hàng tuần rồi áp dụng sẽ **ghi đè mù** |
| **`tierFor()` trả `T1` cho khách nhìn người sống** thay vì từ chối | "Gọi `canSee` trước" là bất biến bảo mật **không được kiểu dữ liệu bảo vệ** — mà đợt A đang mở loạt lối vào mới |

---

## Ba câu hỏi chặn, chờ Hội đồng Tộc biểu

Gắt nhất là câu thứ hai: nó phải xong **trước khi mở đường ống nhập liệu**. Sau khi đã
nhập 1.500 người thật, đổi luật xoá không còn là sửa mã.

1. Màu huy hiệu **Dâu / Rể** — hai sắc lạnh duy nhất trên phả đồ toàn sắc đất.
2. **Quyền xoá dữ liệu của dâu/rể**, đặc biệt sau ly hôn.
3. **Di chúc** có tách khỏi thư viện di sản không — khuyến nghị Giai đoạn 3 chỉ lưu siêu
   dữ liệu, không lưu nội dung.

---

## Ước lượng

Ghi rõ **là ước lượng**, suy ra bằng cách so với các context đã đo được, dùng để so độ
lớn tương đối chứ **không để hứa ngày**.

| Đợt | Ngày-người |
|---|---|
| A · Đóng Giai đoạn 1 | 51–72 |
| B · Nhập liệu ban đầu | 53 |
| C · Nợ Giai đoạn 2 | ~7 (GEDCOM) + phần frontend |
| D · Giai đoạn 3 | 109–164 |
| Frontend xuyên suốt | ~91 |

Trong đó **≈18,5 ngày frontend đầu tiên không phụ thuộc backend gì cả** — chạy được ngay
hôm nay, song song với việc backend dựng Zalo và endpoint công khai.
