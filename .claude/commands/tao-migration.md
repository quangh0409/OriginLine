---
description: Tạo migration schema PostgreSQL (Flyway) — bảng quan hệ và/hoặc graph Apache AGE — đúng chuẩn dự án
---

Tạo migration cho thay đổi schema được mô tả: **$ARGUMENTS**

Quy trình bắt buộc:

1. **Xác định phạm vi.** Chỉ có **một CSDL: PostgreSQL 16 + Apache AGE.** Thay đổi có thể chạm (a) bảng quan hệ, (b) graph `giapha_graph` trong AGE, hoặc cả hai — nhưng tất cả đều đi qua **Flyway**, không có store thứ hai. Nếu đụng cả hai, gộp vào một migration có thứ tự rõ ràng để graph và bảng không lệch nhau.

2. **Bảng quan hệ:** tạo file migration mới theo convention `V{n}__mo_ta.sql` (Flyway); dùng `R__` cho repeatable (ví dụ seed bộ quy tắc danh xưng `DEFAULT`). Tuyệt đối không sửa migration đã merge. Thêm index cho cột hay tra cứu; `JSONB` cho thuộc tính nhân khẩu mở rộng; `ltree` cho `branch.path`; generated column không dấu + FTS `unaccent` cho tra cứu tên.

3. **Graph AGE:** viết Cypher trong `SELECT * FROM cypher('giapha_graph', $$ ... $$) AS (...)`. Nhớ **soft-delete flag** — không migration nào được xóa cứng node. Nếu thêm loại cạnh mới, cập nhật luôn bảng chiếu `relationship` để hai bên khớp nhau.

4. **Toàn vẹn:** đảm bảo mọi bảng chịu mutation có cột **audit versioning** (ai, khi nào, before/after) và `version` cho optimistic locking. RBAC luôn có chiều **branch scope** (`ltree`). Trường dữ liệu cá nhân người sống phải xác định rõ thuộc Tầng 1/2/3 theo BA §10.

5. **Rollback:** cung cấp script down / hướng dẫn hoàn tác.

6. Nêu lệnh chạy migration và cách verify. Nếu cần công việc graph phức tạp, uỷ thác cho subagent `graph-engineer`.

Đọc `CLAUDE.md` gốc và từ điển dữ liệu ở TDD §5–6 trước để nắm quy ước persistence.
