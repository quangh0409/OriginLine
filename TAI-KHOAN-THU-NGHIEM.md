# Tài khoản thử nghiệm

Sáu tài khoản trên Keycloak realm `giapha`, dựng cho việc nghiệm thu trên **máy phát triển**.

> **Mật khẩu chung: `giapha123`** — mật khẩu rác cố ý, chỉ dùng cho máy phát triển.
> Môi trường thật phải dùng mật khẩu lấy từ kho bí mật, không bao giờ dùng lại bộ này.

Đăng nhập tại <http://localhost:3000>.

## Sáu tài khoản

| Tài khoản | Vai Keycloak | Phạm vi chi | Gắn nhân khẩu | Dựng để thử điều gì |
|---|---|---|---|---|
| `thanhvien` | `MEMBER` | Chi Bính | có | Thành viên thường: đọc theo phạm vi, sửa hồ sơ của mình, gửi đề nghị đính chính |
| `truongchi` | `BRANCH_HEAD` + `MEMBER` | **Chi Bính** | có | Quyền ghi trong đúng một chi |
| `truongchi.at` | `BRANCH_HEAD` + `MEMBER` | **Chi Ất** | có | **Ranh giới phạm vi** — phải có hai trưởng chi ở hai chi khác nhau mới thử được |
| `hoidong` | `COUNCIL` + `MEMBER` | toàn họ | có | **Chức danh dòng họ, KHÔNG phải quản trị kỹ thuật** |
| `admin.giapha` | `ADMIN` + `COUNCIL` | toàn họ | có | Quản trị kỹ thuật (đang gộp cả hai vai — xem ghi chú dưới) |
| `chuaduyet` | `MEMBER` | — | **không** | Ca lỗi có thật: `ACCOUNT_NOT_PROVISIONED` |

### Vì sao `hoidong` phải tách khỏi `admin.giapha`

Dự án phân biệt **chức danh dòng họ** (Tộc trưởng, Hội đồng Tộc biểu — theo huyết thống)
với **vai quản trị kỹ thuật**. Một người có thể giữ cả hai, nhưng chúng là hai thứ khác nhau.

`admin.giapha` mang cả `ADMIN` lẫn `COUNCIL`, nên nó **không thử được** những quyết định
dành riêng cho Hội đồng — ví dụ câu hỏi đang chờ Hội đồng Tộc biểu chốt: *Hội đồng có được
đọc số điện thoại của người đã chọn mức "Riêng tư" không?* Muốn nghiệm thu câu ấy thì
phải dùng `hoidong`, không phải `admin.giapha`.

### Vì sao `chuaduyet` cố ý không gắn nhân khẩu

Đây là trạng thái có thật: người mới được mời vào, đã có tài khoản Keycloak, nhưng trưởng
chi chưa gắn họ với một nhân khẩu trong phả. Họ **đăng nhập được**, và mọi chức năng cần
phạm vi chi trả **403 `ACCOUNT_NOT_PROVISIONED`**. Giao diện phải xử lý cho tử tế —
đây là một ca nghiệm thu riêng, không phải một lỗi.

## Phạm vi chi — đã đo trên hệ thống chạy thật

`GET /api/v1/import/branches`, cột `canImport`:

| | Chi Ất | Chi Bính | Chi Đinh | Chi Giáp | Cả họ |
|---|---|---|---|---|---|
| `truongchi` | ✗ | **✓** | ✗ | ✗ | ✗ |
| `truongchi.at` | **✓** | ✗ | ✗ | ✗ | ✗ |
| `hoidong` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `thanhvien` | ✗ | ✗ | ✗ | ✗ | ✗ |
| `chuaduyet` | \- | \- | \- | \- | **403** |

## Lấy token để gọi API tay

```bash
curl -s -X POST "http://localhost:8081/realms/giapha/protocol/openid-connect/token" \
  -d "client_id=giapha-frontend" -d "grant_type=password" \
  -d "username=truongchi" -d "password=giapha123"
```

## Dữ liệu trong cơ sở dữ liệu phát triển

1.506 nhân khẩu (627 còn sống), 4 chi × 3 ngành. Một `rootId` hợp lệ ở đời 1:
`3b54e802-a966-5570-b69b-3d01b808b0e3`.

## Cổng — máy này có nhiều thứ đụng nhau

| Dịch vụ | Cổng | Ghi chú |
|---|---|---|
| Frontend | 3000 | |
| Backend | **8088** | 8080 bị Apache `httpd` của máy chiếm |
| Keycloak | 8081 | admin console `admin` / `admin` |
| PostgreSQL | **55432** | 5432 bị một PostgreSQL cài sẵn trên máy chiếm — nếu để 5432 thì ứng dụng nối vào **nhầm cơ sở dữ liệu** và báo sai mật khẩu |

Chạy backend phải ép múi giờ: JVM lấy `Asia/Saigon` từ Windows và PostgreSQL **từ chối
kết nối** với bí danh đó.

```
-Duser.timezone=Asia/Ho_Chi_Minh -Dfile.encoding=UTF-8 -Dserver.port=8088
DB_URL=jdbc:postgresql://localhost:55432/giapha
```

## Dựng lại bộ tài khoản này từ đầu

Ba tài khoản `thanhvien` / `truongchi` / `admin.giapha` nằm trong
`infra/keycloak/realm-giapha.json` và tự nạp khi Keycloak khởi động lần đầu.

Ba tài khoản `hoidong` / `truongchi.at` / `chuaduyet` được thêm sau qua Admin API, và
**chưa nằm trong tệp realm** — xoá volume Keycloak là mất. Nếu bộ này còn dùng lâu dài
thì nên đưa cả ba vào tệp realm, kèm hàng `app_user` và `branch_assignment` tương ứng
(`hoidong` → `COUNCIL` phạm vi toàn họ; `truongchi.at` → `BRANCH_HEAD` + `MEMBER` phạm vi
`ho_nguyen_dinh.chi_at`; `chuaduyet` → **không** có hàng `app_user`, đó là điểm mấu chốt).
