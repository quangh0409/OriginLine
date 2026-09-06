---
description: Audit logic kinship engine (LCA + suy luận danh xưng) về tính đúng đắn văn hoá và các ca biên
---

Rà soát logic quan hệ họ hàng liên quan tới: **$ARGUMENTS** (nếu trống, rà toàn bộ kinship engine trong diff hiện tại).

Uỷ thác cho subagent `graph-engineer`, kiểm tra:

1. **LCA (tổ tiên chung gần nhất):** truy vấn Cypher AGE đúng, có tham số hoá, chạy nhanh trên cây lớn; xử lý đúng khi hai người khác chi/ngành hoặc cùng nhánh. Trả đủ `(lca, dist_a, dist_b)`.

2. **Suy luận danh xưng:** đối chiếu quy tắc theo `RelationFacts` (gen_delta + side nội/ngoại + giới tính + vai trên/dưới + priority). Kiểm tra các danh xưng chuẩn: bác họ, chú/thúc bá, cô ruột, cậu, dì, cháu gọi bằng cụ họ… Mỗi quy tắc phải có test.

2b. **Rule engine (FR-1.3a):** danh xưng phải đọc từ `kinship_rule_set`/`kinship_rule`, **không hard-code**; kiểm tra kế thừa & ghi đè `DEFAULT → REGION → CLAN → BRANCH` giải đúng thứ tự, và `KinshipResolver` là domain thuần (test được không cần Spring context).

3. **Ca biên bắt buộc phủ:**
   - Đa thê / đa phu (nhiều vợ/chồng).
   - Con nuôi vs con ruột.
   - Dâu / rể (quan hệ hôn nhân, không phải huyết thống).
   - Tái hôn, con riêng.
   - Tuyệt tự / kế tự (đứt dòng, lập kế tự); đích tôn / thừa tự.
   - Node đã soft-delete không được làm sai lệch đường đi / danh xưng.
   - Con gái & bên ngoại được ghi nhận ngang bằng con trai (mặc định hệ thống).

4. **Nhất quán dữ liệu:** không recompute kinship ở client; nguồn chân lý của cạnh là graph AGE, bảng `relationship` chỉ là bản chiếu — hai bên phải ghi trong cùng transaction.

Báo cáo theo mức độ nghiêm trọng với tham chiếu `file:line`, kèm ca test còn thiếu và đề xuất sửa. Nếu phát hiện quy tắc văn hoá mơ hồ, liệt kê câu hỏi mở ở cuối.
