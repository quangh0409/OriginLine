import { apiFetch, apiFetchWithMeta, type ApiResult } from "./http";
import type { PageMeta } from "@/types/api";

/**
 * Lớp API cho **vinh danh** — bản ghi gắn với một nhân khẩu (đỗ đạt, chức
 * tước, thành tích, khen thưởng), khác hẳn một bài viết vì nó có cấu trúc để
 * tra cứu và thống kê ("chi nào có bao nhiêu người đỗ đạt").
 *
 * <h2>Trạng thái hợp đồng</h2>
 * Cùng hoàn cảnh với `src/lib/api/posts.ts`: chưa có trong
 * `contracts/openapi.yaml`, hình dạng dưới đây do PO chốt cho Đợt 2.
 *
 * <h2>`GET` NHẬN tham số `status` (sửa lại — bản trước của tệp này sai)</h2>
 * `HonourController.list` nhận `status` và lọc ngay trong SQL. Nghĩa là:
 * <b>đừng tải cả trang rồi gom nhóm theo `status` ở client</b> — gọi hai lượt
 * riêng (`status: "PENDING"` cho hàng chờ duyệt, `status: "PUBLISHED"` cho
 * danh sách công khai) như `posts.list({status:"PENDING"})` đã làm. Gom ở
 * client từng là cách xử lý tạm khi hợp đồng còn thiếu tham số này; giữ lại
 * nó bây giờ nghĩa là người duyệt tải về cả `PUBLISHED`/`WITHDRAWN` rồi bỏ,
 * đúng thứ PO vừa yêu cầu bỏ.
 *
 * <h2>Máy trạng thái CHUNG với bài viết (sửa lại — bản trước SAI)</h2>
 * Chỉ ba giá trị thật: <code>PENDING → PUBLISHED</code> (duyệt) hoặc
 * <code>PENDING → WITHDRAWN</code> (từ chối). <b>Không có `APPROVED`, không có
 * `REJECTED`</b> — đó là suy đoán sai của bản đầu tệp này. Vinh danh dùng
 * chung một máy trạng thái với bài viết theo chủ ý của backend (tách riêng
 * nghĩa là chép máy trạng thái thành hai bản phải đồng bộ tay).
 *
 * <h2>Người còn sống: đừng giả định trường nào cũng có mặt</h2>
 * Vinh danh của người còn sống đi qua nhóm trường riêng tư thứ sáu (ngoài năm
 * nhóm của `PrivacySettings`, chưa có tên chính thức trong contract). Hệ quả
 * cho giao diện: `personDisplayName` (và có thể cả `issuer`/`description`) có
 * thể **vắng mặt** dù bản ghi vẫn hiện ra — dùng `isPresent` cho MỌI trường
 * tuỳ chọn, không bao giờ suy luận từ tình trạng còn sống hay chưa của ai.
 * Hệ quả khác, cũng đã chốt: <b>duyệt xong không có nghĩa là cả họ thấy tên</b>
 * — một vinh danh `PUBLISHED` của người chưa bật chia sẻ vẫn chỉ hiện tên với
 * chính chủ (và người duyệt đúng phạm vi). Đó là đúng thiết kế.
 *
 * <h2>`personId` tra qua tìm kiếm nhân khẩu, không gõ tay</h2>
 * `personsApi.search` (`/api/v1/persons/search`) đã lọc riêng tư và trả `id` —
 * dùng nó để chọn người được vinh danh (xem `HonourFormModal`), đừng bắt người
 * dùng gõ một mã nhân khẩu.
 *
 * <h2>`page.totalElements` đếm TRƯỚC lọc riêng tư</h2>
 * Không dùng nó để vẽ số trang hay "N kết quả" — dùng `page.hasNext`. Một
 * lượt gọi có thể báo `totalElements: 12` mà chỉ ba bản ghi thật sự lọt qua bộ
 * lọc, vì con số ấy đếm trên tập chưa lọc.
 */

export type HonourKind = "DO_DAT" | "CHUC_TUOC" | "THANH_TICH" | "KHEN_THUONG";

/** Ba giá trị thật — CHUNG máy trạng thái với `PostStatus` (xem javadoc đầu tệp). */
export type HonourStatus = "PENDING" | "PUBLISHED" | "WITHDRAWN";

export interface HonourDto {
  id: string;
  personId: string;
  /** Vắng mặt là câu trả lời cuối — có thể là do lọc riêng tư, không phải lỗi. */
  personDisplayName?: string | null;
  kind: HonourKind;
  title: string;
  year?: number | null;
  issuer?: string | null;
  description?: string | null;
  status: HonourStatus;
  /** Định danh nội bộ — không hiển thị trực tiếp. Dùng `reviewedByDisplayName`. */
  reviewedBy?: string | null;
  /** Tên hiển thị của người duyệt, đã qua lọc riêng tư. `null` ⇒ không vẽ tên, chỉ vẽ ngày. */
  reviewedByDisplayName?: string | null;
  reviewedAt?: string | null;
  createdAt: string;
  version: number;
}

export interface HonourPage {
  items: HonourDto[];
  page: PageMeta;
}

export interface ListHonoursParams {
  personId?: string;
  kind?: HonourKind;
  branchId?: string;
  status?: HonourStatus;
  page?: number;
  size?: number;
}

export interface HonourWriteBody {
  personId: string;
  kind: HonourKind;
  title: string;
  year?: number | null;
  issuer?: string | null;
  description?: string | null;
}

export type HonourPatchBody = Partial<Omit<HonourWriteBody, "personId">>;

export interface ReviewHonourBody {
  approve: boolean;
  note?: string | null;
}

const BASE = "/api/v1/honours";

export const honoursApi = {
  list: ({ personId, kind, branchId, status, page = 0, size = 20 }: ListHonoursParams = {}) =>
    apiFetch<HonourPage>(BASE, { query: { personId, kind, branchId, status, page, size } }),

  create: (body: HonourWriteBody): Promise<ApiResult<HonourDto>> =>
    apiFetchWithMeta<HonourDto>(BASE, { method: "POST", body }),

  update: (id: string, body: HonourPatchBody, ifMatch: string): Promise<ApiResult<HonourDto>> =>
    apiFetchWithMeta<HonourDto>(`${BASE}/${id}`, { method: "PATCH", body, ifMatch }),

  review: (id: string, body: ReviewHonourBody) =>
    apiFetch<HonourDto>(`${BASE}/${id}/review`, { method: "POST", body }),

  remove: (id: string) => apiFetch<void>(`${BASE}/${id}`, { method: "DELETE" }),
};
