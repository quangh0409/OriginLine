# `infra/keycloak/` — realm, giao diện, và bốn công cụ dòng lệnh

Thư mục này giữ mọi thứ thuộc về Keycloak: định nghĩa realm, bộ giao diện
(theme) tiếng Việt, và bốn script Node dùng để dựng hoặc kiểm realm ấy.

## Cái bẫy phải đọc trước khi sửa bất cứ thứ gì ở đây

**Sửa `realm-giapha.json` KHÔNG có tác dụng với một instance đang chạy.**
`--import-realm` bỏ qua realm đã tồn tại, nên tệp ấy chỉ có tác dụng khi dựng
từ một máy sạch. Muốn đổi một realm đang sống thì phải gọi Admin API
(`PUT /admin/realms/giapha`). Chuyện này đã tốn thời gian thật một lần; các
script dưới đây tồn tại một phần chính vì nó.

**Keycloak không đọc biến môi trường lúc nhập realm.** `${env.X:default}`
trong tệp realm luôn cho ra giá trị mặc định. Nên bí mật thật phải đặt bằng
Admin API hoặc bằng biến môi trường của tiến trình backend, không bao giờ
bằng cách sửa tệp realm.

## Bốn script

Cả bốn đều chạy bằng `node`, không cần cài thêm gì.

| Script | Dùng để làm gì | Khi nào cần |
|---|---|---|
| `phat-ma-moi.mjs` | **Phát · thu hồi · đếm lượt dùng mã mời dòng họ.** | Hôm nay đây là lối duy nhất phát mã bằng dòng lệnh. Giao diện `/quan-ly/phat-ma` làm được việc này, nhưng script vẫn cần khi dựng dữ liệu thử hoặc khi chưa có ai đăng nhập được. |
| `kiem-dang-ky.mjs` | **Phép kiểm tĩnh** cho cấu hình đăng ký, máy chủ thư và bộ thông điệp. Trả `0` khi đạt, `1` khi có chỗ trượt. | Sau mỗi lần sửa realm hoặc theme. Vì nó trả mã thoát, nó cắm thẳng vào CI được. |
| `dong-goi-user-profile.mjs` | Nhúng `user-profile-giapha.json` vào `realm-giapha.json` **và canh cho hai bên không trôi khỏi nhau.** | Sau mỗi lần sửa `user-profile-giapha.json`. Bỏ qua bước này thì realm dựng từ máy sạch sẽ thiếu đúng trường mã mời. |
| `do-chieu-cao.mjs` | **Đo thật trên trình duyệt** bốn ràng buộc hình học của màn đăng nhập. | Sau mỗi lần sửa theme. `design/06-dang-nhap` tự khai rằng mọi con số px trong nó là *đề xuất vẽ ra*, không phải số đo; script này là thứ biến chúng thành số đo thật. |

## Vì sao giữ lại cả bốn

Ba script đầu là công cụ vận hành: thiếu chúng thì phát một mã mời, kiểm một
realm, hay dựng lại realm từ máy sạch đều phải làm tay và làm sai được.

`do-chieu-cao.mjs` thì khác — nó đã làm xong việc của mình (các con số đã vào
tài liệu) nên trông như rác. Giữ lại vì nó là **bằng chứng có thể chạy lại**:
lần tới ai đó sửa theme đăng nhập, câu hỏi "có còn vừa ngân sách chiều cao
không" trả lời được bằng một lệnh, thay vì bằng một cuộc tranh luận trên ảnh
chụp màn hình.
