import type { ClaimReviewView } from "@/lib/api/membership-admin";

/**
 * Một cụm trong hàng chờ duyệt: hoặc một lá đơn đứng riêng, hoặc **nhiều đơn
 * cùng nhận một nhân khẩu**.
 */
export interface QueueGroup {
  /** Khoá ổn định cho React — khoá của lá đơn đầu cụm. */
  key: string;
  /**
   * Các đơn trong cụm, **giữ nguyên thứ tự máy chủ trả về**.
   *
   * Cố ý không sắp lại theo `createdAt`: design 07 §1.4 chốt rằng người gửi
   * trước **không** được ưu tiên, và một danh sách sắp theo thời gian gửi là
   * một cách ưu tiên — mắt người đọc từ trên xuống. V16 cũng cố ý không đặt
   * chỉ mục duy nhất trên `(person_id) WHERE status='PENDING'` vì cùng lý do.
   */
  requests: ClaimReviewView[];
  /**
   * Số đơn tranh chấp mà máy chủ có nhắc tới nhưng **không nằm trong trang
   * này**.
   *
   * Lẽ ra luôn là `0`: hợp đồng đòi máy chủ trả mọi đơn tranh chấp trong cùng
   * một trang. Nhưng "lẽ ra" không phải một phép kiểm — nếu nó khác `0` thì
   * giao diện phải **nói ra**, vì lặng lẽ hiện một trong hai đơn chính là cái
   * lỗi mà ca biên này sinh ra để chặn.
   */
  missingCompeting: number;
}

/**
 * Gom hàng chờ duyệt thành cụm theo **`competingClaimIds` do máy chủ gắn**.
 *
 * <h2>Vì sao không nhóm theo `person.id`</h2>
 * Nhóm theo nhân khẩu là suy luận từ dữ liệu đang cầm, và dữ liệu đang cầm là
 * *một trang*. Đơn thứ hai nằm ở trang sau thì phép nhóm ấy im lặng bỏ sót đúng
 * ca "hai người cùng nhận một nhân khẩu" — rồi Trưởng chi duyệt lá đơn duy nhất
 * mình thấy, vốn chưa chắc là lá đúng.
 *
 * Máy chủ biết toàn bộ hàng đợi, nên việc nói "hai đơn này tranh nhau" là việc
 * của máy chủ. Hàm này chỉ dựng lại cụm từ lời khai ấy.
 *
 * <h2>Quan hệ tranh chấp được coi là ĐỐI XỨNG và BẮC CẦU</h2>
 * Nếu A khai tranh với B thì B thuộc cụm của A, kể cả khi bản ghi của B quên
 * nhắc tới A. Một bên khai thiếu là lỗi dữ liệu; bỏ sót một lá đơn vì lỗi ấy
 * tệ hơn hẳn việc hiện thừa một lá. Ba người cùng nhận một nhân khẩu ra **một**
 * cụm ba, không phải ba cụm đôi chồng nhau.
 */
export function groupClaimQueue(requests: ClaimReviewView[]): QueueGroup[] {
  const byId = new Map(requests.map((r) => [r.id, r]));
  const thuTu = new Map(requests.map((r, i) => [r.id, i]));
  const daXep = new Set<string>();
  const groups: QueueGroup[] = [];

  for (const request of requests) {
    if (daXep.has(request.id)) continue;

    const cum: ClaimReviewView[] = [];
    const canXet: string[] = [request.id];
    const daXet = new Set<string>();
    let thieu = 0;

    while (canXet.length > 0) {
      const id = canXet.pop() as string;
      if (daXet.has(id)) continue;
      daXet.add(id);

      const hien = byId.get(id);
      if (!hien) {
        thieu += 1;
        continue;
      }
      cum.push(hien);
      for (const other of hien.competingClaimIds ?? []) {
        if (!daXet.has(other)) canXet.push(other);
      }
      // Chiều ngược lại: ai khai tranh với `id` mà `id` không khai lại.
      for (const other of requests) {
        if (!daXet.has(other.id) && (other.competingClaimIds ?? []).includes(id)) {
          canXet.push(other.id);
        }
      }
    }

    // `cum` dựng theo thứ tự duyệt đồ thị; xếp lại theo thứ tự MÁY CHỦ trả về
    // để không vô tình tạo ra một thứ tự ưu tiên nào khác.
    cum.sort((a, b) => (thuTu.get(a.id) ?? 0) - (thuTu.get(b.id) ?? 0));

    for (const r of cum) daXep.add(r.id);
    groups.push({
      key: cum[0]?.id ?? request.id,
      requests: cum,
      missingCompeting: thieu,
    });
  }

  return groups;
}
