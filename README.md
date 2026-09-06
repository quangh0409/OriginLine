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
| `truongchi` | `BRANCH_HEAD`, `MEMBER` |
| `thanhvien` | `MEMBER` |

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
│   └── realm-giapha.json           # realm, client, role, user demo
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
| Sửa `realm-giapha.json` mà Keycloak vẫn dùng cấu hình cũ | `docker compose restart keycloak` không đủ nếu realm đã tồn tại trong phiên đang chạy — dùng `docker compose up -d --force-recreate keycloak`. |
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
