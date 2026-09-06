package vn.giapha.kinship.domain;

/**
 * Trạng thái một lần tra danh xưng — khớp trường {@code status} của {@code KinshipResult}
 * trong {@code contracts/openapi.yaml}.
 */
public enum KinshipStatus {

    /** Khớp được một luật cụ thể — có {@code title}. */
    RESOLVED,
    /** Không tìm được tổ chung và cũng không có cạnh trực tiếp/hôn nhân nào nối hai người. */
    NO_COMMON_ANCESTOR,
    /** Có dữ kiện quan hệ nhưng bộ luật đang hiệu lực chưa phủ tổ hợp này. */
    NO_MATCHING_RULE,
    /** {@code from} trùng {@code to}. */
    SELF
}
