# Báo cáo

Nơi lưu mọi **báo cáo** của dự án, chia theo **ngày lập**. Mở [`index.html`](index.html)
để xem mục lục (mới nhất trước, gom theo tháng → ngày).

## Báo cáo khác tài liệu thế nào

| Thư mục | Chứa | Đặc điểm |
|---|---|---|
| `bao-cao/` | Báo cáo: hiện trạng, nghiệm thu, đánh giá, sự cố, tiến độ, kết quả kiểm thử | **Ảnh chụp tại một thời điểm.** Lập xong là không sửa nội dung nữa |
| `plan/` | Kế hoạch | Tài liệu sống, sửa dần theo thời gian |
| `design/` | Thiết kế, hướng dẫn, kịch bản nghiệm thu | Tài liệu sống |
| gốc repo | BA v2, TDD, spec gốc | Tài liệu nền, có thứ tự ưu tiên (xem `CLAUDE.md`) |

Một *kịch bản* nghiệm thu là thiết kế (`design/09-...`). Một *kết quả* chạy kịch bản ấy
vào một ngày cụ thể là báo cáo (`bao-cao/<ngày>/nghiem-thu-...html`).

## Quy ước

```
bao-cao/
├── README.md              ← tệp này
├── index.html             ← mục lục, SINH TỰ ĐỘNG, đừng sửa tay
├── tao-muc-luc.mjs        ← script sinh/kiểm mục lục
├── _mau/bao-cao-mau.html  ← mẫu để chép
└── 2026-11-01/            ← một thư mục cho mỗi ngày có báo cáo (YYYY-MM-DD)
    ├── hien-trang-chuc-nang.html
    └── hien-trang-chuc-nang/   ← (tuỳ chọn) ảnh, tệp đính kèm của báo cáo cùng tên
```

1. **Thư mục ngày** là ngày lập, dạng ISO `YYYY-MM-DD`, để sắp xếp theo tên cũng là
   sắp theo thời gian. Không lồng thêm cấp năm/tháng: mỗi ngày chỉ có vài báo cáo, và
   mục lục đã gom theo tháng.
2. **Tên tệp** viết chữ thường không dấu, nối bằng `-`. Bắt đầu bằng loại báo cáo, sau
   đó là chủ đề: `hien-trang-chuc-nang.html`, `nghiem-thu-dot-b.html`,
   `su-co-cong-8080.html`. Không ghi ngày vào tên tệp vì thư mục đã mang ngày.
3. **Siêu dữ liệu bắt buộc** đặt trong `<head>` (mẫu có sẵn):
   - `<meta name="bao-cao:loai">` nhận một trong các giá trị: `hien-trang` · `nghiem-thu` ·
     `danh-gia` · `su-co` · `tien-do` · `kiem-thu`
   - `<meta name="bao-cao:tom-tat">` là **một câu nêu kết luận**, hiện trên mục lục.
4. **Không sửa nội dung báo cáo cũ.** Khi tình hình thay đổi, lập báo cáo mới ở thư mục
   ngày mới và khai `<meta name="bao-cao:thay-the" content="<ngày>/<tệp>.html">`. Mục
   lục sẽ tự làm mờ bản cũ và trỏ sang bản mới. Lý do: báo cáo cũ là bằng chứng về
   *lúc ấy* hệ thống ra sao. Sửa đè lên nó là mất bằng chứng đó.
   Được phép sửa lỗi chính tả, link hỏng, và siêu dữ liệu.
5. **Bản xuất kèm (`.xlsx`, `.pdf`, `.docx`)** đặt cùng thư mục, **trùng tên** với báo
   cáo HTML: `hien-trang-chuc-nang.html` + `hien-trang-chuc-nang.xlsx`. HTML là bản gốc
   mang siêu dữ liệu; mục lục tự gắn nút tải bản xuất. Một bản xuất không có HTML cùng tên
   sẽ bị `--kiem` từ chối. Số tổng hợp trong Excel phải là **công thức** trên sheet dữ liệu
   (`COUNTIFS`…), không gõ tay, để sửa một dòng trạng thái là số tự cập nhật.
6. **Ghi mốc đối chiếu.** Báo cáo về code phải ghi commit được đối chiếu, và nói rõ cái gì
   *không* được kiểm (ví dụ "không chạy lại test").

## Thêm một báo cáo

```bash
mkdir -p bao-cao/2026-11-15
cp bao-cao/_mau/bao-cao-mau.html bao-cao/2026-11-15/nghiem-thu-dot-c.html
# sửa nội dung + 3 thẻ meta
node bao-cao/tao-muc-luc.mjs          # sinh lại index.html
node bao-cao/tao-muc-luc.mjs --kiem   # kiểm quy ước + mục lục khớp (dùng được trong CI)
```

Script dừng với mã 1 và liệt kê từng lỗi khi có: thư mục không phải ngày, tên tệp sai,
thiếu `title`/`loai`/`tom-tat`, hoặc `thay-the` trỏ tới tệp không tồn tại.
