package vn.giapha.membership.domain.port;

import java.time.Instant;

/**
 * Đếm và ghi nhận số lần thử mã mời của một người gọi — nền của phép giới hạn tần suất.
 *
 * <h2>Vì sao đây là một cổng riêng chứ không phải một cột trên {@code invitation}</h2>
 * Người dò mã <b>không trúng lời mời nào</b>. Đếm trên dòng {@code invitation} chỉ đếm được những
 * lần đoán đúng — tức là đếm sau khi mất bò. Khoá đếm vì vậy phải là người gọi, không phải lời mời.
 *
 * <p>Định danh người gọi được truyền xuống ở dạng <b>đã băm</b>: địa chỉ IP là dữ liệu cá nhân theo
 * Nghị định 13/2023, và câu hỏi duy nhất cần trả lời là "có phải cùng một người gọi không".</p>
 */
public interface InviteThrottlePort {

    /** Số lần thử <b>thất bại</b> của {@code clientKeyHash} kể từ {@code since}. */
    int failuresSince(String clientKeyHash, Instant since);

    /**
     * Ghi một lần thử.
     *
     * <p>Phải ghi được cả khi lời mời không dùng được, tức là <b>sau</b> một ngoại lệ nghiệp vụ.
     * Hiện thực vì thế chạy trong transaction riêng — xem adapter.</p>
     */
    void record(String clientKeyHash, String outcome);
}
