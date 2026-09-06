---
description: Audit diff/PR hiện tại theo checklist kiến trúc của dự án trước khi merge
---

Rà soát thay đổi hiện tại (mặc định `git diff`; nếu $ARGUMENTS là số PR thì lấy PR đó qua `gh pr diff`).

Uỷ thác cho subagent `code-reviewer`. Ưu tiên bug đúng/sai trước, rồi tới đơn giản hoá/thiết kế. Bắt buộc soi checklist đặc thù dự án:

- **Một store duy nhất:** cây là graph trong **Apache AGE nội trong PostgreSQL**; không traversal cây bằng recursive CTE, không có dependency Neo4j quay lại.
- **Dual write:** cạnh AGE và dòng `relationship` ghi trong **cùng một transaction** — không cái nào đứng lẻ.
- **Ranh giới hexagonal:** `domain/` không dính annotation Spring/JPA; phụ thuộc một chiều `api → application → domain`; không gọi thẳng repository của context khác.
- **Soft delete:** không có `DELETE` cứng làm mồ côi liên kết cây; xóa hợp pháp = ẩn danh hóa Tầng 3, giữ node.
- **Audit trail** ghi cho mọi mutation phả hệ / dữ liệu lõi (actor, thời điểm, before/after) — không log giá trị nhạy cảm.
- **RBAC theo branch:** Trưởng Chi/Ngành chỉ sửa/duyệt trong nhánh của mình — check soi `ltree` branch scope chứ không chỉ role.
- **Phân tầng hiển thị (NĐ 13/2023):** lọc theo `is_alive` + role + `privacy_level`; Khách không thấy người còn sống; Tầng 3 chỉ bản thân/admin/opt-in.
- **Async bền:** gửi thông báo qua RabbitMQ có retry + DLQ; request path không block; consumer idempotent.
- **Kinship & lịch âm:** LCA/danh xưng đúng ca biên và **đọc từ rule set** (không hard-code danh xưng); quy đổi âm–dương xử lý tháng nhuận / tiết khí / GMT+7.
- **Media** đẩy lên MinIO, không nhét blob vào DB.
- **Chung:** null/type safety, ranh giới transaction, dọn resource, Cypher/SQL tham số hoá, không log/commit secret, test cho path quan trọng.

Xuất kết quả theo mức độ nghiêm trọng, tham chiếu `file:line`, đề xuất fix cụ thể.
