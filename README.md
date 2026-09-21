# Hệ Thống Quản Lý Gia Phả & Cổng Thông Tin Dòng Họ

Nền tảng quản lý gia phả và cổng thông tin cho dòng họ Việt Nam: phả đồ tương tác,
tra cứu **danh xưng** theo văn hoá vùng miền, hồ sơ nhân khẩu đa lớp tên
(húy / tự / hiệu / thụy / thường gọi / pháp danh), và nhắc **giỗ** theo lịch âm.

- Tài liệu nghiệp vụ & thiết kế: `BA_Gia_Pha_Dong_Ho_v2.html` (BA v2.0),
  `TDD_Gia_Pha_Dong_Ho.html` (TDD v1.0), `.claude/plan-giai-doan-1.html` (kế hoạch GĐ1).
- Thứ tự ưu tiên khi tài liệu mâu thuẫn: **TDD → BA → review → spec gốc**.

```
OriginLine/
├── backend/     # Spring Boot 3.x, Java 21, Maven — gốc package vn.giapha
├── frontend/    # Next.js (App Router) + PWA
├── contracts/   # OpenAPI + GraphQL schema dùng chung
└── infra/       # docker-compose môi trường dev + realm Keycloak  ← tài liệu này
```

---

## 1. Yêu cầu cài đặt

| Công cụ | Phiên bản | Ghi chú |
|---|---|---|
| Docker Desktop (hoặc Docker Engine + Compose v2) | Docker 24+, Compose **v2** | Bắt buộc. Lệnh là `docker compose` (có dấu cách), không phải `docker-compose`. |
| JDK | **21** (LTS) | Temurin/Zulu đều được. `java -version` phải ra 21. |
| Maven | 3.9+ | Hoặc dùng `./mvnw` trong `backend/` khi đã có. |
| Node.js | 20 LTS trở lên | Kèm npm/pnpm cho `frontend/`. |
| RAM trống | ~4 GB | 5 container chạy song song. |

Cần khoảng **1.5 GB** để tải image lần đầu.

---

## 2. Khởi động từ máy trắng

```bash
git clone <repo> OriginLine
cd OriginLine/infra

# 1) Tạo file môi trường (giá trị mặc định đã chạy được ngay)
cp .env.example .env          # Windows PowerShell: Copy-Item .env.example .env

# 2) Dựng hạ tầng
docker compose up -d

# 3) Kiểm tra: cột STATUS của postgres/rabbitmq/minio phải là "(healthy)"
docker compose ps
```

Lần đầu mất vài phút để tải image. Container `giapha-minio-init` **thoát ngay sau
khi tạo xong bucket** — trạng thái `Exited (0)` là đúng, không phải lỗi.

Trong script/CI, chờ đúng thời điểm dịch vụ sẵn sàng thay vì `sleep`:

```bash
docker compose up -d --wait postgres rabbitmq redis minio
```

(Chỉ định tên service để không vướng job một lần `minio-init` — tuỳ phiên bản
Compose, `--wait` trần có thể coi container đã thoát là thất bại.)

Dừng và dọn:

```bash
docker compose down       # dừng, GIỮ dữ liệu trong volume
docker compose down -v    # xoá luôn volume — dựng lại DB từ số 0
```

> `docker compose down -v` là cách duy nhất để chạy lại script trong
> `infra/init/` — chúng chỉ chạy khi cluster Postgres được tạo mới.

### Chạy backend (cổng 8080)

```bash
cd backend
./mvnw spring-boot:run          # hoặc: mvn spring-boot:run
# Windows: .\mvnw.cmd spring-boot:run
```

Kiểm tra: <http://localhost:8080/actuator/health> phải trả `{"status":"UP"}`.

### Chạy frontend (cổng 3000)

```bash
cd frontend
npm install
npm run dev
```

Mở <http://localhost:3000>.

---

## 3. Bảng cổng & tài khoản (chỉ dùng cho DEV)

| Service | Image | Cổng host | URL / cách truy cập | Tài khoản |
|---|---|---|---|---|
| **PostgreSQL 16 + Apache AGE** | `apache/age:release_PG16_1.6.0` | `5432` | `jdbc:postgresql://localhost:5432/giapha` | `giapha` / `giapha`, db `giapha` |
| **Redis** | `redis:7.4-alpine` | `6379` | `redis://localhost:6379` | không đặt mật khẩu ở dev |
| **RabbitMQ** | `rabbitmq:4.1-management-alpine` | `5672` (AMQP), `15672` (UI) | <http://localhost:15672> | `giapha` / `giapha` |
| **MinIO** | `minio/minio:RELEASE.2025-04-22T22-12-26Z` | `9000` (S3 API), `9001` (Console) | <http://localhost:9001> | `giapha` / `giapha123` |
| **Keycloak** | `quay.io/keycloak/keycloak:26.7` | `8081` | <http://localhost:8081> | admin console: `admin` / `admin` |
| **Mailpit** (máy chủ thư của máy dev) | `axllent/mailpit:v1.27` | `1025` (SMTP), `8025` (UI) | <http://localhost:8025> | không đặt mật khẩu; nhận mọi thứ |
| Backend (chạy trên host) | — | `8080` | <http://localhost:8080> | — |
| Frontend (chạy trên host) | — | `3000` | <http://localhost:3000> | — |

**Keycloak dùng 8081 chứ không phải 8080** — 8080 đã dành cho backend. Container
nghe thẳng cổng 8081 (`--http-port=8081`) để `issuer` trong JWT khớp đúng
`http://localhost:8081/realms/giapha`, không bị lệch khi đổi port mapping.

> Toàn bộ mật khẩu trên là **rác dành cho dev**, cố tình để yếu. Staging/production
> phải sinh giá trị mới và giữ trong vault; `.env` đã bị `.gitignore` chặn.

### Realm Keycloak `giapha`

Import tự động từ `infra/keycloak/realm-giapha.json` mỗi lần container khởi động.

| Client | Kiểu | Dùng cho |
|---|---|---|
| `giapha-backend` | confidential, không có browser flow (resource server) | Spring Boot xác thực bearer token |
| `giapha-frontend` | public, Authorization Code + PKCE `S256` | Next.js PWA (`http://localhost:3000/*`) |

Realm role: `ADMIN` · `COUNCIL` (Hội đồng Tộc biểu / Tộc trưởng) · `BRANCH_HEAD`
(Trưởng Chi/Ngành) · `MEMBER` · `GUEST`.

User demo (mật khẩu đều là `giapha123`):

| Username | Role |
|---|---|
| `admin.giapha` | `ADMIN`, `COUNCIL` |
| `hoidong` | `COUNCIL`, `MEMBER` |
| `truongchi` | `BRANCH_HEAD`, `MEMBER` |
| `truongchi.at` | `BRANCH_HEAD`, `MEMBER` |
| `thanhvien` | `MEMBER` |
| `chuaduyet` | `MEMBER` (chưa nối với nhân khẩu nào — dùng để thử `ACCOUNT_NOT_PROVISIONED`) |

> `id` của sáu tài khoản này được ghi **cố định** trong `realm-giapha.json`. Đó
> không phải thói quen gọn gàng: backend ánh xạ `keycloak_sub → app_user →
> person`, nên một `id` mới sau mỗi lần dựng lại container sẽ cắt đứt mọi tài
> khoản khỏi nhân khẩu của họ. Thêm user mới thì **cũng phải** đặt `id` cố định.

Lấy nhanh một access token để thử API bằng curl:

```bash
curl -s -X POST http://localhost:8081/realms/giapha/protocol/openid-connect/token \
  -d client_id=giapha-frontend \
  -d grant_type=password \
  -d username=thanhvien \
  -d password=giapha123
```

Lưu ý:

- Keycloak chạy **dev mode với H2 nhúng và không gắn volume**: mọi thay đổi trong
  admin console sẽ mất khi `docker compose down`. Muốn giữ lại thì sửa thẳng vào
  `realm-giapha.json` (đó mới là nguồn chân lý), rồi `down` + `up`.
- Social login (Google, Zalo) chưa cấu hình sẵn vì cần client ID/secret thật —
  thêm ở mục *Identity Providers* của realm khi có, đừng commit secret vào JSON.
- Role của Keycloak chỉ là **một nửa** bài toán phân quyền. Phạm vi theo chi/ngành
  (`ltree` branch path) là chiều phân quyền độc lập, backend tự kiểm — không thể
  suy ra từ role trong token.

### Giao diện đăng nhập của dòng họ — theme `giapha`

Trang đăng nhập **không phải** một tuyến Next.js. Nó là một **theme Keycloak**,
nằm ở `infra/keycloak/themes/giapha/`. Lý do (`design/06-dang-nhap` §3, đường 3):

- **mật khẩu không bao giờ đi qua JavaScript của ta** — biểu mẫu của ta gửi
  thẳng vào luồng Authorization Code của Keycloak, không dùng password grant;
- giữ nguyên chống dò mật khẩu, hành động bắt buộc `UPDATE_PASSWORD`, và
  **chỗ cắm Zalo** sau này qua Identity Provider thay vì SDK nhúng trong trang.

Cái giá, nói trước: **bảng màu và bản dịch nằm ở hai nơi.** `tokens.ts` không với
tới trang Keycloak được. Xem "Phép kiểm chặn trôi lệch" bên dưới.

#### Nạp theme

Hai thứ phải cùng có, thiếu một là **hỏng im lặng**:

1. `docker-compose.yml` gắn thư mục theme vào container:
   ```yaml
   - ./keycloak/themes/giapha:/opt/keycloak/themes/giapha:ro
   ```
2. `realm-giapha.json` khai `"loginTheme": "giapha"`.

Realm trỏ vào một theme **không tồn tại** thì Keycloak lặng lẽ rơi về giao diện
gốc của nó — **không có lỗi nào in ra**, chỉ là màn hình đầu tiên người trong họ
nhìn thấy bỗng không còn là của dòng họ.

```bash
cd infra && docker compose up -d --no-deps keycloak   # sau khi sửa compose/realm
```

`start-dev` **tắt bộ đệm theme**, nên sửa `.ftl`, `.css`, `.properties` là F5
thấy ngay, không phải khởi động lại. Ở chế độ `start` (sản xuất) thì ngược lại —
lúc ấy phải `--spi-theme-cache-themes=false` để dev, hoặc dựng lại container.

> `docker compose up -d keycloak` **tạo lại container**, và Keycloak dev mode giữ
> dữ liệu trong H2 **bên trong container, không có volume**. Mọi thứ tạo tay
> trong admin console sẽ mất; chỉ những gì có trong `realm-giapha.json` sống sót.
> Đổi cấu hình lâu dài thì sửa JSON, đừng sửa admin console.

#### Các trang đã dựng

`parent=base`, tức theme **không** kế thừa CSS của Keycloak. Trang nào chưa dựng
lại sẽ hiện ra dưới dạng HTML trần (vẫn dùng được, chỉ là không có giao diện):

| Tệp | Màn |
|---|---|
| `login.ftl` | Đăng nhập — một ô nhận **cả số điện thoại lẫn email** |
| `login-update-password.ftl` | Đặt mật khẩu mới (`UPDATE_PASSWORD`) |
| `login-reset-password.ftl` | Quên mật khẩu — **đã bật lại**, xem "Máy chủ thư" |
| `register.ftl` | **Đăng ký có ô mã mời dòng họ** |
| `register-commons.ftl` | Khối "điều khoản" (chỉ in khi Hội đồng bật) |
| `login-verify-email.ftl` | Xác minh địa chỉ thư sau khi đăng ký |
| `login-page-expired.ftl` | Lượt đăng nhập quá hạn |
| `error.ftl` · `info.ftl` | Ngõ cụt chung và màn báo tin |
| `template.ftl` | Khuôn chung: bảng tên, công tắc ngôn ngữ, khối "gọi ai" |

Ngoài `login/` còn có **`themes/giapha/email/`** — theme THƯ, `parent=keycloak`
(khác `login/` vốn là `parent=base`). Thư không có bộ CSS nào để cãi nhau, nên kế
thừa ở đó là nhận đủ mọi khuôn thư Keycloak có thể gửi; ta chỉ ghi đè **câu chữ**.
Realm trỏ tới bằng `"emailTheme": "giapha"` — thiếu khoá ấy thì thư vẫn gửi, chỉ
là bằng tiếng của phần mềm, và không có lỗi nào in ra.

Chuỗi tiếng Việt và tiếng Anh ở `themes/giapha/login/messages/`. **Dòng đầu mỗi
tệp phải là `# encoding: UTF-8`** — `java.util.Properties` mặc định đọc
ISO-8859-1, và Keycloak chỉ chuyển sang UTF-8 khi thấy đúng dòng ấy. Xoá nó đi
thì mọi dấu tiếng Việt thành ký tự rác mà trang vẫn chạy.

#### Cấu hình theo từng dòng họ

Sửa `themes/giapha/login/theme.properties`. Mọi khoá **mặc định rỗng**, và rỗng
nghĩa là khối tương ứng **không in ra** — chứ không phải in ra một chỗ trống:

| Khoá | Dùng cho |
|---|---|
| `giaphaClanSubtitle` | Dòng 2 bảng tên: *"Từ đường Đại Lan · Thanh Trì · Hà Nội"* |
| `giaphaClanScale` | Dòng 3: *"14 đời · 1.509 người có tên trong phả"* — **chỉ điền khi có số đếm được thật** |
| `giaphaHelpName` / `giaphaHelpPhone` | Tên và số máy của trưởng chi, in ở mọi ngõ cụt |
| `giaphaGuestUrl` | Lối vào chế độ khách; rỗng thì dùng `client.baseUrl` |
| `giaphaInviteUrl` | Nút "Tôi có mã mời"; rỗng vì tuyến `/moi/[token]` chưa tồn tại |
| `giaphaPublicBrowsingReady` | `false` — xem "Chế độ khách" bên dưới |
| `giaphaPrimaryLocale` | Ngôn ngữ đứng trước trong công tắc; **phải trùng `defaultLocale` của realm** |

Tên dòng họ ở dòng 1 lấy từ `realm.displayName`. Đặt nó là **tên ngắn**
("Phả họ Nguyễn") và để phần còn lại cho `giaphaClanSubtitle` — thanh đầu trang
và bảng tên đều in `displayName`, nên một chuỗi dài sẽ lặp lại hai lần.

#### Đăng ký có kiểm mã mời dòng họ

`registrationAllowed` **đã bật**, và màn đăng ký **bắt buộc có ô mã mời**. Điểm
mấu chốt của checklist §1.3: *mã phải được kiểm TRƯỚC khi tạo tài khoản* — tạo
trước rồi mới hỏi thì một người gõ sai mã vẫn để lại một tài khoản rác, và không
ai dọn.

##### Đường đã chọn, và vì sao

Ô mã mời **không** phải một ô do `register.ftl` nghĩ ra. Nó là một thuộc tính
**User Profile** của realm (`maMoiDongHo`), có validator `pattern`. Điều đó quyết
định tất cả: form action `registration-user-creation` chạy `validate()` cho toàn
bộ thuộc tính **trước**, rồi mới chạy `success()` — mà `success()` mới là chỗ tạo
người dùng.

**Đã đo, không phải suy luận** (Keycloak 26.7.2, đo bằng trình duyệt thật):

| Việc thử | Kết quả |
|---|---|
| Gửi biểu mẫu với mã sai (`XXX-SAI`) | Trang trả về kèm câu "Mã mời này chưa dùng được"; **số tài khoản trong realm giữ nguyên 6 → 6** |
| Gửi với mã đúng, gõ **chữ thường** (`k7m-2qd`) | Tạo 1 tài khoản (6 → 7); mã được lưu lại trên chính tài khoản ấy (`attributes.maMoiDongHo`) |
| Ô đã gõ khi mã sai | **Giữ nguyên** — không bắt gõ lại năm ô |

Ba đường khác đã khảo sát và **loại**, ghi lại để người sau khỏi đi lại:

| Đường | Vì sao loại |
|---|---|
| **Form Action SPI viết bằng Java** | Đây là đường *đúng* về nguyên tắc: Keycloak gọi thẳng API kiểm mã của máy chủ trong `validate()`, nên đủ cả bốn chốt chặn. **Cái giá:** một module Maven mới + JDK 21 (máy dev hiện có `java` 1.8, và image Keycloak chỉ có **JRE**, không có `javac` — đã kiểm), một cổng CI thứ năm, một `.jar` phải dựng lại và khởi động lại Keycloak cho mỗi lần sửa (theme thì F5 là xong), cộng một lời gọi mạng đồng bộ Keycloak → backend với xác thực và ngữ nghĩa hỏng riêng ("backend chết ⇒ không ai đăng ký được"). **Và cái giá nặng nhất:** hợp đồng của API kiểm mã đang được một agent khác dựng *ngay lúc này* — viết SPI hôm nay là đoán hợp đồng của người khác rồi giao một `.jar` gọi vào một điểm cuối có thể không bao giờ tồn tại. |
| **Script Authenticator (JS nạp bằng JAR)** | Không cần biên dịch, nhưng tính năng `scripts` là **preview và đã lỗi thời** từ Keycloak 25, vẫn phải đóng gói `.jar`, và `--features=scripts` là một quyết định vận hành. Một nền móng sắp bị gỡ. |
| **`${env.X}` trong `realm-giapha.json`** | **Không chạy.** Xem khối cảnh báo dưới đây. |

> ⚠️ **`${env.X}` trong tệp realm là một cái bẫy — đã đo trên Keycloak 26.7.2.**
>
> | Viết | Kết quả thật |
> |---|---|
> | `${env.X:mặc định}` | **LUÔN** ra `mặc định` — kể cả khi `X` có thật trong container (kiểm bằng `docker exec … env`) |
> | `${env.X}` | giữ nguyên **nguyên văn** chuỗi `${env.X}` |
>
> Đúng với cả biến có tiền tố `KC_`. Nghĩa là cú pháp ấy *trông như* đọc biến môi
> trường, **không bao giờ đọc**, và lặng lẽ trả về đúng giá trị đã commit.
>
> **Hệ quả cho `giapha-provisioner`:** secret của nó viết
> `"${env.GIAPHA_KEYCLOAK_ADMIN_CLIENT_SECRET:dev-only-provisioner-secret}"`, nên
> giá trị thật đang chạy là `dev-only-provisioner-secret` — **một hằng số trong
> kho**, không phải một bí mật đến từ môi trường. Ở dev thì vô hại (đó vốn là giá
> trị dev), nhưng **staging/production không được tin vào cơ chế này**: đặt
> secret bằng admin console hoặc Admin API.

##### Phát và thu hồi mã — bằng Admin API, không bằng tệp realm

Mẫu trong kho **luôn là `(?!)`** — một regex không bao giờ khớp. Một bản sao kho
chưa cấu hình gì thì **không ai đăng ký được**: đóng sẵn là mặc định đúng cho một
cánh cổng, và `kiem-dang-ky.mjs` **đỏ** nếu có ai commit một mẫu khác (một mã mời
nằm trong git là một mã đã lộ).

```bash
# phát mã — mã đi qua BIẾN MÔI TRƯỜNG, không qua tham số dòng lệnh
GIAPHA_CLAN_INVITE_CODE='K7M-2QD' node infra/keycloak/phat-ma-moi.mjs --phat

node infra/keycloak/phat-ma-moi.mjs --xem       # cửa đang mở hay đóng (không in mã)
node infra/keycloak/phat-ma-moi.mjs --dem       # đã dùng bao nhiêu lượt, ai dùng
node infra/keycloak/phat-ma-moi.mjs --thu-hoi   # đóng cửa ngay
```

Có hiệu lực **ngay**, không phải dựng lại container — nên "đóng cửa khi biết mã đã
lan" mất vài giây chứ không mất phiên của mọi người đang đăng nhập. Script **không
bao giờ in mã**, kể cả ở `--xem`; nó in **dấu vân** (8 ký tự đầu SHA-256), đủ để
hai người xác nhận đang nói về cùng một mã, không đủ để dựng lại mã.

##### Bốn chốt chặn của checklist §1.2 — cái nào đã có, cái nào chưa

| Chốt | Hôm nay |
|---|---|
| **Thu hồi được** | ✅ `--thu-hoi`, tức thì |
| **Đếm lượt dùng** · **ai đã dùng mã nào** | ✅ `--dem`. Mã hợp lệ được lưu lên chính tài khoản vừa tạo (`attributes.maMoiDongHo`), nên không cần bảng nào mới |
| **Có hạn dùng** | ⚠️ **một nửa** — Keycloak không biết ngày tháng. Hôm nay hạn dùng là việc của người vận hành: hẹn lịch chạy `--thu-hoi` |
| **Giới hạn tần suất** | ❌ **chưa có.** Keycloak **không** áp chống dò mật khẩu lên trang đăng ký. Chốt này phải là reCAPTCHA (`registration-recaptcha-action` có sẵn trong luồng, đang `DISABLED`, cần khoá Google) hoặc giới hạn ở tầng proxy |

**Nói thẳng ranh giới:** Keycloak chỉ so mã với một **mẫu**. Nó không hỏi được máy
chủ, nên không biết mã đã hết hạn hay bị thu hồi *theo dữ liệu của máy chủ*, và
không lưu mã dạng băm như mã mời cá nhân đang làm. Bốn chốt đầy đủ chỉ có khi máy
chủ giữ mã và **máy chủ tạo tài khoản** — tức khi API kiểm mã của backend lên, việc
tạo tài khoản nên chuyển về đó (backend đã có sẵn tài khoản dịch vụ
`giapha-provisioner` với quyền `manage-users`, và đã chạy thật cho luồng mời cá
nhân). Lúc ấy mẫu ở đây quay về `(?!)` và trang đăng ký của Keycloak tắt đi.

##### ⚠️ HAI CỬA ĐANG CÙNG MỞ — và phải đóng bớt một cửa

Máy chủ **đã có** đường đăng ký thật: `POST /api/v1/clan-invites/register`
(`ClanInviteController`), kèm `POST /api/v1/clan-invites/lookup` để kiểm mã trước.
Javadoc của nó nói đúng cùng một câu với mục này: *"mã phải được kiểm trước khi
tài khoản được tạo, không phải tạo rồi mới hỏi"*. Và nó giữ **đủ bốn chốt chặn**:
mã lưu dạng **băm**, có hạn, thu hồi được, có bộ đếm, có giới hạn tần suất theo IP.

Trang đăng ký của Keycloak chỉ so mã với một **mẫu**. Vậy vì sao nó vẫn bật?

**Vì hôm nay không có màn Next.js nào gọi tới đường của máy chủ.** Đã kiểm:
`frontend/src/` chỉ gọi nửa **quản trị** của `clan-invites` (`/quan-ly/phat-ma`),
không gọi `/register` lẫn `/lookup`. Tắt trang Keycloak lúc này là đóng cánh cửa
**duy nhất** đang chạy được.

> **Cái giá của việc để hai cửa cùng mở, vì nó không hiện ra ở đâu cả:** tài khoản
> tạo qua trang Keycloak **không** đi qua `ClanInviteRedeemer`, nên nó **không vào
> sổ lượt dùng của máy chủ**. Bộ đếm Hội đồng thấy ở `/quan-ly/phat-ma` sẽ **đếm
> thiếu** — mà đếm thiếu thì đúng cái chốt chặn checklist §1.2 gọi là quan trọng
> nhất (*"không có bộ đếm thì không ai phát hiện được gì"*) mất tác dụng trong im
> lặng. Trong lúc hai cửa cùng mở, phải cộng tay hai con số:
> `phat-ma-moi.mjs --dem` đếm phía Keycloak, màn `/quan-ly/phat-ma` đếm phía máy chủ.

**Ngày màn Next.js ấy có — ba việc, làm CÙNG LÚC:**

1. điền `giaphaRegisterUrl` trong `themes/giapha/login/theme.properties` (liên kết
   "Tôi có mã mời của dòng họ" tự chuyển đích — `login.ftl` đã chừa sẵn đường khâu);
2. đặt `"registrationAllowed": false` trong `realm-giapha.json`;
3. `node infra/keycloak/phat-ma-moi.mjs --thu-hoi`.

Làm thiếu việc 3 thì cửa Keycloak vẫn mở dù không còn liên kết nào trỏ tới —
`/protocol/openid-connect/registrations` là một URL đoán được.

##### Một chỗ hở đã biết, không phải chỗ bị bỏ sót

`usernameExistsMessage` / `emailExistsMessage` **tiết lộ** rằng một số máy hoặc một
địa chỉ thư đã có tài khoản. Keycloak buộc phải từ chối trùng và không giấu được
điều ấy nếu không viết SPI. `design/06-dang-nhap` §7.2 luật 6 cấm để câu lỗi thành
công cụ dò — ở màn *đăng nhập* ta giữ đúng luật ấy (sai mật khẩu và khoá tạm dùng
chung một câu); ở màn *đăng ký* thì không giữ được.

##### Không có ô mật khẩu trên màn đăng ký, và đó là cố ý

Realm bật `verifyEmail`. Khi ấy `RegistrationPassword` của Keycloak **cố ý** không
đặt `passwordRequired`, và javadoc của chính nó giải thích: với `verifyEmail` bật,
mật khẩu đặt **sau** khi địa chỉ thư đã được xác minh — *"this is recommended for
security reasons"*. Cờ `always_set_password_on_register_form` ép quay lại kiểu cũ và
Keycloak ghi thẳng rằng cờ ấy **đã lỗi thời**.

Giữ hành vi mặc định vì với sản phẩm này nó tốt hơn thật: một mã mời rò ra cũng
không tự nó thành một tài khoản sống (phải mở được đúng hộp thư), và màn đăng ký
ngắn đi hai ô. Luồng thật, **đã chạy từ đầu đến cuối**:

```
đăng ký (mã mời + họ tên + email + số ĐT)
   → thư "Xin xác nhận địa chỉ thư"   ← bắt được trong Mailpit
   → bấm liên kết → màn "Đây có đúng là địa chỉ thư của ông/bà không?"
   → "Đặt mật khẩu mới"
   → emailVerified = true
```

> ⚠️ **Thứ tự hành động bắt buộc: số NHỎ chạy TRƯỚC.** Bản đầu đặt `VERIFY_EMAIL`
> ưu tiên 50 trong khi `UPDATE_PASSWORD` là 30, nên Keycloak bắt đặt mật khẩu
> trước, và **lá thư xác minh không bao giờ được gửi**. `VERIFY_EMAIL` phải là
> **10**. `kiem-dang-ky.mjs` ghim đúng bất biến này.

> ⚠️ **`requiredActions` là danh sách TOÀN QUYỀN.** Khai thiếu một mục là gỡ mục ấy
> khỏi realm — lặng lẽ. Đã có tiền lệ: `"requiredActions": []` từng làm
> `UPDATE_PASSWORD` không tồn tại, nên mật khẩu tạm `temporary: true` cũng không ép
> đổi được.

##### Thứ tự các ô trên màn đăng ký đến từ đâu

Từ **`infra/keycloak/user-profile-giapha.json`**, không từ `register.ftl`. Tệp ấy
là nguồn đọc được; `realm-giapha.json` chỉ giữ **bản nhúng** (Keycloak lưu cả cấu
hình User Profile thành *một chuỗi JSON nằm trong một chuỗi JSON khác*, tức một
dòng vài nghìn ký tự đầy dấu nháy thoát mà không ai sửa tay nổi).

```bash
node infra/keycloak/dong-goi-user-profile.mjs          # sinh lại bản nhúng
node infra/keycloak/dong-goi-user-profile.mjs --kiem   # chỉ kiểm, không ghi
```

Cùng khuôn mẫu với biểu mẫu Excel của `dataimport`: một nguồn, một bản sinh, một
phép kiểm để chúng không thể lệch nhau trong im lặng.

#### Máy chủ thư

`resetPasswordAllowed` và `verifyEmail` **đã bật lại**, vì realm giờ có
`smtpServer` **thật**.

##### Máy phát triển — Mailpit bắt thư, không gửi ra ngoài

```bash
cd infra && docker compose up -d mailpit
# xem thư đã gửi: http://localhost:8025
```

Keycloak nối tới `mailpit:1025` **bên trong mạng compose** (không phải
`localhost`). Hai cổng publish chỉ để xem/kiểm từ máy host. Không gắn volume: hộp
thư trống sau mỗi lần dựng lại là thứ giúp phép kiểm "bắt được thư" nói đúng sự
thật.

Điểm mấu chốt: **thư không đi ra Internet**, nên một địa chỉ gõ nhầm trong dữ liệu
thử không thể làm phiền một người thật, và không cần tài khoản SendGrid/SES nào để
chạy luồng đặt lại mật khẩu từ đầu đến cuối.

Đã gửi thử và **bắt được** cả hai lá thư (21-09-2026):

| Thư | Tiêu đề bắt được | Tới |
|---|---|---|
| Xác minh địa chỉ | *Xin xác nhận địa chỉ thư — trang gia phả dòng họ* | `lan@giapha.local` |
| Đặt lại mật khẩu | *Đặt lại mật khẩu cho trang gia phả dòng họ* | `lan@giapha.local` |

Cả hai từ `Gia Pha Dong Ho <khong-tra-loi@giapha.local>`, thân thư có đường dẫn
`action-token` bấm được, và đã bấm thử tới cùng.

> ⚠️ **Tiêu đề thư KHÔNG nhận tham số.** Keycloak định dạng *tiêu đề* bằng một danh
> sách tham số **rỗng**; chỉ *thân* thư mới nhận `{0}`..`{3}`. Bản đầu viết
> `emailVerificationSubject=… — {2}` và thư gửi đi có tiêu đề nguyên văn `… — {2}`
> — bắt được trong Mailpit ngay lượt đầu. Tiêu đề phải đứng vững một mình.

##### Staging / production cần gì

Mailpit **không** dùng cho production — nó cố ý không gửi đi đâu cả. Ở đó cần:

1. **Một máy chủ SMTP thật** trên tên miền của dòng họ, hoặc một dịch vụ gửi thư
   (SES, SendGrid, Mailgun…). Địa chỉ `from` phải là địa chỉ **thật trên tên miền
   ấy** — không thì SPF/DKIM trượt và thư rơi thẳng vào hộp thư rác, tức đúng cái
   bẫy cũ quay lại dưới hình dạng khác: liên kết hiện ra, thư có gửi, và không ai
   nhận được.
2. **`auth: true` + `starttls: true` (hoặc `ssl: true`)**. Dev để `false` vì Mailpit
   nằm trong mạng nội bộ của compose và bắt nó phải có chứng chỉ là dựng một hàng
   rào không bảo vệ gì.
3. **SPF, DKIM, DMARC** trên tên miền. Đây là việc DNS, không phải việc code.
4. **Cấu hình bằng admin console hoặc Admin API**, lấy mật khẩu từ vault.

> **Vì sao bí mật máy chủ thư không nằm trong kho, và cũng không đi vào realm qua
> `.env`:** cú pháp `${env.X}` trong tệp realm **không chạy** (xem khối cảnh báo ở
> mục đăng ký). Nên nếu đặt mật khẩu SMTP vào `realm-giapha.json`, nó sẽ là một
> **mật khẩu thật nằm trong git** — không phải một tham chiếu. `kiem-dang-ky.mjs`
> **đỏ** nếu `smtpServer.user` hoặc `smtpServer.password` có giá trị trong kho.

##### Ngân sách chiều cao sau khi bật "Quên mật khẩu" — đo lại thật

`design/06-dang-nhap` §9 đặt trần: **đáy nút chính cách đáy ô mật khẩu ≤ 120px**,
nếu không nút rơi xuống dưới bàn phím ảo trên điện thoại. Bản trước đo được 112px
khi liên kết "Quên mật khẩu?" còn ẩn, và README này từng dự báo bật lên sẽ thành
156px.

**Đo lại thật, khung 400px, bằng trình duyệt** (`do-chieu-cao.mjs`):

| Bố cục | Đo được | Kết luận |
|---|---|---|
| Liên kết trên **hàng nhãn** ô mật khẩu — *đang dùng* | **112px** | đạt, còn dư 8px |
| Liên kết trên **hàng riêng giữa ô và nút** — đúng chữ của §9 | **164px** | vượt trần (README cũ đoán 156 — thật ra tệ hơn) |
| **Gộp** "Quên mật khẩu?" với "Ghi nhớ đăng nhập" — cách README cũ đề nghị | **168px**, và hàng ấy **xuống dòng thành 100px cao** ở khung 400 | **tệ nhất**; cách chữa cũ không chữa được gì |

Cách đã làm: liên kết lên **cùng hàng với nhãn "Mật khẩu"**, tức nằm *phía trên* ô
mật khẩu. Bàn phím ảo che từ **dưới** lên, nên chiều cao thêm vào ở phía trên không
tính vào ngân sách; liên kết vẫn nằm trong vùng còn thấy được, và vẫn ở đúng chỗ
người dùng nghi ngờ mình quên mật khẩu. **Ngân sách giữ nguyên 112px với liên kết
đã hiện.**

##### Đo thật, không đếm px trên khung dây

```bash
cd infra && docker compose up -d keycloak mailpit
node infra/keycloak/do-chieu-cao.mjs
```

Mở trang **thật** ở khung **400px**, **cả chế độ sáng lẫn tối**, cho **cả màn đăng
nhập lẫn màn đăng ký**, rồi đo bốn thứ bằng `getBoundingClientRect` và
`getComputedStyle`: ngân sách chiều cao · cỡ chữ ≥ 16px · vùng chạm ≥ 44×44px ·
tương phản AA (4.5:1 chữ thường, 3:1 chữ lớn; nền tính bằng cách leo lên tới lớp
không trong suốt đầu tiên). Kết quả 21-09-2026: **14/14 đạt**.

Chế độ tối không phải trang trí: `prefers-color-scheme` do hệ điều hành quyết,
người dùng không bật nó trong sản phẩm này — nên một lỗi tương phản ở chế độ tối là
lỗi mà nửa số người dùng gặp và không ai báo cáo được.

#### Phiên đăng nhập: `ssoSessionIdleTimeout` = 2 giờ

Trước là 1800 (30 phút). Cụ ngồi đọc một trang tiểu sử, đeo kính, lấy cuốn phả
giấy ra đối chiếu — 30 phút là khoảng thời gian bình thường của **một** hồ sơ, và
hết phiên giữa chừng là bị đá ra giữa việc.

`design/06-dang-nhap` §1 đề xuất **8 giờ cho `MEMBER`, giữ 30 phút cho
`COUNCIL`/`ADMIN`** vì hai vai ấy đọc được Tầng 3 và duyệt được đính chính.
**Keycloak không tách timeout theo role được** — `ssoSessionIdleTimeout` là thuộc
tính của realm, và `clientSessionIdleTimeout` tách theo *client*, không theo vai;
cả hai vai dùng chung client `giapha-frontend`.

Nên phải chọn **một con số an toàn cho tài khoản có quyền cao nhất**: **7200 (2
giờ)**. Nó gấp bốn lần thời gian đọc một hồ sơ và phủ được một bữa trưa, nhưng
không phải "cả buổi chiều" như 8 giờ — một máy tính bảng để quên ở nhà từ đường
đóng phiên sau 2 giờ không ai chạm. `ssoSessionMaxLifespan` giữ 36000 (10 giờ) và
`accessTokenLifespan` giữ 900 (15 phút), nên một tài khoản bị khoá mất quyền
trong vòng 15 phút bất kể phiên còn hay hết.

Đổi lại con số này khi: Keycloak có timeout theo vai, **hoặc** `COUNCIL`/`ADMIN`
được tách sang một client riêng — lúc ấy `clientSessionIdleTimeout` hạ riêng cho
client ấy được.

#### Chế độ khách

`giaphaPublicBrowsingReady=true`. Cổng công khai **có thật** và trả dữ liệu thật
cho người **không có token** — mã ở `backend/.../genealogy/api/rest/public_/`
(`PublicTreeController`, `PublicSearchController`, `PublicPersonController`, cộng
`PublicGuestScope`, `PublicVisibilityGuard`, `PublicRateLimitFilter`). Đo
19-09-2026 trên backend cổng 8088, **không kèm Authorization**:

| Lời gọi | Kết quả |
|---|---|
| `GET /api/v1/public/tree` (không tham số) | 200 — mở từ Thuỷ tổ |
| `GET /api/v1/public/tree?depth=2` | 200 — **66 node**, `guestFiltered: true`, **0 người còn sống** |
| `GET /api/v1/public/tree?depth=4` | 200 — 373 node, 0 người còn sống (`truncated: true`) |
| `GET /api/v1/public/tree?depth=5` | **400** — trần `maxTreeDepth = 4` |
| `GET /api/v1/public/persons/search?q=Nguyễn` | 200 — 20 kết quả, 0 người còn sống |
| `GET /api/v1/public/persons/search?q=Nguyen` | 200 — **cùng 20 kết quả** (FTS + `unaccent`) |
| `GET /api/v1/public/persons/{id}` | 200 — `names[]` (HUY/TU/THUY), `generation`, `primaryBranch`, `nativePlace`, `death.solar` **và** `death.lunar` |

**Cái bẫy đã làm tôi kết luận sai một lần, ghi lại để đừng ai lặp:**
`GET /api/v1/persons` và `GET /api/v1/tree` trả **401** cho khách. Điều đó
**đúng thiết kế** — đó là bề mặt của **thành viên**. Cổng công khai là một bề mặt
khác, có `PublicGuestScope` và trần riêng. **401 ở một tuyến không nói gì về
tuyến khác.**

Mỗi dòng trong khối "khách xem được gì" đối chiếu với đúng một lời gọi ở bảng
trên; chú thích trong `messages/messages_vi.properties` ghi rõ dòng nào ứng với
lời gọi nào. **Cố ý không có mục "lịch giỗ chung của dòng họ"** như khung dây
§4.2 vẽ: không có endpoint công khai nào liệt kê sự kiện, ngày giỗ chỉ xem được
trên hồ sơ từng cụ.

**Ranh giới pháp lý do `PublicVisibilityGuard` giữ**, và nó hỏi trực tiếp
`alive == false` trên **từng bản ghi sắp rời khỏi tiến trình**, không tin vào
lớp lọc phía trên. Hồ sơ đơn lẻ của người còn sống ⇒ **404** (không phải 403 —
403 là tự xác nhận người ấy tồn tại); trong danh sách/phả đồ ⇒ **lặng lẽ loại
bỏ**. Vì vậy cột phải của khối viết *"phải là người trong họ mới xem được"* và
**không bao giờ** in một con số kiểu "có N người đang sống bị ẩn" — con số ấy tự
nó là một rò rỉ.

Đặt lại `false` nếu một dòng họ **chọn** đóng cổng công khai. Câu về Nghị định
13/2023 thì **luôn in**, vì nó đúng bất kể cấu hình.

#### Nút "Xem phần công khai" dẫn đi đâu

Dẫn thẳng vào **phả đồ**, không về trang chủ: `giaphaGuestPath={lang}/tree`,
ghép với `client.baseUrl`. `{lang}` thay bằng ngôn ngữ người dùng vừa chọn
**trên chính trang đăng nhập** — ai vừa bấm "English" mà bị ném sang bản tiếng
Việt là mất luôn lựa chọn họ vừa làm. Đã kiểm: bản VI ra
`http://localhost:3000/vi/tree`, bản EN ra `.../en/tree`, cả hai trả 200.

`giaphaGuestUrl` là ghi đè tuyệt đối, chỉ dùng khi cổng công khai nằm ở tên miền
khác.

> ⚠️ **Còn một khoảng hở ở phía frontend, không ở theme.** Bấm nút ấy hôm nay thì
> `/tree` gọi `GET /api/v1/public/tree?rootId=&depth=2` và **nhận 200 với đủ dữ
> liệu**, nhưng trang vẫn vẽ trạng thái rỗng *"Phả đồ bắt đầu từ ai?"* và **0 thẻ**
> trên canvas — giống nhau ở 400px, 1280px và cả khi vào thẳng `/tree`. Khách vẫn
> tới được cây bằng ô tìm kiếm ngay trên đó (tìm công khai chạy tốt), nhưng đó là
> một bước thừa so với lời hứa. Việc này thuộc `frontend/src/app/[locale]/tree/`,
> **không** thuộc theme — theme đã trỏ đúng chỗ.

#### Chỗ cắm Zalo

`identityProviders` đang rỗng. `login.ftl` đã chừa **thứ tự**, không chừa **khoảng
trống**: khối nhà cung cấp nằm giữa vạch "hoặc" và nút "Xem phần công khai", hôm
nay cao đúng **0px**, nên thẻ vẫn cân. Không có nút xám "sắp có" và **không được
thêm** — `design/06-dang-nhap` §4.5 luật 2.

Khi có Zalo OA: thêm một mục vào `identityProviders` của `realm-giapha.json`.
**Không** nhúng SDK Zalo vào trang — làm thế là mất luồng chuẩn, đúng lý do
đường 2 ở §3 bị loại. Và Zalo **không bao giờ là nút đầu tiên**.

#### Ba phép kiểm chặn trôi lệch

Cả ba **không cần Docker, không cần Keycloak chạy, không cần dựng frontend** —
một phép kiểm chỉ chạy được khi cả ngăn xếp đã lên là một phép kiểm không ai chạy.
Nên gắn cả ba vào cổng *frontend static* của `.github/workflows/ci.yml`.

```bash
node infra/keycloak/kiem-mau-theme.mjs                 # màu + ba cái sàn
node infra/keycloak/kiem-dang-ky.mjs                   # đăng ký · thư · thông điệp
node infra/keycloak/dong-goi-user-profile.mjs --kiem   # bản nhúng User Profile
```

`kiem-dang-ky.mjs` giữ bảy bất biến, và **mỗi cái ứng với một lỗi đã thật sự xảy
ra ở đây**:

1. `registrationAllowed: true` mà User Profile không có `maMoiDongHo` **bắt buộc +
   có `pattern`** → cổng đang mở toang, mà nhìn thì y hệt lúc chạy đúng.
2. Mẫu `maMoiDongHo` trong kho khác `(?!)` → một mã mời nằm trong git là một mã đã lộ.
3. `resetPasswordAllowed`/`verifyEmail` bật mà `smtpServer` thiếu `host`/`from`, hoặc
   `smtpServer.user`/`password` có giá trị **trong kho**.
4. `smtpServer` còn dùng cú pháp `${env....}` — cú pháp không chạy (xem trên).
5. `requiredActions` khai thiếu, hoặc `VERIFY_EMAIL` có ưu tiên **≥** `UPDATE_PASSWORD`.
6. Khoá thông điệp bị trỏ hụt: `user-profile-giapha.json` trỏ `${giaphaRegPhone}`
   mà bộ thông điệp không có → màn hình in ra nguyên văn cái khoá làm nhãn ô. Kiểm
   ở **cả hai** ngôn ngữ, và kiểm cả việc hai ngôn ngữ phủ cùng một tập khoá.
7. Dòng đầu mỗi `messages_*.properties` không đúng `# encoding: UTF-8`, hoặc có dấu
   nháy đơn **không nhân đôi** (`MessageFormat` nuốt im lặng một `'` đứng một mình
   — bản nháp đầu từng in "Each ancestors record").

Đã thử ngược: sửa hỏng năm chỗ cùng lúc thì nó bắt đủ năm.

```bash
node infra/keycloak/kiem-mau-theme.mjs
```

Đọc `frontend/src/styles/tokens.ts` và `themes/giapha/login/resources/css/giapha.css`
rồi đối chiếu **từng kênh màu**, cộng ba cái sàn (chữ ≥ 16px, vùng chạm ≥ 44px,
ô nhập/nút ≥ 44px). Không cần Docker, không cần Keycloak chạy, không cần dựng
frontend — **`tokens.ts` luôn thắng**, theme chỉ là bản chép.

Nên gắn vào cổng *frontend static* của `.github/workflows/ci.yml`. Không có phép
kiểm này thì sau sáu tháng hai bên lệch màu, và người dùng nhìn thấy sự lệch ấy
đúng vào giây họ cần tin tưởng nhất.

#### Bốn chỗ bản thiết kế KHÔNG hiện thực được bằng theme Keycloak

(Danh sách này **từng có mục thứ năm** — "chế độ khách chưa có hàng". Mục ấy
**sai** và đã gỡ: cổng công khai có thật, xem mục Chế độ khách ở trên.)

Ghi lại để người sau khỏi đi tìm lại, và để chủ dự án biết mình đang nhận gì:

1. **Thời hạn phiên theo vai.** `design/06-dang-nhap` §1 muốn 8 giờ cho `MEMBER`
   và 30 phút cho `COUNCIL`/`ADMIN`. Keycloak chỉ có `ssoSessionIdleTimeout` ở
   cấp **realm**; `clientSessionIdleTimeout` tách theo *client*, không theo vai.
   Đang lấy một con số chung là 7200. Xem mục phiên đăng nhập ở trên.

2. **"Bị khoá tạm" không phân biệt được với "sai mật khẩu".** Keycloak cố ý trả
   cùng một câu (`accountTemporarilyDisabledMessage` = `invalidUserMessage`) để
   không ai dò được số nào là người trong họ, và ta **giữ nguyên** vì
   `design/06-dang-nhap` §7.2 luật 6 cấm đúng điều đó. Trạng thái lỗi số 2 ở §7
   vì vậy không có màn riêng — bù bằng khối hổ phách in luật khoá tạm cho **mọi**
   ca lỗi, nên không lộ ca nào đang xảy ra.

3. **Màn "Đặt mật khẩu mới" không hiện được tên tài khoản.** §5.2 khung 2 vẽ
   *"Số để đăng nhập từ nay — đã điền sẵn"*. Keycloak **không đưa `username` hay
   `auth.attemptedUsername` vào ngữ cảnh** của `login-update-password.ftl` (đã
   thử, rỗng). Khối ấy đã viết sẵn trong `login-update-password.ftl` và bọc
   `<#if (auth.attemptedUsername)??>` nên sẽ tự hiện ra nếu bản Keycloak sau này
   cấp biến ấy. Muốn có ngay thì phải viết một Authenticator SPI.

4. **Ba trạng thái lỗi còn lại thuộc về Next.js, không thuộc theme.** §7 liệt kê
   năm trạng thái; theme này phủ được **hai** (sai mật khẩu / khoá tạm) cộng
   trang "lượt đăng nhập quá hạn". `ACCOUNT_NOT_ACTIVE`, `ACCOUNT_NOT_PROVISIONED`
   và "phiên hết hạn giữa chừng" nổ ra **sau khi** đăng nhập thành công, ở tầng
   ứng dụng — Keycloak không bao giờ nhìn thấy chúng. Chúng cần các tuyến
   `/tai-khoan/chua-noi`, `/tai-khoan/cho-duyet` và một hộp thoại tại chỗ trong
   `frontend/`, đúng như §3 phân ranh giới.

**Một chỗ lệch có chủ ý so với danh mục kiểm §11:** mục #4 đòi điểm dừng Tab đầu
tiên là "Bỏ qua, tới nội dung chính". Ô định danh có `autofocus`, nên tiêu điểm
**bắt đầu ngay ở ô ấy** — cụ mở trang ra là gõ được luôn, không phải Tab lần nào.
Liên kết "Bỏ qua" vẫn là phần tử đầu trong DOM và vẫn tới được bằng Shift+Tab, và
trang **không có `tabindex` dương nào** (bản gốc của Keycloak dùng `tabindex="1"`
đến `"8"` — đã bỏ hết). Nếu Hội đồng muốn đúng chữ của §11 #4 thì bỏ `autofocus`;
đánh đổi là cụ phải tự chạm vào ô trước khi gõ.

#### Bộ chữ

`themes/giapha/login/resources/fonts/` chứa sẵn Be Vietnam Pro (400/500/600/700)
và Noto Serif 700, hai tập con `vietnamese` + `latin`, tổng ~175 KB. **Không gọi
Google Fonts lúc chạy**: trang đăng nhập thường mở trên mạng ở quê, và một lượt
tải bị chặn làm chữ nhảy cỡ đúng lúc người dùng đang gõ. `fonts.css` được **sinh
ra** từ `fonts.googleapis.com/css2?...` rồi viết lại đường dẫn — muốn đổi bộ chữ
thì sinh lại, đừng sửa tay.

**Cấm hạ weight xuống 300**: 00 §3 cấm chữ mảnh cho nội dung đọc, vì dấu tiếng
Việt chồng tầng (ắ ộ ữ ể ỡ) biến mất ở cỡ nhỏ.

### Bucket MinIO

Container `minio-init` chạy một lần lúc `up` và tạo sẵn:

| Bucket | Nội dung |
|---|---|
| `giapha-portraits` | Ảnh chân dung nhân khẩu |
| `giapha-stele` | Thác bản / ảnh văn bia |
| `giapha-graves` | Ảnh mộ phần |
| `giapha-documents` | Gia phả giấy scan, tài liệu dòng họ |

Tất cả đều **private** (`mc anonymous set none`). Ảnh người còn sống là dữ liệu
Tầng 3 theo Nghị định 13/2023 — backend phát **presigned URL** có hạn, không bao
giờ mở anonymous read cho bucket. Media **không bao giờ** lưu blob trong Postgres.

### Khoá Web Push (VAPID)

Thiếu cặp khoá này thì công tắc *Thông báo đẩy* trong phần Cài đặt báo
`WEBPUSH_NOT_CONFIGURED` và không bật được — phần còn lại của hệ thống chạy bình
thường, mọi lời nhắc giỗ vẫn vào trung tâm thông báo.

**Vì sao trong kho mã không có khoá nào.** Khoá riêng VAPID là *quyền đẩy thông
báo tới điện thoại thật của người trong dòng họ*. Thứ gì đã vào lịch sử Git thì ở
đó vĩnh viễn: xoá ở commit sau không xoá được nội dung ở commit trước, và mọi bản
clone đều đã có. Vì vậy khoá **chỉ** vào hệ thống qua hai biến môi trường
`GIAPHA_WEBPUSH_PUBLIC_KEY` / `GIAPHA_WEBPUSH_PRIVATE_KEY`, không có mặc định
trong mã, không có dòng nào trong `application.yml`, và không có endpoint nào
nhận khoá qua HTTP (nhận qua HTTP thì máy chủ phải lưu nó xuống một chỗ nào đó,
và chỗ đó sẽ theo bản sao lưu đi khắp nơi). `WebPushPropertiesTest` canh đúng
điều này và sẽ đỏ nếu khoá riêng xuất hiện trong tệp cấu hình hoặc trong tệp mẫu.

**Mỗi môi trường một cặp khoá riêng** (dev / staging / production). Dùng chung
nghĩa là một bản dev chạy trên laptop có thể đẩy thông báo tới điện thoại thật.

**1) Sinh khoá** — bằng công cụ của chính dự án, không thêm phụ thuộc nào:

```bash
cd backend
./mvnw -q compile        # Windows: .\mvnw.cmd -q compile
java -cp target/classes vn.giapha.notification.infrastructure.webpush.VapidKeyGenerator
```

In ra đúng hai dòng: khoá công khai (87 ký tự, luôn bắt đầu bằng `B`) và khoá
riêng (43 ký tự), đều là base64url không đệm của đường cong P-256. Công cụ in ra
`System.out` **chứ không dùng logger**, có chủ đích: mọi thứ qua logger đều chảy
vào tệp log rồi lên Loki, và một khoá riêng nằm trong kho log tập trung là đã lộ.

**2) Nạp vào tiến trình backend.** Mẫu và hướng dẫn đầy đủ:
[`infra/webpush-dev.example.txt`](infra/webpush-dev.example.txt) — tệp mẫu chỉ
chứa chỗ dành sẵn; giữ bản có khoá thật **ngoài** thư mục kho mã.

```powershell
$env:GIAPHA_WEBPUSH_PUBLIC_KEY  = "<khoá công khai>"
$env:GIAPHA_WEBPUSH_PRIVATE_KEY = "<khoá riêng>"
cd backend; .\mvnw.cmd spring-boot:run
```

Khoá được đọc **một lần lúc khởi động**, nên đặt biến cho một tiến trình đang
chạy không có tác dụng gì — phải khởi động lại.

**3) Kiểm tra** (cần token vai `ADMIN`):

```bash
curl -s -H "Authorization: Bearer $TOKEN"   http://localhost:8080/api/v1/admin/notifications/webpush
# -> {"ready":true, ..., "remediation":[]}
```

Endpoint này luôn trả `200`: *chưa cấu hình* là một **câu trả lời**, không phải
một lỗi. Khi `ready=false` nó nói rõ thiếu khoá nào (hoặc kênh bị tắt tay) và
liệt kê các bước phải làm — đó là chỗ để quản trị viên xử lý khi thành viên báo
lỗi. `GET /api/v1/push/public-key` là đường của người dùng cuối: `200` kèm khoá
khi đã sẵn sàng, `422 WEBPUSH_NOT_CONFIGURED` khi chưa.

**Cấu hình sai thì kêu to, không im lặng** — hai ca đã được gài chốt:

- **Dán nhầm nửa cặp khoá** (công khai của lần sinh này, riêng của lần kia): cả
  hai chuỗi đều đúng độ dài và đúng base64url, nên trước đây ứng dụng khởi động
  sạch rồi mọi lượt gửi nhận `401`. Nay khoá được **tự kiểm "cùng một cặp"** lúc
  khởi động (ký một chuỗi rồi tự xác minh) và ứng dụng **không khởi động** nếu
  lệch.
- **Push service từ chối** (`400/401/403`, gần như luôn là VAPID sai): ghi
  `ERROR` và kết thúc ở `FAILED`, **không phải** `SKIPPED`. `SKIPPED` là kết quả
  bình thường mà không ai đi soi — từng có giai đoạn mọi thông báo đẩy biến mất
  mà không con số nào trong `notification_log` báo động.

**Đổi khoá là mất toàn bộ đăng ký hiện có:** trình duyệt gắn subscription với
đúng khoá công khai lúc `subscribe()`, nên mọi bản ghi trong `push_subscription`
sẽ bị trả `403` và người dùng phải bật lại công tắc. Sinh một lần rồi giữ.

### Bí mật tài khoản dịch vụ Keycloak (lập tài khoản cho người được mời)

Đây là **bí mật thứ hai** của hệ thống, và nó đi theo đúng khuôn mẫu của khoá
VAPID ở trên: **chỉ** đến từ biến môi trường
`GIAPHA_KEYCLOAK_ADMIN_CLIENT_SECRET`, không có mặc định trong mã, không có dòng
nào trong `application.yml`. `KeycloakAdminPropertiesTest` canh điều này và sẽ đỏ
nếu bí mật xuất hiện trong tệp cấu hình, trong tệp mẫu, hay trong
`realm-giapha.json`.

Mẫu và hướng dẫn đầy đủ:
[`infra/keycloak-admin-dev.example.txt`](infra/keycloak-admin-dev.example.txt).

**Vì sao cần nó.** Realm đặt `registrationAllowed: false` — cố ý, và đề xuất giữ
vĩnh viễn: người ta không tự ghi danh rồi vào xem phả nhà người khác. Nhưng hệ
quả là **không ai lập được tài khoản cho cụ bà vừa nhận tờ phiếu mời**. Vì vậy
`POST /api/v1/invitations/accept` tự lập tài khoản Keycloak cho người được mời và
trả về một liên kết một lần để chính họ đặt mật khẩu. Không có bí mật này thì
đường ấy trả `503 IDENTITY_PROVIDER_UNAVAILABLE` — **và mã mời không bị tiêu**,
nên bấm lại được sau khi quản trị viên cấu hình xong.

**Quyền tối thiểu: đúng một vai client** — `realm-management:manage-users`, gán
cho tài khoản dịch vụ của client `giapha-provisioner`, **không** phải
`realm-admin` (vai ấy gộp cả `manage-realm`, `manage-clients`,
`manage-identity-providers`, `impersonation` — một bí mật rò ra sẽ thành quyền
quản trị cả realm). Client đặt `fullScopeAllowed: false` và có **scope mapping
tường minh** tới đúng vai ấy, nên token của nó không bao giờ mang thêm vai nào kể
cả khi sau này có người lỡ gán thêm cho tài khoản dịch vụ.

```bash
# Kiem chung: chi manage-users, khong gi hon
T=$(curl -s -X POST http://localhost:8081/realms/giapha/protocol/openid-connect/token \
      -d "grant_type=client_credentials&client_id=giapha-provisioner&client_secret=$SECRET" \
    | jq -r .access_token)
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $T" \
     "http://localhost:8081/admin/realms/giapha/users?email=x@y.z"   # -> 200
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $T" \
     "http://localhost:8081/admin/realms/giapha/clients"             # -> 403
```

**Realm đã nhập rồi thì `--import-realm` KHÔNG nhập lại**, nên client mới trong
`infra/keycloak/realm-giapha.json` sẽ không tự xuất hiện trên một Keycloak đang
chạy. Tệp mẫu ở trên có sẵn các lệnh `kcadm.sh` để tạo client, gán vai và gán
scope mapping trên một realm đang sống.

---

## 4. ⚠️ Bẫy Apache AGE — phải đọc trước khi gõ Cypher

**AGE cần được nạp và có `ag_catalog` trong `search_path` trên MỖI connection.**
Quên bước này thì câu Cypher sẽ fail rời rạc với thông báo chẳng liên quan gì tới
nguyên nhân thật:

```
ERROR:  function cypher(unknown, unknown) does not exist
ERROR:  type "agtype" does not exist
```

Đây là lỗi cấu hình phiên, không phải lỗi cú pháp câu truy vấn — rất tốn thời gian
nếu không biết trước.

### Khi gõ `psql` / DBeaver / pgAdmin bằng tay

Chạy hai dòng này **trước** mọi câu Cypher, mỗi lần mở phiên mới:

```sql
LOAD 'age';
SET search_path = ag_catalog, "$user", public;
```

Script `infra/init/01-giapha-extensions.sql` đã đặt sẵn `search_path` mặc định ở
cấp database (`ALTER DATABASE giapha SET search_path = ag_catalog, "$user", public`),
nên phần lớn công cụ sẽ chạy được ngay. **Đừng dựa vào đó**: nếu ai đó tạo database
mới, restore từ dump, hay đổi user, thiết lập này không đi theo.

### Phía backend (Spring Boot)

Cấu hình ở Hikari để mọi connection trong pool đều được khởi tạo:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/giapha
    username: giapha
    password: giapha
    hikari:
      connection-init-sql: "LOAD 'age'; SET search_path = ag_catalog, \"$user\", public;"
```

Bỏ dòng `connection-init-sql` thì triệu chứng đặc trưng là: **một số** request cây
phả đồ chạy được, số khác lỗi — tuỳ connection nào trong pool được lấy ra. Đừng đi
tìm bug trong tầng domain khi thấy triệu chứng này.

Hai điểm liên quan:

- `agtype` **không map được qua JPA/Hibernate**. Truy cập AGE bằng `JdbcTemplate`
  với native Cypher, parse thủ công, và luôn **tham số hoá** — không nối chuỗi vào
  câu Cypher.
- Image `apache/age` khởi động Postgres với `shared_preload_libraries=age`, nên
  `LOAD 'age'` thường đã là no-op. Vẫn giữ nó: nó vô hại, và nó là thứ duy nhất cứu
  bạn trên một Postgres không preload (ví dụ instance quản trị hoặc bản cài tay).
  **Đừng ghi đè `command:` của service `postgres`** trong compose — ghi đè mà quên
  `shared_preload_libraries=age` là hỏng toàn bộ tầng graph.

### Tự kiểm tra AGE hoạt động

Script sẵn có, tạo graph tạm `smoke_test` rồi xoá (không đụng `giapha_graph`):

```bash
docker exec -i giapha-postgres \
  psql -U giapha -d giapha -v ON_ERROR_STOP=1 -f /scripts/age-smoke-test.sql
```

Kỳ vọng dòng cuối: `>>> AGE OK: Cypher chay duoc trong Postgres.`

Hoặc gõ tay một câu Cypher tối thiểu:

```bash
docker exec -it giapha-postgres psql -U giapha -d giapha
```

```sql
LOAD 'age';
SET search_path = ag_catalog, "$user", public;

SELECT create_graph('smoke_test');

SELECT * FROM cypher('smoke_test', $$
  CREATE (cha:Person {ten: 'Thuy To', doi: 1})-[:CHA_CON]->(con:Person {ten: 'Doi 2', doi: 2})
  RETURN con.ten
$$) AS (ten agtype);

SELECT * FROM cypher('smoke_test', $$
  MATCH (a:Person)-[:CHA_CON]->(b:Person) RETURN a.ten, b.ten
$$) AS (cha agtype, con agtype);

SELECT drop_graph('smoke_test', true);
```

Kiểm tra extension đã cài:

```bash
docker exec -i giapha-postgres psql -U giapha -d giapha \
  -c "SELECT extname, extversion FROM pg_extension ORDER BY extname;"
```

Phải thấy đủ: `age`, `ltree`, `pg_trgm`, `pgcrypto`, `plpgsql`, `unaccent`.

---

## 5. Thư mục `infra/`

```
infra/
├── docker-compose.yml              # 5 service + 1 job tạo bucket MinIO
├── .env.example                    # mọi biến môi trường, kèm chú thích
├── init/
│   └── 01-giapha-extensions.sql    # extension + search_path, chỉ chạy lúc initdb
├── keycloak/
│   ├── realm-giapha.json           # realm, client, role, user demo
│   ├── kiem-mau-theme.mjs          # phép kiểm chặn trôi lệch theme ↔ tokens.ts
│   └── themes/giapha/login/        # giao diện đăng nhập của dòng họ
│       ├── theme.properties        # cấu hình theo từng dòng họ
│       ├── *.ftl                   # login · update-password · reset · error · info
│       ├── messages/               # chuỗi VI–EN (đầu tệp: # encoding: UTF-8)
│       └── resources/{css,js,fonts}/
└── scripts/
    └── age-smoke-test.sql          # kiểm tra Cypher (mount vào /scripts)
```

**Ranh giới trách nhiệm:** `infra/init/` chỉ làm phần bắt buộc ở tầng image
(extension + `search_path`). Toàn bộ schema — bảng, index, graph `giapha_graph`,
seed bộ luật danh xưng — thuộc về **Flyway** trong `backend/src/main/resources/db/migration`.
Mọi lệnh ở `init/` đều `IF NOT EXISTS` để chạy chồng với Flyway mà không vỡ.

Script trong `init/` được mount **từng file** vào `/docker-entrypoint-initdb.d/`,
cố ý không mount cả thư mục: image `apache/age` đã có sẵn
`00-create-extension-age.sql` ở đó, mount đè cả thư mục sẽ che mất nó.

---

## 6. Sự cố thường gặp

| Triệu chứng | Nguyên nhân & cách xử lý |
|---|---|
| `bind: address already in use` khi `up` | Cổng đã bị chiếm (hay gặp nhất: Postgres cài sẵn trên máy chiếm 5432). Đổi `POSTGRES_PORT` trong `.env` rồi `up` lại. |
| `function cypher(...) does not exist` | Thiếu `LOAD 'age'` / `search_path` — xem §4. |
| Sửa file trong `infra/init/` mà không thấy tác dụng | Script chỉ chạy khi cluster mới. `docker compose down -v` rồi `up` lại. |
| Sửa `realm-giapha.json` mà Keycloak vẫn dùng cấu hình cũ | `docker compose restart keycloak` không đủ nếu realm đã tồn tại trong phiên đang chạy — dùng `docker compose up -d --force-recreate keycloak`. **Chú ý:** việc này xoá sạch mọi thứ tạo tay trong admin console (H2 nằm trong container, không có volume). Kiểm lại danh sách user sau khi dựng lại. |
| Trang đăng nhập hiện ra là **giao diện gốc của Keycloak**, không phải của dòng họ | Realm trỏ `loginTheme` vào một theme Keycloak không tìm thấy, và nó **rơi về mặc định mà không in lỗi nào**. Kiểm hai thứ: `"loginTheme": "giapha"` trong realm, và mount `./keycloak/themes/giapha:/opt/keycloak/themes/giapha:ro` trong `docker-compose.yml`. Xác nhận nhanh: `docker exec giapha-keycloak ls /opt/keycloak/themes`. |
| Trang đăng nhập trả **HTTP 500**, nhật ký nói `coerceModelToTextualCommon` | Lỗi FreeMarker trong theme. `docker logs giapha-keycloak` in đúng tên tệp và số dòng — tìm chuỗi `FTL stack trace`. Hay gặp nhất: `<#assign x><#nested ...></#assign>` trả về markup chứ không phải chuỗi, nên `?trim` / `?upper_case` ném lỗi. |
| Sửa tệp trong `themes/giapha/` mà không thấy đổi | Ở `start-dev` bộ đệm theme đã tắt, F5 là thấy — nếu không thấy thì mount chưa vào. Ở chế độ `start` thì phải dựng lại container. |
| Dấu tiếng Việt trong theme thành ký tự rác | Tệp `messages_*.properties` thiếu dòng đầu `# encoding: UTF-8`. `java.util.Properties` mặc định đọc ISO-8859-1. |
| Postgres `healthy` nhưng backend vẫn không kết nối được | Đang trong `start_period` của healthcheck hoặc container còn chạy initdb. Xem `docker compose logs -f postgres`. |
| MinIO không khởi động | `MINIO_ROOT_PASSWORD` ngắn hơn 8 ký tự. |
| RabbitMQ mất rất lâu mới `healthy` | Bình thường ở lần khởi động đầu (`start_period` 60s). Xem `docker compose logs -f rabbitmq`. |
| Console MinIO trống trơn / thiếu chức năng | Bản community từ giữa 2025 đã cắt bớt Console. Compose đang ghim `RELEASE.2025-04-22T22-12-26Z` — bản cuối còn object browser đầy đủ. Nâng tag lên thì mất giao diện duyệt file, dùng `mc` thay thế. |
| `FATAL: password authentication failed for user "giapha"` **dù mật khẩu đúng** | Máy đã có một **PostgreSQL cài native** chiếm cổng 5432 (kiểm tra: `Get-Service *postgres*`, hoặc `Get-NetTCPConnection -LocalPort 5432 -State Listen`). Backend đang nói chuyện với Postgres đó, không phải container. Xem §6.1. |
| `FATAL: invalid value for parameter "TimeZone": "Asia/Saigon"` | JVM trên Windows locale VN gửi bí danh cũ; Postgres chỉ nhận tên IANA. Chạy backend với `-Duser.timezone=Asia/Ho_Chi_Minh`. Xem §6.2. |
| `Schema-validation: missing table [branch]` lúc khởi động | Va chạm giữa AGE và Hibernate về "schema mặc định". Đã xử lý bằng `hibernate.default_schema: public` trong `application.yml` — **đừng gỡ**. Xem §6.3. |
| `Parameter 1 of method apiSecurityFilterChain required a single bean, but 2 were found` | Từ Spring 6, `mvcHandlerMappingIntrospector` cũng implement `CorsConfigurationSource`. Đã xử lý bằng `@Qualifier("corsConfigurationSource")` trong `SecurityConfig` — **đừng gỡ**. |
| `mvn` báo hàng loạt `illegal start of expression` ở `switch` | Đang chạy bằng JDK 8. Dự án cần **JDK 21**. Không phải lỗi code. |
| Frontend: `Could not find the module "…#SomeComponent" in the React Client Manifest` | Cache `.next` cũ, không phải lỗi code. Xem §6.4. |

### 6.4 Frontend: manifest client component cũ trong `.next`

Thêm một client component mới trong lúc `next dev` đang chạy có thể để lại manifest lỗi thời
trong `.next`. Trang dùng component đó chết bằng một **server-side exception**, thông báo lại đổ
lỗi cho bundler:

```
Error: Could not find the module
  "D:\OriginLine\frontend\src\components\...\x.tsx#X" in the React Client Manifest.
  This is probably a bug in the React Server Components bundler.
Application error: a server-side exception has occurred
```

Component vẫn có `"use client"` đầy đủ — đọc code sẽ không thấy gì sai. Cách xử lý:

```bash
cd frontend
# dừng mọi dev server đang giữ .next trước, nếu không Windows sẽ khoá file
rm -rf .next && npm run dev:mock
```

Đáng chú ý vì nó **giả dạng lỗi nghiệp vụ**: khi gặp lần đầu, 5 bài kiểm thử quyền riêng tư của
khách cùng đỏ một lúc, trông y như phân tầng hiển thị bị hỏng. Dọn cache là xanh cả 9/9. Nếu một
loạt test trên cùng một route cùng đỏ mà mã nguồn không đổi, hãy nghi cache trước khi nghi logic.

### 6.1 Postgres native chiếm cổng — bẫy tốn thời gian nhất

Trên Windows, khi đã có sẵn service `postgresql-x64-NN`, Docker Desktop **bind cổng 5432 mà
không báo lỗi** `address already in use`. Kết quả: `docker compose ps` xanh, `docker port`
hiển thị `5432 -> 0.0.0.0:5432`, nhưng mọi kết nối từ host lại rơi vào Postgres native —
nơi không có role `giapha`. Triệu chứng đội lốt thành **lỗi sai mật khẩu**, và người ta đi
sửa `.env` hàng giờ mà không ra.

Cách nhận diện dứt điểm — nếu lệnh này chạy được mà kết nối từ host vẫn hỏng, thủ phạm chắc
chắn là cổng chứ không phải mật khẩu:

```bash
MSYS_NO_PATHCONV=1 docker exec -i giapha-postgres \
  psql "postgresql://giapha:giapha@127.0.0.1:5432/giapha" -c "select current_user"
```

Ba cách xử lý, chọn một:

```bash
# a) Đổi cổng container, không đụng service native (khuyến nghị)
#    Sửa POSTGRES_PORT trong .env, rồi:
docker compose up -d --force-recreate --no-deps postgres
#    Backend chạy với DB_URL=jdbc:postgresql://localhost:<cổng mới>/giapha

# b) Tắt service native (cần quyền admin, ảnh hưởng dự án khác của bạn)
#    Stop-Service postgresql-x64-18

# c) Gỡ hẳn Postgres native nếu không dùng tới
```

`MSYS_NO_PATHCONV=1` là bắt buộc khi `docker exec` có đường dẫn kiểu Unix: Git Bash sẽ biến
`/scripts/x.sql` thành `C:/Program Files/Git/scripts/x.sql` và báo "No such file or directory".

### 6.2 Múi giờ: `Asia/Saigon` vs `Asia/Ho_Chi_Minh`

Driver JDBC gửi múi giờ mặc định của JVM sang Postgres ngay khi mở connection. Trên Windows
đặt locale Việt Nam, JVM phân giải ra bí danh cũ `Asia/Saigon`, còn tzdata của Postgres chỉ
có `Asia/Ho_Chi_Minh` → **Flyway chết trước cả khi chạy migration đầu tiên**. Luôn khởi động
backend với `-Duser.timezone=Asia/Ho_Chi_Minh` (hoặc đặt `TZ` trong môi trường).

Cùng loại bẫy, cùng chỗ chữa: khi chạy bằng `java -cp` trần cũng phải thêm
**`-Dfile.encoding=UTF-8`**. Thiếu nó thì dữ liệu đọc từ CSDL vẫn đúng dấu, nhưng **mọi thông
điệp lỗi tiếng Việt trong RFC 7807 bị vỡ**:

```
"title": "Thiáº¿u tham sá»‘"     ← thiếu cờ
"title": "Thiếu tham số"                                  ← có cờ
```

Đây **không** phải lỗi cấu hình `MessageSource` — `WebConfig.messageSource()` đã khai
`setDefaultEncoding("UTF-8")` và `application.yml` cũng đặt `spring.messages.encoding`. Vỡ ở
tầng ghi phản hồi HTTP. `mvnw`/`spring-boot:run` truyền sẵn cờ này qua `MAVEN_OPTS`, nên lỗi chỉ
lộ ra khi chạy JVM trực tiếp — đúng kiểu bẫy chỉ gặp lúc bàn giao.

### 6.3 AGE và Hibernate tranh nhau "schema mặc định"

`connection-init-sql` buộc phải đặt `ag_catalog` **đầu** `search_path` để Cypher chạy. Nhưng
Hibernate suy schema mặc định từ đúng phần tử đầu tiên đó, nên khi `ddl-auto=validate` nó đi
tìm `ag_catalog.branch` và chết lúc khởi động — trong khi bảng vẫn nằm đúng ở `public`.

`application.yml` đã ghim `spring.jpa.properties.hibernate.default_schema: public`. **Đừng
sửa thứ tự `search_path` để né lỗi này** — đó là thứ tầng graph phụ thuộc vào.

Lưu ý phụ: Flyway cũng lấy `ag_catalog` làm schema của nó, nên bảng `flyway_schema_history`
nằm trong `ag_catalog` chứ không phải `public`. Các migration đều tự `SET search_path` nên
bảng nghiệp vụ vẫn vào `public` đúng ý đồ.

### 6.5 JDK 21 biến mất giữa chừng — build hỏng hàng loạt

`java` trên PATH của máy dev hiện là **1.8.0_202** và `JAVA_HOME` cũng trỏ vào đó, nên
`mvnw` đổ ra một bức tường lỗi `illegal start of expression` ngay tại các biểu thức
`switch` của Java 21. **Đó là sự cố toolchain, không phải lỗi code** — đừng đi sửa code
theo các lỗi này.

Cách đúng là cài Temurin/Zulu 21 rồi trỏ lại `JAVA_HOME`. Khi chưa cài được, dự án từng
dùng một bản JDK 21 + Maven giải nén trong thư mục scratchpad tạm. **Cách này không bền:**
Windows dọn `%TEMP%` theo lịch và đã một lần rút ruột thư mục JDK — chỉ còn `lib/modules`,
thiếu `lib/jvm.cfg`, cho lỗi khó đoán:

```
Error: could not open `...\tools\jdk-21.0.12.1+1\lib\jvm.cfg'
```

Gặp lỗi đó thì giải nén lại từ hai file zip gốc (`jdk21.zip`, `maven.zip`) — chúng nằm cạnh
thư mục đã hỏng và thường sống sót lâu hơn phần đã giải nén. Kiểm chứng bằng
`<jdk>/bin/java -version` phải in `21.x` trước khi tin bất kỳ kết quả build nào.

Khi nhiều tiến trình cùng build `backend/`, chúng dùng chung một thư mục `target/`; hai
`javac` ghi đè cùng file `.class` sẽ đẻ ra `NoClassDefFoundError` ngẫu nhiên không tái hiện
được. Hãy tuần tự hoá bằng một mutex `mkdir` (Git Bash trên Windows không có `flock`).

Xem log:

```bash
docker compose logs -f postgres     # hoặc redis / rabbitmq / minio / keycloak
docker compose ps                   # trạng thái + healthcheck
```

---

## 7. CI — bốn cổng kiểm trên GitHub Actions

Workflow: [`.github/workflows/ci.yml`](.github/workflows/ci.yml). Chạy trên **mọi pull
request** và trên **push vào `main`**. Job `Tổng kết` gom kết quả bốn cổng thành **một
check duy nhất** — đặt check đó làm *required* cho nhánh `main`.

Vì sao cổng 3 (`next build`) đứng riêng dù không ca kiểm nào chạy trên nó: bản dựng
production đã hỏng suốt nhiều đợt mà không ai biết — thiếu `<Suspense>` quanh
`useSearchParams()` trong một thành phần nằm trên **mọi** trang. Toàn bộ E2E chạy trên
`next dev`, nên không lượt nào chạm tới `next build`. Thông báo lỗi khi đó còn trỏ vào
một **trang vô can**; hãy đọc tên **thành phần** trong vết lỗi, đừng đọc tên trang.

### 7.1 Bốn cổng và lệnh thật của từng cổng

| Cổng | Kiểm gì | Lệnh CI chạy | Chạy lại y hệt ở máy cá nhân |
|---|---|---|---|
| **1 · Backend** | `mvn test` trên JDK 21, gồm ~50–150 ca tích hợp chạm **PostgreSQL 16 + Apache AGE thật** qua Testcontainers | `cd backend && ./mvnw -B -ntp test` rồi `node .github/ci/check-surefire.mjs backend/target/surefire-reports` | Cần JDK 21 thật (xem §6.5) và Docker đang chạy: `cd backend && ./mvnw -B -ntp test` |
| **2 · Frontend tĩnh** | kiểu, lint, và toàn bộ vitest — đối chiếu với danh sách ca đỏ có chủ ý | `npm run typecheck` · `npm run lint` · `npx vitest run --reporter=default --reporter=json --outputFile=../vitest-results.json` rồi `node .github/ci/check-vitest.mjs vitest-results.json .github/ci/vitest-expected-failures.json` | `cd frontend && npm run typecheck && npm run lint && npm run test` |
| **3 · `next build`** | bản dựng production biên dịch và prerender được | `cd frontend && npm run build` | `cd frontend && npm run build` |
| **4 · E2E** | Playwright, **tách từng tệp spec** qua `matrix` (10 việc song song) | `cd frontend && npx playwright install --with-deps chromium` rồi mỗi việc một lệnh `npx playwright test e2e/<tệp>.spec.ts` | `cd frontend && npx playwright test e2e/tree-canvas.spec.ts` (lặp cho từng tệp; **đừng** chạy cả 134 ca một lượt — xem §7.4) |

`~/.m2` được cache bằng `actions/setup-java` (`cache: maven`); `node_modules` của
`frontend/` được cache theo băm của `package-lock.json` trong action dùng chung
[`.github/actions/frontend-deps`](.github/actions/frontend-deps/action.yml), nên `npm ci`
chỉ chạy khi lockfile đổi.

### 7.2 Cổng nào cũng phải chứng minh là nó có chạy thật

Hai kịch bản "xanh mà chẳng kiểm gì" đã được chặn tường minh:

- **Backend.** Mọi lớp `*IT` gắn `@EnabledIf(...dockerAvailable)`. Runner không thấy
  Docker thì JUnit **bỏ qua chúng trong im lặng** và `mvn test` vẫn trả về 0.
  `check-surefire.mjs` đọc `target/surefire-reports/TEST-*.xml` và bắt đỏ nếu có lớp
  tích hợp bị bỏ qua toàn bộ, hoặc nếu tổng số ca tụt dưới `BACKEND_MIN_TESTS`.
- **Frontend.** `check-vitest.mjs` bắt đỏ nếu tổng số ca tụt dưới `soCaToiThieu`.

`BACKEND_MIN_TESTS` hiện là **1800** vì `mvn test` trên commit `e34f1ba` chạy đúng
**1827 ca**. Ngưỡng phải nằm **dưới** mốc thật của `main`, nếu không CI đỏ ngay lượt đầu
và không ai còn tin nó. Khi nhánh `dataimport` (đưa con số lên ~2185) vào `main`, hãy nâng
ngưỡng lên khoảng 2100.

### 7.3 Bốn ca vitest đỏ CÓ CHỦ Ý

Bốn ca này là **lời nhắc đang chờ Hội đồng Tộc biểu quyết**, không phải nợ kỹ thuật:
hai ca về sắc huy hiệu **Dâu/Rể** (`tests/unit/a11y/no-hardcoded-colors.test.ts`) và hai
ca về tương phản của token `accent` (`tests/unit/a11y/token-contrast.test.ts`). Cả hai đều
là câu hỏi về **nghĩa** — dâu/rể có được một sắc riêng ngoài bảng màu dòng họ hay không,
và có đổi sắc hổ phách của dòng họ hay không — nên mã nguồn không được tự quyết.

Cách xử lý: chúng được khai báo đích danh trong
[`.github/ci/vitest-expected-failures.json`](.github/ci/vitest-expected-failures.json),
kèm lý do và **điều kiện gỡ bỏ**. `check-vitest.mjs` đòi tập ca đỏ khớp **chính xác**
danh sách đó:

| Tình huống | Kết quả cổng |
|---|---|
| đúng bốn ca ấy đỏ | **xanh** |
| có ca đỏ **ngoài** danh sách | **đỏ** — hồi quy thật, phải sửa mã nguồn |
| một ca trong danh sách **đã xanh** trở lại | **đỏ** — Hội đồng đã quyết, hãy xoá mục đó khỏi danh sách |
| một ca trong danh sách **không còn tồn tại** (đổi tên/xoá) | **đỏ** — lời nhắc đang mất hiệu lực |

Hai cách làm bị loại bỏ có chủ ý: để CI đỏ vĩnh viễn (vài tuần sau không ai nhìn màu nữa,
và ca đỏ thứ năm — ca thật — lọt qua), và `it.skip` (ca kiểm biến mất khỏi báo cáo, không
còn gì nhắc người sửa quay lại khi Hội đồng quyết xong).

### 7.4 E2E: hai ràng buộc đã đo được, đừng đụng vào

- **Tách từng tệp spec.** Chạy cả 134 ca trong **một** tiến trình thì `next dev` phình
  tới **~2,9 GB** và hệ điều hành giết lượt chạy. Tách theo tệp, dọn tiến trình node giữa
  các lượt, thì cả 9 tệp đều xanh. Runner GitHub có ~7 GB nên *có thể* dư hơn máy dev —
  `matrix` theo tệp để không phải đặt cược vào điều đó, và tiện chạy song song.
- **KHÔNG chạy E2E trên bản dựng production.** Service worker của PWA giành mất phạm vi
  mà MSW cần, và **92/134 ca sẽ đỏ vì không có dữ liệu**. Xem `disable:` của `next-pwa`
  trong `frontend/next.config.js`. `webServer` trong `playwright.config.ts` gọi
  `npm run dev:mock` — giữ nguyên.
- `e2e/real-auth/` **không** nằm trong matrix: nó cần Keycloak + backend + PostgreSQL
  sống và dùng `playwright.real.config.ts`. Chạy tay khi cần.

### 7.5 Bí mật

Không có giá trị bí mật nào nằm trong workflow. Khi cần thêm, dùng `secrets.*` của
GitHub, không ghi thẳng vào YAML và không đọc từ `.env` của repo.
