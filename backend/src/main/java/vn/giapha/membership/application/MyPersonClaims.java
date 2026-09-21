package vn.giapha.membership.application;

import java.util.List;

/**
 * Màn "đang chờ duyệt" của chính người gửi: danh sách đơn <b>cộng hạn mức gửi lại</b>.
 *
 * <h2>Vì sao endpoint này bọc, trong khi mọi danh sách khác trả mảng phẳng</h2>
 * {@code /change-requests/mine} và {@code /invitations} trả mảng phẳng, và giữ được quy ước là
 * tốt. Nhưng ở đây có một thứ <b>không thuộc về bất kỳ phần tử nào</b> trong mảng: số lần gửi lại
 * còn lại. Nhét nó vào từng đơn là lặp một giá trị toàn cục lên n dòng rồi để chúng có cơ hội lệch
 * nhau; đặt nó ở một endpoint thứ hai là bắt màn hình gọi hai lượt cho một câu trả lời.
 *
 * @param quota hạn mức gửi lại — xem {@link ClaimQuota}
 */
public record MyPersonClaims(List<PersonClaimView> claims, ClaimQuota quota) {

    public MyPersonClaims {
        claims = claims == null ? List.of() : List.copyOf(claims);
    }

    /**
     * Còn được gửi lại mấy lần nữa.
     *
     * <h2>Ngưỡng là một giá trị CẤU HÌNH CỦA MÁY CHỦ, nên client không được đoán</h2>
     * Nó nằm ở {@code giapha.membership.claim.max-rejected} và một dòng họ hoàn toàn có thể đặt
     * khác. Giao diện gán cứng con số 3 sẽ đúng hôm nay và <b>âm thầm sai</b> ngày Hội đồng đổi
     * cấu hình — lúc ấy người dùng thấy "còn 1 lần" trong khi máy chủ đã chặn, hoặc ngược lại.
     *
     * <p>Và không có trường này thì màn hình <b>im lặng cho tới khi đâm vào</b>
     * {@code CLAIM_LIMIT_REACHED}: người dùng gửi đơn thứ tư, bị chặn, và mới biết là có giới hạn.
     * Design 07 §1.4 chốt rằng phải nói trước.</p>
     *
     * @param rejected  số đơn đã bị <b>từ chối</b>. Đơn tự rút <b>không</b> tính: người tự sửa sai
     *                  của mình không phải là người đang dò
     * @param max       ngưỡng hiện hành của máy chủ
     * @param remaining {@code max - rejected}, không bao giờ âm
     */
    public record ClaimQuota(int rejected, int max, int remaining) {

        public static ClaimQuota of(int rejected, int max) {
            return new ClaimQuota(rejected, max, Math.max(max - rejected, 0));
        }
    }
}
