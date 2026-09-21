package vn.giapha.membership.domain;

import java.util.UUID;
import vn.giapha.shared.vo.BranchPath;

/**
 * Một chi/ngành ở mức đủ để màn "đang chờ duyệt" trả lời câu hỏi <b>"đang chờ ai"</b>.
 *
 * <h2>Chức danh của một CHI, không phải tên của một người</h2>
 * {@link ClanTitle#of} dựng từ đây ra "Trưởng Chi Giáp" / "Tộc trưởng" — một <b>vai</b>, không phải
 * một con người. Đó là chủ ý: một UUID không trả lời được "đang chờ ai", nhưng tên và số điện
 * thoại của Trưởng chi thì <b>không được</b> đi ra màn này (design 06 §7). Một người vừa đăng ký,
 * chưa được duyệt, chưa ở trong phả mà đã đọc được danh bạ ban quản trị là một bề mặt không ai
 * xin.
 *
 * <p>Tên chi là dữ liệu công khai — nó đã nằm trong {@code BranchRef} mà cổng công khai trả cho
 * Khách.</p>
 *
 * @param kind loại chi ({@code DONG_HO}/{@code CHI}/{@code NGANH}/…), quyết định cách đọc chức danh
 * @param path {@code ltree} của chi; dùng cho phép kiểm phạm vi, <b>không</b> nhất thiết đi ra API
 */
public record BranchSummary(UUID id, String name, String kind, BranchPath path) {

    /** Chức danh dòng tộc của người đứng đầu chi này; {@code null} khi không dựng được. */
    public String clanTitle() {
        return ClanTitle.of(new ClanOffice(name, kind));
    }
}
