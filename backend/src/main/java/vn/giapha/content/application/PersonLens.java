package vn.giapha.content.application;

import java.util.Map;
import java.util.UUID;
import vn.giapha.genealogy.application.DisclosedPerson;

/**
 * Kết quả một lượt hỏi {@code genealogy} về một lô nhân khẩu, <b>đã lọc theo người gọi hiện
 * tại</b>.
 *
 * <h2>Vì sao context này không bao giờ đọc thẳng bảng {@code person}</h2>
 * Đọc thẳng cho nhanh là cách <b>tên người còn sống rò ra qua một bài viết công khai</b>: chỉ cần
 * một bài của một người đang sống nằm trên trang chủ là cái tên ấy đi ra cho bất kỳ ai mở trang.
 * Đây đúng là lỗ mà {@code RelationshipSummaryLoader} đã chặn ở màn "Quan hệ" ("tên người còn sống
 * rò ra qua hồ sơ công khai của một cụ đã khuất"), chỉ đổi lối vào.
 *
 * <h2>Vắng mặt nghĩa là "không được biết người ấy tồn tại", không phải "không có dữ liệu"</h2>
 * {@code PersonDisclosureService.hoSo} bỏ khỏi kết quả những nhân khẩu mà người gọi không được
 * thấy. Ở đây điều đó thành {@link #displayName} trả {@code null} và {@link #honourVisible} trả
 * {@code false} — <b>fail-closed</b>, và không phân biệt được với "không có dữ liệu", đúng nguyên
 * tắc của BA v2 §10.
 *
 * @param byId hồ sơ đã lọc, tra theo {@code person.id}
 */
public record PersonLens(Map<UUID, DisclosedPerson> byId) {

    public PersonLens {
        byId = byId == null ? Map.of() : Map.copyOf(byId);
    }

    public static PersonLens empty() {
        return new PersonLens(Map.of());
    }

    /**
     * Tên hiển thị đã qua bộ lọc riêng tư; {@code null} khi người gọi không được thấy nhân khẩu ấy.
     *
     * <p>Ca {@code null} có thật và không hiếm: hồ sơ tác giả bị xoá mềm sau khi bài đã đăng. Bài
     * viết <b>vẫn ở lại</b> — nó là tiếng nói của dòng họ chứ không phải tài sản riêng của một hồ
     * sơ — nhưng chỗ ghi tên thì trống. Giao diện phải chịu được điều đó.</p>
     */
    public String displayName(UUID personId) {
        DisclosedPerson person = tra(personId);
        return person == null ? null : person.thuongGoi();
    }

    /**
     * Người gọi có được xem <b>vinh danh</b> của nhân khẩu này không (nhóm trường riêng tư thứ sáu,
     * V17).
     *
     * <p>Câu trả lời đến nguyên vẹn từ {@code genealogy.PrivacyTierService}; context này không
     * đọc {@code privacy_consent} và không diễn giải nó. Đó là toàn bộ ý nghĩa của thiết kế:
     * {@code content} nhận lại <i>kết luận</i> của bộ lọc, chứ không chép lại <i>luật</i> của nó —
     * hai bản luật riêng tư thì sớm muộn cũng lệch, và triệu chứng sẽ là hồ sơ che còn trang chủ
     * thì không.</p>
     */
    public boolean honourVisible(UUID personId) {
        DisclosedPerson person = tra(personId);
        return person != null && person.vinhDanhHienDuoc();
    }

    /** Người gọi có được biết nhân khẩu này tồn tại không. */
    public boolean visible(UUID personId) {
        return tra(personId) != null;
    }

    /**
     * Tra một hồ sơ, <b>chịu được khoá {@code null}</b>.
     *
     * <p>Không thừa: {@code reviewedBy} của một bài chưa ai duyệt là {@code null}, nên khoá nhân
     * khẩu suy ra từ nó cũng {@code null} — và {@code Map.copyOf(...).get(null)} <b>ném NPE</b>
     * (map bất biến của JDK gọi {@code key.hashCode()} trước). Đúng loại lỗi chỉ nổ ở đường bình
     * thường nhất: mở một bản nháp.</p>
     */
    private DisclosedPerson tra(UUID personId) {
        return personId == null ? null : byId.get(personId);
    }
}
