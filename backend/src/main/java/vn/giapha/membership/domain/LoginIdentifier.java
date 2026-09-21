package vn.giapha.membership.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Thứ một người tự khai để <b>lập tài khoản đăng nhập</b>: địa chỉ thư <i>hoặc</i> số điện thoại.
 *
 * <h2>Vì sao phải có kiểu này, và nó sửa một mâu thuẫn trong chính sản phẩm</h2>
 * Realm Keycloak được cấu hình cho <b>cả hai</b> ở ô đăng nhập — quyết định ấy có để phục vụ các cụ
 * không có email. Nhưng hai lối <i>lập</i> tài khoản (nhận lời mời cá nhân, và đăng ký bằng mã dòng
 * họ) từng chỉ nhận email, nên một người 70 tuổi gõ đúng thứ mà màn đăng nhập sẽ nhận lại bị từ
 * chối bằng một câu lỗi về khuôn dữ liệu. Đó là mâu thuẫn của sản phẩm, không phải lỗi của người
 * dùng — và nó rơi đúng vào nhóm người mà cả luồng mời sinh ra để phục vụ.
 *
 * <h2>Không có email thì Keycloak vẫn lập được tài khoản</h2>
 * {@code username} mới là định danh bắt buộc; {@code email} là thuộc tính tuỳ chọn. Lo ngại thường
 * gặp — "cần email để gửi thư xác minh" — <b>không áp dụng ở đây</b>: hệ thống chưa có SMTP, và
 * liên kết đặt mật khẩu <i>không</i> đi qua thư mà được trả thẳng trong phản hồi HTTP của lệnh
 * nhận/đăng ký. Khi có SMTP thật, tài khoản dùng số điện thoại sẽ không nhận được thư đặt lại mật
 * khẩu, và <b>đó là giới hạn có thật cần nói với người dùng</b> — nhưng nó là giới hạn của việc
 * <i>khôi phục</i>, không phải của việc <i>đăng ký</i>.
 *
 * <h2>Chuẩn hoá số điện thoại Việt Nam</h2>
 * Người ta viết một số máy bằng mười cách: {@code 0912 345 678}, {@code 0912.345.678},
 * {@code +84912345678}, {@code 84912345678}. Cả bốn là <b>một</b> số, và nếu không quy về một dạng
 * thì cùng một người đăng ký hai lần sẽ ra hai tài khoản — rồi một trong hai được Trưởng chi ghép
 * vào phả còn cái kia thì không, và không ai hiểu vì sao đăng nhập được mà không thấy gì.
 *
 * <p>POJO thuần: không {@code @Entity}, không {@code @Component}.</p>
 */
public final class LoginIdentifier {

    /**
     * Số Việt Nam sau chuẩn hoá: bắt đầu bằng {@code 0}, tổng 9–11 chữ số.
     *
     * <p>Khoảng rộng là cố ý — di động hiện là 10 chữ số, nhưng máy bàn theo mã vùng thì ngắn hơn,
     * và một cụ ở quê hoàn toàn có thể chỉ có số bàn. Siết chặt hơn ở đây là loại đúng nhóm người
     * mà thay đổi này sinh ra để phục vụ.</p>
     */
    private static final Pattern VN_PHONE = Pattern.compile("^0\\d{8,10}$");

    /**
     * Phép kiểm email cố ý <b>lỏng</b>: có phần trước {@code @}, có phần sau, phần sau có dấu chấm.
     *
     * <p>Không đi xa hơn, vì bộ kiểm email chặt tay là một nguồn lỗi kinh điển (địa chỉ hợp lệ bị
     * từ chối) và ở đây nó <i>không bảo vệ gì</i>: chốt thật nằm ở Keycloak, nơi realm áp bộ kiểm
     * của chính nó và từ chối thẳng. Việc của lớp này chỉ là <b>phân loại</b> email với số điện
     * thoại.</p>
     */
    private static final Pattern LOOSE_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Kiểu định danh — quyết định tra cứu ở Keycloak bằng trường nào. */
    public enum Kind {
        EMAIL,
        PHONE
    }

    private final Kind kind;
    private final String value;

    private LoginIdentifier(Kind kind, String value) {
        this.kind = kind;
        this.value = value;
    }

    /**
     * Phân loại và chuẩn hoá.
     *
     * <p>Thứ tự xét: có {@code @} thì coi là email (kể cả khi phần trước toàn số), còn lại thì thử
     * đọc như số điện thoại. Đảo lại sẽ làm một địa chỉ như {@code 0912345678@example.com} đi sai
     * nhánh.</p>
     *
     * @throws IllegalArgumentException khi chuỗi rỗng, hoặc không đọc được thành email lẫn số máy
     */
    public static LoginIdentifier of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(
                    "Phai cho biet dia chi thu dien tu hoac so dien thoai de lap tai khoan");
        }
        String trimmed = raw.trim();
        if (trimmed.indexOf('@') >= 0) {
            String email = trimmed.toLowerCase(Locale.ROOT);
            if (!LOOSE_EMAIL.matcher(email).matches()) {
                throw new IllegalArgumentException("Dia chi thu dien tu khong hop le");
            }
            return new LoginIdentifier(Kind.EMAIL, email);
        }
        String phone = normalizeVietnamesePhone(trimmed);
        if (phone == null) {
            throw new IllegalArgumentException(
                    "Khong doc duoc dia chi thu dien tu hay so dien thoai tu gia tri da nhap");
        }
        return new LoginIdentifier(Kind.PHONE, phone);
    }

    public Kind kind() {
        return kind;
    }

    /** Dạng đã chuẩn hoá — <b>tên đăng nhập</b> ở Keycloak, dù là email hay số máy. */
    public String value() {
        return value;
    }

    public boolean isEmail() {
        return kind == Kind.EMAIL;
    }

    /**
     * Giá trị để ghi vào thuộc tính {@code email}; {@code null} với số điện thoại.
     *
     * <p><b>Không</b> nhét số máy vào ô email cho tiện: realm áp bộ kiểm email lên thuộc tính ấy và
     * sẽ từ chối, và kể cả nếu lọt thì mọi lối gửi thư về sau sẽ gửi vào hư không.</p>
     */
    public String emailOrNull() {
        return kind == Kind.EMAIL ? value : null;
    }

    /**
     * Bỏ mọi dấu ngăn cách và quy tiền tố quốc gia về {@code 0}.
     *
     * @return dạng chuẩn, hoặc {@code null} nếu không phải số Việt Nam đọc được
     */
    private static String normalizeVietnamesePhone(String raw) {
        StringBuilder digits = new StringBuilder(raw.length());
        boolean plus = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '+' && i == 0) {
                plus = true;
            } else if (Character.isDigit(c)) {
                digits.append(c);
            } else if (c != ' ' && c != '.' && c != '-' && c != '(' && c != ')') {
                // Mot ky tu la giua chung: khong phai so dien thoai, va doan tiep la doan bua.
                return null;
            }
        }
        String value = digits.toString();
        if (plus || value.startsWith("84")) {
            // +84912345678 / 84912345678 -> 0912345678. Dung cach nguoi Viet doc so cua chinh minh.
            value = "0" + value.substring(value.startsWith("84") ? 2 : 0);
        }
        return VN_PHONE.matcher(value).matches() ? value : null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LoginIdentifier id && kind == id.kind && value.equals(id.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /** Không in ra giá trị: cả email lẫn số máy đều là dữ liệu cá nhân (Nghị định 13/2023). */
    @Override
    public String toString() {
        return "LoginIdentifier[" + kind + "]";
    }
}
