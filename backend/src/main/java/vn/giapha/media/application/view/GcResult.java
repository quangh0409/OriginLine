package vn.giapha.media.application.view;

/**
 * Kết quả một lượt dọn tệp mồ côi.
 *
 * <p>Trả về con số chứ không chỉ ghi log, vì lệnh quản trị
 * {@code POST /api/v1/admin/media/gc} phải nói được nó đã làm gì — cùng lý do mà
 * {@code POST /api/v1/admin/reminders/dispatch} trả về số lượng: một lệnh vận hành im lặng là một
 * lệnh không ai dám bấm lần thứ hai.
 *
 * @param expiredTickets phiếu {@code PENDING} quá hạn đã dọn — "xin URL rồi bỏ ngang"
 * @param orphanAssets tệp {@code READY} mất chủ đã dọn — bài bị gỡ, ảnh chân dung bị thay
 * @param storageFailures số đối tượng xoá khỏi kho <b>không thành công</b>. Không phải lỗi chặn:
 *        hàng vẫn để nguyên trạng thái cũ và lượt dọn sau thử lại. Con số này khác 0 nhiều lần
 *        liên tiếp nghĩa là kho có vấn đề, và đó là thứ đáng cảnh báo
 */
public record GcResult(int expiredTickets, int orphanAssets, int storageFailures) {

    public static final GcResult NONE = new GcResult(0, 0, 0);

    public int total() {
        return expiredTickets + orphanAssets;
    }
}
