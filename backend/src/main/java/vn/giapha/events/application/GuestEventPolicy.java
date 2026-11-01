package vn.giapha.events.application;

import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventSubject;

/**
 * Khách vãng lai được thấy sự kiện nào — quyết định của chủ dự án ngày 26/09/2026: <b>chỉ phần về
 * người đã khuất</b>.
 *
 * <h2>Vì sao không chỉ hỏi {@code subject.alive()}</h2>
 * Phép kiểm cũ ({@code guest && subject != null && subject.alive()}) để lọt hai loại bản ghi mà nay
 * {@code GET /events} đã thật sự mở cho Khách:
 * <ol>
 *   <li><b>Chủ thể không nạp được</b> ({@code personId} có nhưng {@code subject == null}). Phép cũ
 *       coi đó là "sự kiện cấp dòng họ" và cho qua — tức một lỗi dữ liệu biến thành một lần lộ.
 *       Nay: có {@code personId} mà không biết chủ thể là ai ⇒ không cho Khách xem (fail-closed).</li>
 *   <li><b>Sự kiện không gắn nhân khẩu nhưng nói về người sống</b> — một dòng "Cưới hỏi cháu
 *       Minh" hay "Họp họ tại nhà anh Tư" nhập tay (V18) không có {@code personId}, và
 *       {@code title}/{@code location}/{@code description} là chữ tự do. Không có cách nào đọc
 *       chữ tự do để biết nó nói về ai. Nên với sự kiện không gắn nhân khẩu, Khách chỉ thấy các
 *       <b>lễ cúng tổ tiên</b> ({@link vn.giapha.events.domain.EventType#isAncestralRite()}: giỗ
 *       Tổ, tế lễ, tảo mộ…) — thứ theo định nghĩa là về người đã khuất.</li>
 * </ol>
 *
 * <p>Cái giá, nói thẳng: Khách không thấy lịch họp họ và khánh thành từ đường, dù đó là việc chung
 * của dòng họ chứ không phải dữ liệu cá nhân. Bên thua ở đây ("việc họ là công khai") có lý, nhưng
 * nó đòi phân biệt được chữ tự do nào nhắc tới người sống — điều hệ thống không làm được. Mở rộng
 * về sau thì mở bằng một cờ "công bố cho Khách" do người nhập chủ động bật, không phải bằng cách nới
 * luật ở đây.</p>
 *
 * <p>Các loại về người sống ({@code SINH_NHAT}, {@code MUNG_THO}, {@code CUOI_HOI}) bị chặn cả khi
 * chủ thể được ghi là đã khuất: dữ liệu như thế là mâu thuẫn, và bản ghi mâu thuẫn không được là
 * thứ đầu tiên Khách nhìn thấy.</p>
 */
final class GuestEventPolicy {

    private GuestEventPolicy() {
    }

    /**
     * @param subject chủ thể đã nạp; {@code null} khi sự kiện không gắn nhân khẩu <i>hoặc</i> khi
     *                nạp không được — hai ca được phân biệt bằng {@code event.personId()}
     */
    static boolean visibleToGuest(Event event, EventSubject subject) {
        if (event.type().isAboutLivingPerson()) {
            return false;
        }
        if (event.personId() != null) {
            return subject != null && !subject.alive();
        }
        return event.type().isAncestralRite();
    }
}
