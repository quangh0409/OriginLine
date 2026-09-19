package vn.giapha.membership.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Mã mời: sinh, chuẩn hoá, băm. <b>Không có đường đi ngược từ băm về mã.</b>
 *
 * <h2>Mã thô rời hệ thống đúng một lần</h2>
 * Nó xuất hiện trong phản hồi của lệnh phát mã rồi biến mất khỏi bộ nhớ máy chủ. Trong CSDL chỉ có
 * {@code invitation.code_hash}. Vì vậy "quên mã" không có đường tra lại — cách đúng là <b>phát lại</b>
 * một mã mới, và thao tác phát lại tự thu hồi mã cũ ({@code ux_invitation_open_person}).
 *
 * <h2>Băm KHÔNG muối, và vì sao ở đây điều đó là đúng</h2>
 * Lưu băm thay vì lưu mã thô là đúng khuôn mẫu lưu mật khẩu. Nhưng khác mật khẩu ở một điểm quyết
 * định: tra cứu phải đi qua chỉ mục {@code ux_invitation_code_hash}, nên phép băm buộc phải
 * <b>tất định</b> — bcrypt/argon2 với muối ngẫu nhiên sẽ biến mỗi lần tra thành một lần quét toàn
 * bảng. Cái giá thông thường của băm không muối là tấn công từ điển và bảng cầu vồng; cả hai đều vô
 * hiệu ở đây vì mã do {@link SecureRandom} sinh, {@value #LENGTH} ký tự trên bảng chữ
 * {@value #RADIX} ký tự, tức {@code 32^10 = 2^50} khả năng — không có từ điển nào phủ được không
 * gian đó. Nếu về sau độ dài mã bị rút ngắn vì lý do trải nghiệm thì lập luận này <b>hết hiệu lực</b>
 * và phải thêm một pepper phía máy chủ.
 *
 * <h2>Bảng chữ Crockford Base32, và phép chuẩn hoá đi kèm</h2>
 * Người nhập mã là một cụ 70 tuổi đọc từ một tờ phiếu A6 (design 06 §5.1). Bảng chữ vì thế bỏ hẳn
 * {@code I}, {@code L}, {@code O}, {@code U}; và {@link #normalize} còn dịch ngược những nhầm lẫn
 * còn lại ({@code O → 0}, {@code I/L → 1}) cùng với việc bỏ mọi dấu gạch, khoảng trắng và chuyển
 * hoa. Hệ quả: {@code k7m2q-d9hfx}, {@code K7M2QD9HFX} và {@code K7M2Q D9HFX} là cùng một mã, còn
 * {@code O} gõ nhầm thay cho số {@code 0} vẫn vào được. Đây không phải chiều chuộng — mỗi lần gõ
 * sai là một cú điện thoại cho Trưởng chi.
 */
public final class InvitationCode {

    /** Crockford Base32 — đã loại {@code I}, {@code L}, {@code O}, {@code U}. */
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private static final int RADIX = 32;

    /** 10 ký tự = 50 bit entropy. Xem javadoc lớp trước khi đổi con số này. */
    private static final int LENGTH = 10;

    /** Chia nhóm khi in ra phiếu giấy: {@code K7M2Q-D9HFX}. */
    private static final int GROUP = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private InvitationCode() {
    }

    /**
     * Sinh một mã mới, đã chia nhóm sẵn để in.
     *
     * <p>Luôn lấy từ {@link SecureRandom}: {@code Math.random()} hay {@code new Random()} ở đây là
     * một lời mời đoán được từ thời điểm phát, và thời điểm phát thì người mời biết.</p>
     */
    public static String generate() {
        StringBuilder raw = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            raw.append(ALPHABET.charAt(RANDOM.nextInt(RADIX)));
        }
        return format(raw.toString());
    }

    /**
     * Dạng chuẩn để băm và so sánh: chỉ chữ/số, viết hoa, đã dịch ký tự dễ nhầm.
     *
     * @throws IllegalArgumentException khi chuỗi rỗng hoặc chứa ký tự ngoài bảng chữ
     */
    public static String normalize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Ma moi khong duoc rong");
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(upper.length());
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c == '-' || c == ' ' || c == '.' || c == '_') {
                continue;
            }
            char mapped = switch (c) {
                case 'O' -> '0';
                case 'I', 'L' -> '1';
                default -> c;
            };
            if (ALPHABET.indexOf(mapped) < 0) {
                throw new IllegalArgumentException("Ma moi chua ky tu khong hop le");
            }
            out.append(mapped);
        }
        if (out.length() != LENGTH) {
            throw new IllegalArgumentException("Ma moi phai co " + LENGTH + " ky tu");
        }
        return out.toString();
    }

    /** Chia nhóm cho dễ đọc; không đổi giá trị vì {@link #normalize} bỏ dấu gạch. */
    public static String format(String normalized) {
        StringBuilder out = new StringBuilder(normalized.length() + 1);
        for (int i = 0; i < normalized.length(); i++) {
            if (i > 0 && i % GROUP == 0) {
                out.append('-');
            }
            out.append(normalized.charAt(i));
        }
        return out.toString();
    }

    /**
     * SHA-256 hex thường của mã đã chuẩn hoá — đúng thứ được lưu vào {@code invitation.code_hash}.
     *
     * <p>Chuẩn hoá <b>trước</b> khi băm, luôn luôn: băm chuỗi người dùng gõ nguyên xi thì
     * {@code k7m2q-d9hfx} và {@code K7M2QD9HFX} cho ra hai băm khác nhau và một nửa số lời mời hợp
     * lệ sẽ bị từ chối.</p>
     */
    public static String hash(String raw) {
        return sha256Hex(normalize(raw));
    }

    /** Băm một định danh bất kỳ (ví dụ địa chỉ IP của người gọi) — cùng thuật toán, cùng dạng hex. */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 la thuat toan bat buoc cua moi ban JRE — khong bao gio toi day.
            throw new IllegalStateException("JRE khong co SHA-256", ex);
        }
    }
}
