package vn.giapha.events.application;

/**
 * Kết quả một lượt dọn phạm vi sự kiện cũ — xem {@link EventScopeBackfillService}.
 *
 * @param scanned       số sự kiện còn hiệu lực không có phạm vi nào
 * @param assignedBranch số dòng được gán chi/ngành lấy từ hồ sơ nhân khẩu
 * @param markedClanWide số dòng được đánh dấu tường minh là cấp dòng họ
 * @param dryRun        lượt chạy này chỉ đếm, không ghi
 */
public record EventScopeBackfillResult(int scanned, int assignedBranch, int markedClanWide,
                                       boolean dryRun) {

    /** Số dòng thật sự bị sửa — {@code 0} với {@code dryRun}. */
    public int changed() {
        return dryRun ? 0 : assignedBranch + markedClanWide;
    }
}
