package vn.giapha.shared.api;

import java.net.URI;

/**
 * Định danh {@code type} của RFC 7807 Problem Details. Đây là <b>hợp đồng công khai</b> với
 * frontend: client phân nhánh xử lý theo URI này, nên chỉ được thêm mới, không đổi/xoá.
 */
public final class ProblemTypes {

    private static final String BASE = "https://giapha.vn/problems/";

    public static final URI VALIDATION = URI.create(BASE + "validation-error");
    public static final URI BUSINESS_RULE = URI.create(BASE + "business-rule-violation");
    public static final URI NOT_FOUND = URI.create(BASE + "not-found");
    public static final URI FORBIDDEN = URI.create(BASE + "forbidden");
    public static final URI UNAUTHORIZED = URI.create(BASE + "unauthorized");
    public static final URI CONFLICT = URI.create(BASE + "conflict");
    public static final URI INTERNAL = URI.create(BASE + "internal-error");

    private ProblemTypes() {
    }
}
