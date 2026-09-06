package vn.giapha.genealogy.domain;

/**
 * Lớp tên của người Việt truyền thống. Một nhân khẩu có <b>nhiều tên</b>, không chỉ một —
 * đây là điểm khác biệt nền tảng so với mô hình "một cột full_name" của phần mềm phương Tây.
 *
 * <p>Khớp 1-1 với {@code ck_person_name_type} ở {@code V2__core.sql} và enum {@code NameType}
 * của {@code contracts/openapi.yaml}. Thêm giá trị ở đây là thay đổi contract.</p>
 */
public enum NameType {

    /** Tên húy — tên thật, kiêng gọi khi người đó là bậc trên. Căn cứ cảnh báo kỵ húy (FR-1.6). */
    HUY,

    /** Tên tự — tên chữ, đặt khi trưởng thành. */
    TU,

    /** Tên hiệu — bút hiệu, biệt hiệu tự đặt. */
    HIEU,

    /** Tên thụy — đặt <b>sau khi mất</b>, dùng trong tế lễ và văn khấn. */
    THUY,

    /** Tên thường gọi hằng ngày, thường trùng tên khai sinh hiện đại. */
    THUONG_GOI,

    /** Pháp danh (người quy y Phật giáo). */
    PHAP_DANH
}
