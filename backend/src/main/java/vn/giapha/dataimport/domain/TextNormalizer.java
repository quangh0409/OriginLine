package vn.giapha.dataimport.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Ba phép chuẩn hoá <b>bắt buộc</b> chạy trên mọi ô chữ ngay khi đọc, <b>trước mọi phép so sánh</b>.
 *
 * <h2>1. Unicode về NFC — lỗi khó chịu nhất trong nhóm này</h2>
 * macOS và một số bộ gõ sinh NFD: chữ "ễ" là "e" cộng hai dấu tổ hợp. Hai chuỗi <b>trông giống hệt
 * nhau trên màn hình</b> nhưng {@code equals()} trả {@code false}, {@code vn_unaccent} ra kết quả
 * khác, và cột {@code Mã cha} trỏ trượt sang không đâu cả. Mắt người không nhìn ra được, nên nếu
 * không chuẩn hoá ở đây thì triệu chứng sẽ là "tôi gõ đúng mã cha mà máy bảo không tìm thấy" và
 * không ai giải thích nổi.
 *
 * <h2>2. Dọn khoảng trắng</h2>
 * {@code U+00A0} (khoảng trắng cứng) sinh ra hàng loạt khi dán từ Word; {@code U+200B} và
 * {@code U+FEFF} đến từ việc dán qua trình duyệt. Cả ba đều vô hình và cả ba đều làm hỏng phép so
 * khớp mã.
 *
 * <h2>3. Riêng cột Mã: nâng hoa và bỏ hết khoảng trắng</h2>
 * Để {@code at-02-001}, {@code AT-02-001} và {@code AT - 02 - 001} là một. Trưởng chi gõ tay 400
 * dòng thì ba biến thể ấy chắc chắn cùng xuất hiện trong một tệp.
 *
 * <p><b>Cố ý KHÔNG làm:</b> không sửa chính tả, không đoán dấu, không viết hoa chữ cái đầu của tên
 * người. Máy không tự sửa con số của người — và tên riêng tiếng Việt có đủ ngoại lệ để mọi phép
 * "chuẩn hoá cho đẹp" đều sai với ai đó.</p>
 */
public final class TextNormalizer {

    /** Khoảng trắng cứng, dấu vết của việc dán từ Word. */
    private static final char NBSP = ' ';

    /** Khoảng trắng rộng bằng chữ, hay lọt vào khi dán từ trang web tiếng Nhật/Trung. */
    private static final char IDEOGRAPHIC_SPACE = '　';

    private TextNormalizer() {
    }

    /**
     * Chuẩn hoá một ô chữ bất kỳ.
     *
     * @return chuỗi đã NFC, đã gộp khoảng trắng, đã cắt hai đầu; {@code null} khi ô rỗng
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = stripInvisible(raw);
        s = Normalizer.normalize(s, Normalizer.Form.NFC);
        s = s.replace(NBSP, ' ').replace(IDEOGRAPHIC_SPACE, ' ');
        s = s.replaceAll("\\s+", " ").trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * Chuẩn hoá riêng cột {@code Mã}: như {@link #normalize(String)} rồi nâng hoa và bỏ sạch
     * khoảng trắng.
     *
     * <p>Dùng {@link Locale#ROOT} chứ không phải locale mặc định: với locale {@code tr} thì
     * {@code "i".toUpperCase()} ra {@code "İ"}, và một máy chủ đặt sai locale sẽ làm mọi mã chứa
     * chữ i không khớp nhau nữa.</p>
     */
    public static String normalizeCode(String raw) {
        String s = normalize(raw);
        if (s == null) {
            return null;
        }
        s = s.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return s.isEmpty() ? null : s;
    }

    /** {@code true} nếu chuỗi đã ở dạng NFC — dùng trong test ghim, không dùng trong luồng chính. */
    public static boolean laNfc(String s) {
        return s == null || Normalizer.isNormalized(s, Normalizer.Form.NFC);
    }

    private static String stripInvisible(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            // U+200B zero-width space, U+200C/D zero-width joiner, U+FEFF BOM.
            if (c == '​' || c == '‌' || c == '‍' || c == '﻿') {
                continue;
            }
            // Ky tu dieu khien (tru tab/xuong dong, von da bi gop thanh khoang trang o buoc sau).
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
