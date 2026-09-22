# Tài khoản thử nghiệm

Sáu tài khoản trên Keycloak realm `giapha`, dựng cho việc nghiệm thu trên **máy phát triển**.

> **Mật khẩu chung: `giapha123`** — mật khẩu rác cố ý, chỉ dùng cho máy phát triển.
> Môi trường thật phải dùng mật khẩu lấy từ kho bí mật, không bao giờ dùng lại bộ này.

Đăng nhập tại <http://localhost:3000>.

## Sáu tài khoản

| Tài khoản | Vai Keycloak | Phạm vi quản trị | Nhân khẩu thuộc | Dựng để thử điều gì |
|---|---|---|---|---|
| `thanhvien` | `MEMBER` | — (không quản trị gì) | `chi_binh.nganh_ut` | Thành viên thường: đọc theo phạm vi, sửa hồ sơ của mình, gửi đề nghị đính chính |
| `truongchi` | `BRANCH_HEAD` + `MEMBER` | **`chi_binh`** | `chi_binh.nganh_thu` | Quyền ghi trong đúng một chi |
| `truongchi.at` | `BRANCH_HEAD` + `MEMBER` | **`chi_at`** | Ngành Trưởng | **Ranh giới phạm vi** — phải có hai trưởng chi ở hai chi khác nhau mới thử được |
| `hoidong` | `COUNCIL` + `MEMBER` | toàn họ | Ngành Trưởng | **Chức danh dòng họ, KHÔNG phải quản trị kỹ thuật** |
| `admin.giapha` | `ADMIN` + `COUNCIL` | toàn họ | Ngành Trưởng | Quản trị kỹ thuật (đang gộp cả hai vai — xem ghi chú dưới) |
| `chuaduyet` | `MEMBER` | — | **không có** | Ca lỗi có thật: `ACCOUNT_NOT_PROVISIONED` |

### Hai cột ấy là hai thứ khác nhau — đừng gộp lại

Bảng này từng có **một** cột tên "Phạm vi chi", trộn *phạm vi quản trị* (một người được
duyệt/ghi ở đâu) với *nơi nhân khẩu của chính họ đứng* (người ấy là ai trong phả). Việc gộp
ấy đã suýt làm hỏng một kịch bản nghiệm thu: nhìn vào "cùng Chi Bính" thì tưởng hai người
không phân biệt được, nhìn vào "khác ngành" thì tưởng họ không thấy nhau. **Cả hai cách đọc
đều sai.**

Tra trên CSDL đang chạy (23/09/2026):

```
truongchi   BRANCH_HEAD  phạm vi: ho_nguyen_dinh.chi_binh
thanhvien   MEMBER       phạm vi: —
nhân khẩu của truongchi:  ho_nguyen_dinh.chi_binh.nganh_thu
nhân khẩu của thanhvien:  ho_nguyen_dinh.chi_binh.nganh_ut
```

Ngành nằm **trong** chi, nên **phạm vi quản trị của `truongchi` CHỨA nhân khẩu của
`thanhvien`**. Ai dựng kịch bản riêng tư dựa trên câu "hai người khác ngành nên không thấy
nhau" sẽ có một ca kiểm **xanh vì lý do sai**, và nó sẽ **đỏ oan** ngay khi ai đó đổi một
công tắc từ `CLAN` sang `BRANCH`.

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

> **Đang nghiệm thu mà thấy `409` thay vì `403`? Đừng báo là lỗi của tệp này.**
> `403` là mã **đã chốt** (chủ dự án quyết ngày 22/09/2026 — lập luận đầy đủ ở
> `design/06-dang-nhap` §7.1): hợp đồng `contracts/openapi.yaml` ghi `403`, và
> `BranchScopeGuard` cùng `ImportScopeGuard` đã trả `403` kèm test ghim. Nhưng
> `GlobalExceptionHandler.accountNotProvisioned()` còn trả `409` và đang được sửa, nên
> **hôm nay cùng một mã lỗi có thể tới bằng hai mã HTTP tuỳ đường gọi**.
>
> Giao diện không bị ảnh hưởng: nó rẽ nhánh theo trường `code`, không theo mã HTTP — đúng
> lời khuyên của `design/06` §7.1, và đó chính là thứ giữ cho màn hình đúng suốt đợt sửa.
> Khi gọi API tay thì so `code`, đừng so mã HTTP.

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

**1.506 nhân khẩu chưa xoá mềm** (627 còn sống), 4 chi × 3 ngành — **17** chi/ngành chưa
xoá. Một `rootId` hợp lệ ở đời 1: `3b54e802-a966-5570-b69b-3d01b808b0e3`.

Ba câu đếm, đo cùng ngày 23/09/2026:

```sql
SELECT count(*) FROM person;                                     -- 1509  (dòng thô)
SELECT count(*) FROM person WHERE is_deleted = FALSE;            -- 1506  ← con số có nghĩa
SELECT count(*) FROM person WHERE is_alive = TRUE
                              AND is_deleted = FALSE;            --  627
```

> **Vì sao phải ghi cả định nghĩa, không chỉ con số.** Chênh lệch **3** giữa 1.509 và 1.506
> là các bản ghi **đã xoá mềm**: xoá mềm là luật tuyệt đối của dự án này — không bao giờ
> xoá cứng một nhân khẩu, vì làm thế là cắt liên kết của cả cái cây — nên hàng đã xoá vẫn
> nằm trong bảng mà **không ai nhìn thấy**.
>
> Đếm bằng `count(*)` trần rồi thấy lệch 3 so với tài liệu và kết luận "tài liệu cũ" là một
> cái bẫy có thật: nó đã xảy ra đúng một lần trong ngày viết dòng này. Có định nghĩa đứng
> cạnh con số thì lần sau không ai "sửa" nhầm nữa.
>
> **Cả ba con số đều là số đo, không phải hằng số** — chúng đổi mỗi khi có ai duyệt một đơn
> "Tôi chưa có trong phả", hoặc ghi một lô nhập liệu. Tra lại trước mỗi buổi nghiệm thu thay
> vì tin vào dòng này.

## Nhân khẩu của bốn tài khoản có gắn phả

Tra trên CSDL đang chạy ngày 23/09/2026. **Cả bốn đều là người còn sống** — cần biết điều
này trước khi dựng bất kỳ kịch bản nào về quyền riêng tư, vì người đã khuất đi một nhánh
lọc hoàn toàn khác.

**Cả bốn đều là người còn sống.** Cần biết trước khi dựng bất kỳ kịch bản nào về quyền riêng
tư: người đã khuất đi một nhánh lọc hoàn toàn khác (`deceased` được xét **trước** mọi mức
chia sẻ), nên một ca kiểm dựng nhầm trên người đã khuất sẽ không chứng minh được gì.

**Cặp dùng để thử công tắc chia sẻ** (xem `design/09` — NT-TV-15): vinh danh khai cho
`thanhvien`, xem bằng `truongchi`.

Lý do cặp này đúng **không phải** vì hai người khác ngành — như vừa nói ở trên, phạm vi của
`truongchi` có chứa nhân khẩu của `thanhvien`. Lý do thật nằm ở `PersonVisibility.allows()`:

```java
if (self || clanWide) return true;   // chỉ ADMIN / COUNCIL
...
case PRIVATE -> false;               // HONOUR mặc định ở đây
case BRANCH  -> inBranchScope;
case CLAN    -> true;
```

Nhóm trường vinh danh **mặc định `PRIVATE`**, và `PRIVATE` trả `false` cho **mọi người** trừ
chính chủ và vai toàn họ. `truongchi` là `BRANCH_HEAD` — **không** phải `clanWide` — nên bị
chặn **kể cả khi phạm vi của anh ấy chứa nhân khẩu kia**. Đó mới là điều ca kiểm chứng minh.

Hai hệ quả phải nhớ khi sửa kịch bản ấy:

- **Đừng thay `truongchi` bằng `hoidong` hay `admin.giapha`**: hai vai đó có phạm vi toàn họ
  nên luôn đọc được, ca kiểm mất hết ý nghĩa mà vẫn xanh.
- **Đừng đổi bước "đặt sang `Cả họ xem`" thành `Cùng chi`**: ở mức `BRANCH`, `truongchi`
  *sẽ* thấy — vì phạm vi có chứa — và ca kiểm sẽ đỏ oan trong khi mã chạy đúng.

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
