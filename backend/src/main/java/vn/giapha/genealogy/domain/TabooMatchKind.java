package vn.giapha.genealogy.domain;

/**
 * Mức độ va chạm kỵ húy — quyết định cảnh báo mạnh hay yếu.
 */
public enum TabooMatchKind {

    /** Trùng toàn bộ tên húy. Cảnh báo mạnh nhất. */
    EXACT,

    /** Trùng phần tên chính (bỏ họ và chữ đệm). */
    GIVEN_NAME,

    /** Chỉ trùng khi bỏ dấu (Duc vs Đức) — dễ gây cảnh báo giả, có thể tắt bằng cấu hình. */
    UNACCENTED
}
