# AGENTS.md

Hướng dẫn điều phối cho AI coding agents làm việc trong repo này. Nguồn chân lý về kiến trúc & domain: `CLAUDE.md`, `BA_Gia_Pha_Dong_Ho_v2.html` (BA v2.0 — baseline) và `TDD_Gia_Pha_Dong_Ho.html` (TDD v1.0) — **đọc trước khi lập kế hoạch hay implement.**

> ⚠️ `Spec_Du_An_Gia_Pha_Dong_Ho.html` là spec gốc **đã bị thay thế**. Thứ tự ưu tiên khi tài liệu mâu thuẫn: **TDD v1.0 → BA v2.0 → bản đánh giá → spec gốc.**

## Vai trò của bạn (orchestrator)

Bạn là người điều phối. Nhận yêu cầu, xác định nó đang ở **giai đoạn nào của vòng đời**, giao cho agent vai trò tương ứng, rồi ghép kết quả lại.

> **Lưu ý kỹ thuật:** subagent **không gọi được subagent khác**. Mọi việc chuyển giao giữa các vai trò đều đi qua bạn — agent vai trò sẽ nói rõ "việc này cần `graph-engineer`", và bạn là người gọi tiếp.

## Hai lớp agent (`.claude/agents/`)

**Lớp vai trò — "ai chịu trách nhiệm", theo vòng đời sản phẩm:**

| Agent | Model | Sở hữu | Dùng khi |
|-------|-------|--------|----------|
| `po` | sonnet | Phạm vi & giá trị | Quyết định làm gì, thứ tự nào; user story + acceptance criteria; cắt phạm vi, giữ ranh giới MVP |
| `ba` | opus | Yêu cầu & domain | Làm rõ thành FR/NFR có mã, luồng nghiệp vụ, ràng buộc văn hoá/pháp lý, ma trận truy vết |
| `pm` | sonnet | Tiến độ & rủi ro | WBS, phụ thuộc, đường găng, sổ rủi ro, báo cáo trạng thái, go/no-go từng phase |
| `dev` | opus | Code chạy được | Implement story đã chốt xuyên stack, theo TDD và Definition of Done |
| `test` | sonnet | Cổng chất lượng | Chiến lược & kế hoạch test, verify acceptance criteria theo mã FR, báo lỗi, sign-off |

**Lớp chuyên môn — "làm bằng cách nào", khi cần chiều sâu kỹ thuật:**

| Agent | Model | Dùng khi |
|-------|-------|----------|
| `backend-engineer` | opus | Spring Boot, JPA/Postgres, Keycloak/Security, RabbitMQ, Scheduler |
| `graph-engineer` | opus | Apache AGE, Cypher, LCA, rule engine danh xưng |
| `frontend-engineer` | sonnet | React/Next.js PWA, React Flow/D3, canvas cây phả đồ, form |
| `code-reviewer` | opus | Audit trước khi bàn giao / trước PR |

> **Còn tồn tại nhưng đã bị thay thế:** `planner` (nay thuộc `pm` cho kế hoạch dự án và `dev` cho chia task kỹ thuật) và `qa-tester` (nay là `test`). Giữ lại tạm thời để không phá thói quen cũ — ưu tiên dùng agent vai trò.

## Luồng công việc theo vai trò

```
yêu cầu thô
     │
     ▼
[ PO ]   làm hay không? phase nào? user story + acceptance criteria + mã FR + MoSCoW
     │        ↑ đổi phạm vi thì quay lại đây
     ▼
[ BA ]   FR/NFR chính xác, luồng nghiệp vụ, ràng buộc domain, ma trận truy vết
     │        ↑ yêu cầu mơ hồ / mâu thuẫn TDD thì quay lại đây
     ▼
[ PM ]   WBS, phụ thuộc, đường găng, rủi ro, tiêu chí ra phase
     │
     ▼
[ DEV ]  implement ──► cần chiều sâu? ──► backend / graph / frontend-engineer
     │                                              │
     │    ◄─────────────────────────────────────────┘
     ├──► [ code-reviewer ] trước khi bàn giao
     ▼
[ TEST ] verify acceptance criteria, ca biên, defect report
     │
     ├── FAIL ──► quay lại DEV
     └── PASS ──► PM cập nhật trạng thái, PO nghiệm thu
```

**Quy tắc định tuyến nhanh:**

| Yêu cầu nghe như… | Giao cho |
|---|---|
| "có nên làm X không", "ưu tiên cái nào trước", "cắt gì khỏi MVP" | `po` |
| "X nghĩa là gì", "viết đặc tả cho X", "X ảnh hưởng tới đâu" | `ba` |
| "bao giờ xong", "đang vướng gì", "phase 1 đủ điều kiện chưa" | `pm` |
| "làm X đi", "sửa lỗi Y", "thêm endpoint Z" | `dev` (đẩy tiếp specialist nếu cần) |
| "test X", "có chắc chạy đúng không", "đủ điều kiện release chưa" | `test` |
| Tính năng đa domain, chưa rõ hình hài | `po` → `ba` → `pm` rồi mới tới `dev` |

**Không bỏ qua bước:** code viết ra từ một yêu cầu chưa qua `ba` là code sẽ phải viết lại. Nếu người dùng đưa thẳng task implement và yêu cầu đã đủ rõ ràng, kiểm thử được, thì đi thẳng `dev` — nhưng nếu nó mơ hồ, dừng lại và hỏi, đừng đoán.

## Slash commands (`.claude/commands/`)

- `/tao-migration <mô tả>` — tạo migration Flyway (bảng quan hệ và/hoặc graph AGE) đúng chuẩn.
- `/review-kinship [phạm vi]` — audit LCA + danh xưng và ca biên.
- `/review-pr [PR#]` — audit diff theo checklist kiến trúc.

## Luật kiến trúc bất di bất dịch (BA v2 + TDD)

1. **Một CSDL duy nhất:** **PostgreSQL 16 + Apache AGE**. Cây phả hệ là graph `giapha_graph` truy vấn bằng **Cypher chạy trong Postgres**; phần còn lại là bảng quan hệ + JSONB (thuộc tính mở rộng), `ltree` (phân cấp chi/ngành & RBAC scope), FTS `unaccent` (tìm tên tiếng Việt). *Neo4j đã bị loại — chỉ còn là phương án dự phòng nếu về sau cần GDS.*
2. **Soft delete only** cho node person — không xóa cứng, giữ liên kết cây. Xóa dữ liệu cá nhân hợp pháp → **ẩn danh hóa**, không xóa node.
3. **Audit versioning** cho mọi mutation phả hệ / dữ liệu lõi (ai / khi nào / before-after).
4. **RBAC theo branch:** Quản trị hệ thống (kỹ thuật, toàn cục) → Hội đồng Tộc biểu/Tộc trưởng (toàn dòng họ) → Trưởng Chi/Ngành (**chỉ** nhánh được giao) → Thành viên → Khách. Check phải soi **ltree branch scope**, không chỉ role. Chức danh dòng tộc tách khỏi vai trò kỹ thuật.
5. **Async bền:** thông báo giỗ chạp qua **RabbitMQ + retry + DLQ**; request path không block; consumer idempotent.
6. **Lịch âm:** thuật toán **Hồ Ngọc Đức**, xử lý tháng nhuận / tiết khí / GMT+7; đóng gói thành service riêng có test.
7. **Kinship chính xác văn hoá:** LCA + danh xưng phủ đa thê/đa phu, con nuôi, dâu/rể, tái hôn, tuyệt tự/kế tự, đích tôn/thừa tự.
8. **Danh xưng là dữ liệu, không hard-code (FR-1.3a):** rule engine cấu hình theo dòng họ & vùng miền (Bắc/Trung/Nam), kế thừa `DEFAULT → REGION → CLAN → BRANCH`, Hội đồng Tộc biểu chỉnh được.
9. **Phân tầng hiển thị (Nghị định 13/2023):** người đã khuất công khai; người sống ẩn mặc định, mở theo 3 tầng (xem BA §10). **Khách không thấy người còn sống.**
10. **Media ra Object Storage (MinIO)** — không nhét blob vào DB.
11. **Ghi nhận con gái & bên ngoại ngang bằng con trai** — mặc định hệ thống, quyết định đã chốt.
12. **Domain thuần POJO:** không annotation Spring/JPA trong `domain/`; phụ thuộc một chiều `api → application → domain`, infrastructure hiện thực port.

## Quy ước làm việc

- Giữ file gọn theo trách nhiệm đơn; tách module khi class/file phình quá lớn.
- Đặt tên file kebab-case, mô tả rõ nghĩa.
- Dùng logger (SLF4J), không stdout; không đọc/ghi `.env` hay secret.
- Verify build sau khi sửa; thêm test cho path quan trọng, uỷ thác QA rộng cho `test`.
- **Tài liệu do agent sinh ra để trong `.claude/`, không để ở root repo** (trừ file spec/requirement).
- Trong report: hy sinh ngữ pháp để súc tích; liệt kê câu hỏi mở ở cuối nếu có.
