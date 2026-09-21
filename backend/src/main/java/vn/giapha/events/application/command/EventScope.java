package vn.giapha.events.application.command;

import java.util.UUID;

/**
 * Phạm vi của một sự kiện: việc của <b>cả họ</b>, hay việc của <b>một chi/ngành</b>.
 *
 * <h2>Đây là trường quyết định AI ĐƯỢC NHẮC, không phải một nhãn hiển thị</h2>
 * Giá trị này chảy thẳng từ đây tới {@code RecipientDirectory.membersOfBranch}, nơi nó trở thành
 * một phép so {@code ltree} ({@code child.path <@ root.path}) trên bảng {@code person}. Đặt sai là
 * gửi thông báo cho vài trăm người không liên quan — và "nhắc nhầm cả họ cho việc của một chi là
 * cách nhanh nhất để người ta tắt thông báo".
 *
 * <h2>Phải chọn ĐÚNG MỘT, không được để trống cả hai</h2>
 * Ràng buộc {@code ck_event_scope} của V4 chỉ cấm đặt <i>cả hai</i>; nó không bắt buộc phải có
 * <i>một</i>. Khoảng trống ấy có hậu quả thật:
 * {@code NotificationDispatchService.resolveRecipients} gặp sự kiện không phạm vi thì <b>nhắc cả
 * họ</b> (chọn rộng thay vì hẹp, vì thiếu một thông báo thì không ai biết cho tới khi giỗ đã qua).
 * Với dữ liệu cũ đó là lựa chọn đúng; với một sự kiện vừa được người ta bấm nút tạo thì không —
 * nên lối ghi này đóng khoảng trống lại ngay tại cửa.
 *
 * @param clanWide     việc của cả dòng họ
 * @param branchId     chi/ngành nhận nhắc, <b>bao gồm mọi nhánh con</b>; bắt buộc khi không phải
 *                     việc của cả họ
 */
public record EventScope(boolean clanWide, UUID branchId) {

    public EventScope {
        if (clanWide && branchId != null) {
            throw new IllegalArgumentException(
                    "Su kien cap dong ho khong duoc gan chi/nganh (ck_event_scope)");
        }
        if (!clanWide && branchId == null) {
            throw new IllegalArgumentException(
                    "Phai chon pham vi: hoac ca dong ho (clanWide), hoac mot chi/nganh"
                            + " (scopeBranchId). Bo trong ca hai thi he thong se nhac ca ho.");
        }
    }

}
