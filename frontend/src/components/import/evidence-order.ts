import type { ImportDuplicateEvidence, ImportEvidenceField } from "@/lib/api/data-import";

/**
 * Thứ tự đọc bằng chứng khi đối chiếu hai người nghi trùng.
 *
 * <h2>Đây là thứ tự theo ĐỘ TIN, và nó là thứ duy nhất client được phép xếp</h2>
 * Bộ dò trùng **không công bố** bản tách điểm theo từng tín hiệu: hợp đồng để
 * `points` là trường tuỳ chọn và hôm nay backend không gửi nó. Vì vậy ở đây
 * không có một con số nào được cộng, ước lượng hay bù vào — thang điểm là dữ
 * liệu hiệu chỉnh của bộ dò, và một bản sao ở client chắc chắn sẽ trôi.
 *
 * Cái client *được* làm là quyết định **mắt người rơi xuống đâu trước**, và với
 * một dòng họ Việt thì hai dấu hiệu sau không hề ngang nhau:
 *
 * <ul>
 *   <li><b>Ngày giỗ</b> được cả họ cúng hằng năm. Nó được nhắc lại thành tiếng,
 *       trước mặt nhiều người, mỗi năm một lần, suốt nhiều đời. Chép sai một
 *       ngày giỗ là chuyện sẽ có người phát hiện.</li>
 *   <li><b>Năm sinh</b> trong sổ cũ thường chép theo trí nhớ, nhiều khi quy từ
 *       can chi hoặc từ "năm đói", và lệch một hai năm là chuyện thường. Hai
 *       bản ghi lệch năm sinh <i>không</i> nói lên rằng đó là hai người.</li>
 * </ul>
 *
 * Người đối chiếu chỉ có vài giây cho mỗi cặp. Nếu dòng đầu tiên họ đọc là năm
 * sinh lệch, họ kết luận "hai người" và bỏ qua một ngày giỗ trùng khít nằm ở
 * dòng thứ sáu.
 */
const TRUST_ORDER: readonly ImportEvidenceField[] = [
  // 1. Ngày giỗ — bằng chứng mạnh nhất, vì lý do ở trên.
  "DEATH_LUNAR",
  // 2. Cha đã xác định — dấu hiệu cấu trúc, mạnh nhất trong nhóm quan hệ.
  "FATHER",
  // 3. Tên huý — ít trùng ngẫu nhiên hơn tên thường gọi rất nhiều.
  "TABOO_NAME",
  // 4. Họ tên chính — trùng nhiều, vì tên đệm theo đời là tục lệ.
  "FULL_NAME",
  // 5. Đời — gần như luôn trùng khi tên trùng, nên tự nó nói được rất ít.
  "GENERATION",
  // 6. Nguyên quán — cả chi cùng một quán thì nó không phân biệt được ai với ai.
  "ORIGIN_PLACE",
  // 7. Năm sinh — xếp CUỐI có chủ ý. Xem chú thích trên.
  "BIRTH_YEAR",
];

const RANK = new Map<ImportEvidenceField, number>(
  TRUST_ORDER.map((field, index) => [field, index])
);

/** Hạng tin cậy của một dấu hiệu; dấu hiệu lạ (backend thêm mới) xuống cuối. */
export function trustRank(field: ImportEvidenceField): number {
  return RANK.get(field) ?? TRUST_ORDER.length;
}

/** Sắp bằng chứng theo độ tin. Không đổi nội dung, không cộng điểm. */
export function orderByTrust(
  evidence: readonly ImportDuplicateEvidence[]
): ImportDuplicateEvidence[] {
  return [...evidence].sort((a, b) => trustRank(a.field) - trustRank(b.field));
}

/**
 * Dấu hiệu nào đáng **tô** cho mắt người dừng lại.
 *
 * Chỉ hai loại: chỗ hai bên khác nhau, và chỗ tệp bỏ trống trong khi bên kia có
 * dữ liệu. Loại thứ hai là loại nguy hiểm — hợp nhất một hồ sơ đủ lớp tên với
 * một bản ghi chỉ có tên không dấu là cách nhanh nhất để mất tên huý và tên tự.
 * Chỗ hai bên giống nhau thì để yên: tô hết thì không còn gì được tô.
 */
export function isDivergence(evidence: ImportDuplicateEvidence): boolean {
  return evidence.match === "DIFFERENT" || evidence.match === "MISSING_IN_FILE";
}
