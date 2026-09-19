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

    /**
     * Tài khoản đăng nhập được nhưng chưa sẵn sàng dùng — chưa có {@code app_user}, hoặc đã có mà
     * chưa được ghép với một nhân khẩu trong phả.
     *
     * <p>Tách khỏi {@link #CONFLICT} dù cùng HTTP 409: {@code CONFLICT} đang mang nghĩa "dữ liệu
     * vừa bị người khác sửa" (khoá lạc quan) và giao diện phản ứng bằng "tải lại trang". Ở đây
     * tải lại trang không giúp được gì — phải có người khác ghép tài khoản.</p>
     */
    public static final URI ACCOUNT_NOT_PROVISIONED = URI.create(BASE + "account-not-provisioned");
    public static final URI INTERNAL = URI.create(BASE + "internal-error");

    private ProblemTypes() {
    }
}
