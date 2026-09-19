# `contracts/` — Nguồn chân lý chung của Backend và Frontend

**Đây là hợp đồng API. Không phải tài liệu tham khảo, không phải bản nháp.**
Backend hiện thực đúng theo nó; Frontend mock đúng theo nó. Chỗ nào lệch nhau, **file trong thư
mục này thắng** — và bên nào lệch thì bên đó sửa.

> **This directory is the shared source of truth for both backend and frontend.**
> Any change here must be announced to *both* sides before it is merged.

| File | Nội dung |
|---|---|
| `openapi.yaml` | OpenAPI 3.1 — toàn bộ REST API dưới `/api/v1`. |
| `schema.graphqls` | GraphQL SDL — truy vấn cây lồng sâu, client tự chọn độ sâu. Chỉ Query, không Mutation. |
| `README.md` | File này — quy tắc dùng và sửa contract. |

---

## 1. Quy tắc vàng: đổi contract phải báo cả hai bên

Contract được chốt ở **Sprint 1** để FE không phải ngồi chờ BE bốn tuần. Giá trị đó chỉ còn
khi hai bên thật sự tin vào cùng một file.

**Khi cần đổi:**

1. **Nói ra trước khi sửa.** Đăng lên kênh chung: đổi gì, vì sao, ảnh hưởng ai.
2. **Sửa file ở đây trước**, code sau. Đừng sửa code rồi mới "cập nhật cho khớp" — làm ngược
   thứ tự thì contract thành bản chép lại của implementation, mất hết tác dụng.
3. **Đánh dấu loại thay đổi:**
   * *Tương thích ngược* — thêm trường tuỳ chọn, thêm giá trị enum ở phản hồi, thêm endpoint.
     Báo là đủ.
   * *Phá vỡ* — đổi tên/xoá trường, đổi kiểu, đổi mã lỗi, đổi ngữ nghĩa, siết một trường thành
     bắt buộc. **Phải có sự đồng ý của cả BE và FE**, và phải ghi vào mục "Nhật ký thay đổi" dưới đây.
4. **Enum là danh sách đóng.** Thêm một giá trị `ProblemCode` hoặc `EventType` mới là thay đổi
   contract, vì FE có thể đang `switch` trên nó.

**Không được phép:**

* BE trả một trường không có trong contract và coi là "bonus" — FE sẽ vô tình phụ thuộc vào nó.
* FE mock một trường chưa có trong contract rồi giả định BE sẽ làm.
* Sửa contract "cho khớp code đang có" mà không báo ai.

---

## 2. Dùng thế nào

### Frontend

```bash
# Sinh type TypeScript từ OpenAPI
npx openapi-typescript contracts/openapi.yaml -o frontend/src/types/api.d.ts

# Sinh type + hook từ GraphQL SDL (graphql-codegen)
npx graphql-codegen --schema contracts/schema.graphqls
```

Mock bằng **MSW** theo đúng schema này. Mock phải phản ánh cả **các trường hợp khó**, không chỉ
đường hạnh phúc — nếu không, tuần tích hợp sẽ là tuần vỡ trận:

* `409 KY_HUY_CONFLICT` khi thêm nhân khẩu trùng tên húy bậc trên.
* Hồ sơ người còn sống ở Tầng 1 (**thiếu hẳn** `birth`, `occupation`, `contact` — không phải `null`).
* `/tree` trả `meta.truncated = true` với `truncatedNodeIds` không rỗng.
* `/kinship` trả `status = NO_MATCHING_RULE`, `title` vắng mặt.
* `404` cho người còn sống khi chưa đăng nhập.

### Backend

Contract này là **spec-first**: viết controller cho khớp, đừng để springdoc tự sinh rồi coi kết
quả sinh ra là chuẩn. Nên có một test so khớp OpenAPI do runtime sinh với file này, để lệch là
biết ngay ở CI chứ không phải ở tuần tích hợp.

### Kiểm tra cú pháp

```bash
npx --yes @redocly/cli lint contracts/openapi.yaml            # đã chạy: valid, 2 warning vô hại
node -e "require('graphql').buildSchema(require('fs').readFileSync('contracts/schema.graphqls','utf8'))"
```

---

## 3. Quy ước xuyên suốt

| Chủ đề | Quy ước |
|---|---|
| Versioning | URL, `/api/v1`. |
| Lỗi | RFC 7807 Problem Details, `application/problem+json`. **FE phân nhánh theo `code`**, không theo `detail`. |
| Phân trang | Offset thống nhất: query `page` (0-based) + `size`; phản hồi `{ items, page }`. GraphQL dùng đúng kiểu này, **không** dùng Relay cursor. |
| Định danh | UUID chuỗi. |
| Thời điểm | ISO-8601 có offset. Múi giờ nghiệp vụ **GMT+7**. |
| Ngày sinh/mất | **Song lịch** — `{ solar, lunar, precision }`. `death.lunar` là **nguồn chân lý tính giỗ**. |
| Quy đổi âm–dương | **Chỉ backend** (Hồ Ngọc Đức, GMT+7, xử lý tháng nhuận). FE không tự tính. |
| Ngôn ngữ | `Accept-Language: vi \| en`. **Danh xưng luôn tiếng Việt** — là dữ liệu nghiệp vụ, không phải chuỗi i18n. |
| Xoá | Nhân khẩu **chỉ xoá mềm**. Không có API xoá cứng. Xoá dữ liệu cá nhân hợp pháp = **ẩn danh hoá**, giữ node phả hệ. |
| Đồng thời | `ETag` + `If-Match` khi `PATCH`; lệch phiên bản ⇒ `409 OPTIMISTIC_LOCK_CONFLICT`. |

### Phân tầng riêng tư — điều dễ làm sai nhất

Cùng một `PersonDto`, **khác tập trường** theo vai người gọi và `isAlive`:

* Trường bị ẩn được **loại khỏi JSON** (REST) — không `null`, không `""`, không `"***"`.
  GraphQL trả `null` vì không có khái niệm trường vắng mặt; ngữ nghĩa vẫn như nhau.
* Ẩn vì thiếu quyền và ẩn vì không có dữ liệu **cố ý không phân biệt được**.
* FE **không render placeholder** kiểu "dữ liệu bị ẩn" — làm thế là tự tay tiết lộ điều mà
  việc lọc đang cố giấu.
* Khách hỏi người còn sống ⇒ **`404`**, không phải `403`. Trả `403` là xác nhận người đó tồn tại.
* Vì vậy cây mà Khách thấy **có thể đứt đoạn** — FE phải render được cây có lỗ, đừng coi cạnh
  thiếu là lỗi dữ liệu.

---

## 4. Danh mục endpoint

| Method | Đường dẫn | Mô tả | Quyền tối thiểu |
|---|---|---|---|
| POST | `/persons` | Thêm nhân khẩu · kiểm tra **kỵ húy** hai bước | `BRANCH_HEAD` trong scope |
| GET | `/persons/{id}` | Hồ sơ, đã lọc phân tầng | Khách (chỉ người đã khuất) |
| PATCH | `/persons/{id}` | Sửa · `If-Match` bắt buộc · `clearFields` để xoá trắng | `MEMBER` (hồ sơ của mình) |
| DELETE | `/persons/{id}` | **Xoá mềm** | `BRANCH_HEAD` trong scope |
| GET | `/persons/search?q=` | Tìm **không dấu**, mọi lớp tên | Khách |
| GET | `/tree?rootId=&depth=` | Projection cây phẳng, lazy theo depth, có `ETag` | Khách |
| GET | `/kinship?from=&to=` | **Danh xưng** + đường quan hệ qua LCA | Khách |
| GET | `/kinship-rules` | Bộ luật thô hoặc bộ **hiệu lực đã hợp nhất** | `MEMBER` |
| PUT | `/kinship-rules` | Ghi đè trọn bộ luật | **`COUNCIL`** (Hội đồng Tộc biểu) |
| GET | `/events` | Giỗ / chạp mả / lễ, kèm ngày dương lần tới | Khách |
| GET | `/notifications` | Hộp thư in-app + `unreadCount` | `MEMBER` |
| POST | `/notifications/{id}/read` | Đánh dấu đã đọc (idempotent) | `MEMBER` |
| GET | `/push/public-key` | Khoá công khai VAPID cho `PushManager.subscribe` | Khách |
| POST | `/push/subscriptions` | Đăng ký Web Push (idempotent theo `endpoint`) | `MEMBER` |
| DELETE | `/push/subscriptions/{id}` | Huỷ đăng ký | `MEMBER` |
| POST | `/graphql` | Cây lồng sâu — schema ở `schema.graphqls` | Khách |

---

## 5. Những chỗ contract tự quyết — cần chốt lại

Các điểm dưới đây **không có trong BA/TDD/plan**; chúng tôi chọn một phương án để FE có cái mà
mock, nhưng đây là chỗ cần một câu trả lời dứt khoát trước khi code sâu.

1. **`404` thay vì `403` cho người còn sống bị ẩn.** Đúng về mặt riêng tư, nhưng làm việc gỡ lỗi
   khó hơn: "không có" và "không được xem" trông giống hệt nhau, kể cả với lập trình viên.
2. **`clearFields` thay vì `null` để xoá trắng trong `PATCH`.** Tránh bẫy "null hay không gửi"
   của Jackson và các lớp JSON client. Đánh đổi: hơi lạ so với JSON Merge Patch (RFC 7396) chuẩn.
3. **`GET /push/public-key`** — không nằm trong danh sách endpoint tối thiểu của plan §5, nhưng
   không có nó thì FE buộc phải nhúng cứng khoá VAPID vào build và mỗi môi trường một khoá sẽ lệch.
4. **`meta` / `access` trên `PersonDto`** (`visibleTier`, `canEdit`, `canDelete`, `isSelf`).
   Cho FE render đúng affordance thay vì gọi thử rồi ăn `403`. Nó tiết lộ **quyền của chính người
   gọi**, không tiết lộ dữ liệu bị giấu — nhưng vẫn nên có người xác nhận ranh giới này.
5. **`RelationshipLinkInput.otherPersonRole` (`SOURCE`/`TARGET`).** Cần vì lúc `POST /persons`
   người mới **chưa có id**. Hơi rườm rà; phương án thay thế là hai trường riêng `parentId` /
   `childId`, kém tổng quát hơn.
6. **`spouseOrder` bắt đầu từ `1`** (vợ cả/chồng cả) — TDD không nói mốc bắt đầu.
7. **`generation` do backend suy ra, client không gửi.** Suy từ quan hệ cha–con nên không bao giờ
   lệch với đồ thị; đổi lại, nhân khẩu chưa nối vào cây sẽ có `generation = null`.
8. **`PUT /kinship-rules` là replace trọn bộ**, không patch từng luật. Giữ cho "bộ luật đang hiệu
   lực là gì" luôn xác định được, nhưng hai người sửa cùng lúc thì một người mất việc —
   đã chặn bằng `expectedVersion`.
9. **`PrivacyLevel.RESTRICTED`** — BA chỉ nói "trẻ vị thành niên ẩn tối đa" mà không đặt tên mức.
   Chúng tôi đặt tên và cho nó là mặc định của trẻ vị thành niên.
10. **`collateralDegree` trong `RelationFacts`/`KinshipRule`** — TDD §5.5 không có trường này, nhưng
    không có nó thì rule engine **không phân biệt được `bác ruột` với `bác họ`**, mà đó là phân biệt
    cơ bản nhất của hệ danh xưng Việt. Đây là chỗ cần `graph-engineer` và Hội đồng xác nhận sớm nhất.
11. **`EventType` và `NotificationCategory`** — danh sách lấy từ FR-2.3 và luồng của plan §W5,
    không phải từ một enum đã chốt trong TDD.
12. **`202 + ChangeRequest` khi `MEMBER` gọi `POST /persons`** — có trong TDD §8.1 nhưng plan §1.7
    dời luồng duyệt sang Giai đoạn 2. Contract mô tả sẵn và ghi rõ **Giai đoạn 1 trả `403`**,
    để thêm luồng duyệt ở GĐ2 không phải phá contract.

---

## 6. Việc còn treo — cần BA / Hội đồng trả lời

1. **Endpoint ẩn danh hoá.** BA v2 §10 và TDD §8.5 bắt buộc "xoá dữ liệu cá nhân hợp pháp =
   ẩn danh hoá, giữ node phả hệ", nhưng plan §5 không liệt kê endpoint nào cho việc đó và
   `DELETE /persons/{id}` là **xoá mềm**, không phải ẩn danh hoá. Đây là **nghĩa vụ pháp lý theo
   Nghị định 13/2023 chưa có đường đi trong API**. Đề xuất `POST /persons/{id}/anonymize`.
   **Chưa đưa vào contract vì ngoài phạm vi Sprint 1 — cần BA chốt trước GĐ4.**
2. **Ai nghiệm thu bộ luật danh xưng miền Bắc?** (plan §11). Contract đã có chỗ cho `collateralDegree`
   và `isElder`, nhưng **giá trị** danh xưng phải do Hội đồng Tộc biểu duyệt. Sai danh xưng là lỗi
   mất mặt với dòng họ, không phải bug kỹ thuật.
3. **Quản lý quan hệ sau khi tạo.** Contract chỉ cho nối quan hệ **lúc tạo** nhân khẩu
   (`initialRelationships`). Sửa/xoá một cạnh quan hệ đã có (ly hôn, đính chính cha mẹ, lập kế tự)
   **chưa có endpoint** — nằm ngoài danh sách tối thiểu của plan §5 nhưng sẽ cần ngay ở Sprint 2
   khi canvas cho phép kéo-thả. Đề xuất `POST|PATCH|DELETE /relationships`.
4. **Ngưỡng "trẻ vị thành niên".** Tính theo tuổi từ `birth`, nhưng chính `birth` đầy đủ lại là
   Tầng 3 — backend tự biết, còn FE thì không. Cần xác nhận rằng FE **không cần** biết ai là trẻ
   vị thành niên (chỉ hiển thị những gì được trả về).
5. **Kỵ húy xét tới đời thứ mấy?** Contract trả `conflicts[]` nhưng không quy định phạm vi quét là
   toàn dòng họ hay chỉ trực hệ N đời. Ảnh hưởng cả hiệu năng lẫn số lượng cảnh báo giả.
   `matchKind = UNACCENTED` (chỉ trùng khi bỏ dấu) đặc biệt dễ gây nhiễu — cần quyết định có bật
   mặc định hay không.

---

## 7. Điểm BE và FE dễ hiểu sai nhau

Đọc kỹ mục này trước khi viết mock hoặc controller.

1. **Trường vắng mặt ≠ `null` ≠ chuỗi rỗng.** REST **loại hẳn** trường bị ẩn; GraphQL trả `null`.
   Mock của FE mà trả `null` cho mọi thứ sẽ khiến giao diện được thiết kế sai ngay từ đầu.
2. **`unreadCount` là tổng toàn hộp thư**, không phải số chưa đọc của trang hiện tại và không
   phụ thuộc bộ lọc. Hiểu sai là badge chuông sai.
3. **`/tree` có thể bị cắt.** `meta.truncated` không phải trường trang trí. Bỏ qua nó thì cây
   trông "đủ" trong khi đang thiếu người.
4. **`depth` âm là đời trên.** `TreeNode.depth` âm khi `direction = ANCESTORS` hoặc `BOTH`.
5. **`RelType` có hướng.** `PARENT_BIO` là `cha/mẹ → con`, không phải ngược lại. Vẽ ngược cạnh
   là lật ngược cả cây.
6. **Dâu/rể không phải cạnh.** Đừng tạo `relType` mới cho dâu/rể; chúng suy ra từ `SPOUSE` +
   huyết thống và đã có trong `badges`.
7. **`status = NO_MATCHING_RULE` không phải lỗi.** Nó nghĩa là bộ luật của dòng họ chưa phủ trường
   hợp đó. FE hiện thông điệp mời bổ sung luật, không hiện "đã xảy ra lỗi".
8. **`nextOccurrenceSolar` do server tính.** FE tự quy đổi âm–dương ở client sẽ lệch tháng nhuận,
   và lệch **không kèm bất kỳ lỗi nào** — bug âm thầm nhất của hệ thống này.
9. **Kỵ húy là hai lượt gọi.** Lượt đầu **không** gửi `confirmTabooOverride`. Đặt mặc định `true`
   "cho tiện" là xoá sổ FR-1.6.
10. **`PATCH` cần `If-Match`.** Thiếu header ⇒ `412`, không phải `400`.
11. **`POST /push/subscriptions` idempotent theo `endpoint`** và có thể trả `200` thay vì `201`.
    Backend cũng **tự xoá** subscription khi push gateway trả `404`/`410` — FE có thể thấy một
    thiết bị biến mất mà không do mình gọi `DELETE`.
12. **Web Push trên iOS chỉ chạy khi PWA đã cài vào màn hình chính** (iOS 16.4+). Không có hướng
    dẫn cài đặt thì phần lớn người dùng iPhone sẽ không bao giờ nhận push — và cũng không thấy
    lỗi gì cả.

---

## 8. Nhật ký thay đổi

| Ngày | Phiên bản | Thay đổi | Người duyệt |
|---|---|---|---|
| 2026-08-31 | `1.0.0-sprint1` | Bản chốt đầu tiên của Sprint 1: 16 operation REST + GraphQL SDL chỉ-Query. | *chờ BE + FE xác nhận* |
| 2026-09-11 | `1.1.0-privacy-consent` | **PHÁ VỠ.** Bỏ `PrivacyLevel`; thay bằng mô hình đồng thuận theo **từng nhóm trường**: enum `ShareScope` (`PRIVATE`/`BRANCH`/`CLAN`) + schema `PrivacySettings` (5 nhóm). `PersonDto.privacyLevel` → `PersonDto.privacy`; `CreatePersonRequest.privacyLevel` → `.privacy` (vắng mặt ⇒ `PRIVATE`); `UpdatePersonRequest.privacyLevel` → `.privacy` (**hợp nhất**, không thay thế; `clearFields: ["privacy"]` để đóng hết). `VisibleTier` giữ nguyên 4 giá trị nhưng nay chỉ là **tóm tắt suy ra từ kết quả lọc**, không phải đầu vào. GraphQL: `Person.privacyLevel` → `Person.privacy: PrivacySettings`. | *chờ BE + FE xác nhận* |
| 2026-09-11 | `1.1.0-privacy-consent` | *Tương thích ngược.* Thêm `RelationshipDto.otherPerson` (không nằm trong `required`). | — |
| 2026-09-11 | `1.1.0-privacy-consent` | **PHÁ VỠ (ngữ nghĩa).** `EventType` thêm `SINH_NHAT`, `KHANH_THANH`, `HOP_HO`, `CUOI_HOI`; `MUNG_THO` không còn gộp sinh nhật và `KHAC` không còn gộp họp họ/cưới hỏi/khánh thành. | *chờ BE + FE xác nhận* |
| 2026-09-11 | `1.1.0-privacy-consent` | **PHÁ VỠ.** `ConflictProblem.conflicts[]` nay là `oneOf [TabooConflict, DuplicateCandidate]` (trước đây khai sai là chỉ `TabooConflict`, không khớp `DUPLICATE_PERSON_SUSPECTED`). Thêm schema `DuplicateCandidate`. | *chờ BE + FE xác nhận* |

Mọi thay đổi **phá vỡ tương thích** phải thêm một dòng ở đây, kèm tên người của cả hai phía đã đồng ý.
