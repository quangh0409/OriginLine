package vn.giapha.membership.application;

import java.util.List;

/**
 * Màn phát mã của Hội đồng: danh sách mã <b>cộng mẫu số của bộ đếm</b>.
 *
 * <h2>Vì sao endpoint này bọc, trong khi {@code GET /invitations} trả mảng phẳng</h2>
 * Cùng lý do với {@link MyPersonClaims}: {@link #clanLivingPersonCount()} <b>không thuộc về bất kỳ
 * mã nào</b> trong danh sách. Gắn nó vào từng phần tử là lặp một giá trị toàn cục lên n dòng rồi
 * để chúng có cơ hội lệch nhau; đặt nó ở một endpoint thứ hai là bắt màn hình gọi hai lượt cho một
 * câu trả lời.
 *
 * @param clanLivingPersonCount số người <b>đang sống</b> trong cả dòng họ.
 *        <p>Lập luận của design 07 §1.2 là <i>"mã đã dùng 400 lần trong khi dòng họ có 600
 *        người"</i>, và vế thứ hai mới làm vế thứ nhất có nghĩa: <b>một bộ đếm không có mẫu số thì
 *        không ai phán xét được</b>. Hội đồng nhìn con số 400 trần trụi sẽ không biết nên lo hay
 *        không, và chốt 3 — chốt được gọi là quan trọng nhất — trở thành một con số trang trí.</p>
 *        <p>Đây là một phép đếm, không phải một báo cáo dân số: nó không phân theo chi, không phân
 *        theo đời, và không tiết lộ ai cả.</p>
 */
public record ClanInviteList(List<ClanInviteView> invites, int clanLivingPersonCount) {

    public ClanInviteList {
        invites = invites == null ? List.of() : List.copyOf(invites);
    }
}
