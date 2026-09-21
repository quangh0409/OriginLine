package vn.giapha.media.domain;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Nhận dạng tệp bằng <b>chữ ký byte</b>, không bằng đuôi tệp và không bằng {@code Content-Type}.
 *
 * <h2>Đây là bản sao có chủ ý của {@code dataimport.XlsxGuards#requireXlsx}</h2>
 * Cùng một bài học, ở một context khác: một tệp {@code .jpg} do người dùng đặt tên không nói lên
 * điều gì, và ở đường này nó còn yếu hơn — tệp đi <b>thẳng lên MinIO</b> qua một URL đã ký, nên
 * backend không hề thấy tên tệp lẫn {@code Content-Type} mà trình duyệt gửi. Thứ duy nhất đáng tin
 * là những byte đầu tiên mà chính backend đọc ngược về từ kho.
 *
 * <h2>Vì sao danh sách định dạng hẹp đến thế</h2>
 * Chỉ <b>JPEG · PNG · WebP · MP4 · WebM</b>. Mỗi định dạng thêm vào là một bộ giải mã nữa chạy
 * trên trình duyệt của một cụ 70 tuổi, và là một lớp nữa trong bề mặt tấn công. Ba thứ bị loại có
 * tên và có lý do:
 * <ul>
 *   <li><b>HEIC/HEIF</b> (ảnh mặc định của iPhone) — chỉ Safari hiển thị được. Nhận nó nghĩa là
 *       ảnh của bác dùng iPhone hiện ra ô vỡ trên máy của mọi người còn lại, mà <b>không</b> có
 *       thông báo lỗi nào. Từ chối kèm đúng câu hướng dẫn đổi cài đặt máy ảnh — cùng khuôn với
 *       câu ".xls thì mở Excel lưu lại thành .xlsx" của {@code XlsxGuards}.</li>
 *   <li><b>GIF</b> — ảnh động là một thể loại nội dung khác, và một GIF 8 MiB phát vòng lặp trên
 *       trang chủ của dòng họ là thứ không ai xin.</li>
 *   <li><b>MOV/QuickTime</b> — dù cũng là hộp ISO-BMFF như MP4, nhãn {@code qt  } thường đi kèm
 *       codec (ProRes, HEVC) mà Chrome/Firefox không phát. Không chuyển mã thì nhận vào là hứa
 *       một thứ không giữ được.</li>
 * </ul>
 *
 * <p><b>Quét vi-rút vẫn ngoài phạm vi</b> — nêu ra để biết mình đang chấp nhận gì, đúng như
 * {@code XlsxGuards} đã nêu.</p>
 */
public final class MediaSignature {

    /** Số byte đầu cần đọc ngược về từ kho để dò. 64 đã thừa cho mọi chữ ký dưới đây. */
    public static final int SNIFF_BYTES = 64;

    public static final String IMAGE_JPEG = "image/jpeg";
    public static final String IMAGE_PNG = "image/png";
    public static final String IMAGE_WEBP = "image/webp";
    public static final String VIDEO_MP4 = "video/mp4";
    public static final String VIDEO_WEBM = "video/webm";

    private MediaSignature() {
    }

    /**
     * Loại tệp đã nhận dạng được.
     *
     * @param kind        {@link MediaKind} suy ra từ chữ ký
     * @param contentType kiểu MIME <b>do ta dò ra</b>, thứ sẽ được ghi vào {@code media_asset} và
     *                    dùng làm {@code Content-Type} khi ký URL đọc
     */
    public record Detected(MediaKind kind, String contentType) {
    }

    /**
     * Dò loại tệp từ những byte đầu.
     *
     * @param head  {@value #SNIFF_BYTES} byte đầu của đối tượng (ít hơn cũng được)
     * @param label tên/khoá dùng trong thông báo lỗi
     * @throws MediaRejectedException khi không nhận ra, hoặc nhận ra một định dạng bị loại
     */
    public static Detected detect(byte[] head, String label) {
        if (head == null || head.length < 12) {
            throw reject("Tệp " + label + " rỗng hoặc quá ngắn để là một tấm ảnh/đoạn video."
                    + " Hãy chọn lại tệp rồi tải lên lần nữa.");
        }

        // --- Ảnh ---
        // JPEG: FF D8 FF (SOI + marker dau tien). Du de phan biet, va khong co dinh dang nao khac
        // dung ba byte nay.
        if (u(head[0]) == 0xFF && u(head[1]) == 0xD8 && u(head[2]) == 0xFF) {
            return new Detected(MediaKind.IMAGE, IMAGE_JPEG);
        }
        // PNG: 89 'P' 'N' 'G' 0D 0A 1A 0A — tam byte, chu y hai byte CRLF co chu dich la de phat
        // hien mot phep truyen FTP kieu text da lam hong tep.
        if (u(head[0]) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                && u(head[4]) == 0x0D && u(head[5]) == 0x0A && u(head[6]) == 0x1A
                && u(head[7]) == 0x0A) {
            return new Detected(MediaKind.IMAGE, IMAGE_PNG);
        }
        // WebP: 'RIFF' ???? 'WEBP'. Phai kiem CA HAI cum — chi kiem 'RIFF' thi mot tep .wav cung
        // di qua.
        if (ascii(head, 0, "RIFF") && ascii(head, 8, "WEBP")) {
            return new Detected(MediaKind.IMAGE, IMAGE_WEBP);
        }
        // GIF — nhan ra de noi mot cau ro rang thay vi "khong phai anh".
        if (ascii(head, 0, "GIF87a") || ascii(head, 0, "GIF89a")) {
            throw reject("Tệp " + label + " là ảnh động GIF. Trang của dòng họ chỉ nhận ảnh tĩnh"
                    + " (JPG, PNG, WebP) và video (MP4, WebM).");
        }

        // --- ISO Base Media (MP4 / MOV / HEIC) : byte 4..7 la 'ftyp', nhan hieu o byte 8..11 ---
        if (ascii(head, 4, "ftyp")) {
            String brand = new String(head, 8, 4, StandardCharsets.ISO_8859_1)
                    .toLowerCase(Locale.ROOT).trim();
            if (HEIF_BRANDS.contains(brand)) {
                throw reject("Tệp " + label + " là ảnh HEIC của iPhone — phần lớn trình duyệt không"
                        + " mở được, nên tải lên sẽ thành một ô vỡ với mọi người trong họ."
                        + " Trên iPhone mở Cài đặt › Máy ảnh › Định dạng › chọn \"Tương thích"
                        + " nhất\", chụp lại (hoặc mở ảnh, bấm Chia sẻ › Sao chép ảnh rồi dán vào"
                        + " một ứng dụng lưu thành JPG), sau đó tải lên lần nữa.");
            }
            if (MP4_BRANDS.contains(brand)) {
                return new Detected(MediaKind.VIDEO, VIDEO_MP4);
            }
            throw reject("Tệp " + label + " là một video định dạng \"" + brand + "\" mà trình duyệt"
                    + " không chắc phát được. Hãy xuất lại thành MP4 (H.264) hoặc WebM.");
        }

        // --- WebM / Matroska: EBML 1A 45 DF A3, roi DocType phai la 'webm' ---
        if (u(head[0]) == 0x1A && u(head[1]) == 0x45 && u(head[2]) == 0xDF && u(head[3]) == 0xA3) {
            // DocType nam trong header EBML, luon o vai chuc byte dau. Doi 'matroska' thuan (khong
            // phai webm) vi cung ly do voi MOV: codec ben trong co the la thu trinh duyet khong
            // phat duoc.
            if (contains(head, "webm")) {
                return new Detected(MediaKind.VIDEO, VIDEO_WEBM);
            }
            throw reject("Tệp " + label + " là Matroska (.mkv) chứ không phải WebM. Hãy xuất lại"
                    + " thành MP4 (H.264) hoặc WebM.");
        }

        throw reject("Tệp " + label + " không phải ảnh hay video mà hệ thống nhận."
                + " Chỉ nhận ảnh JPG, PNG, WebP và video MP4, WebM.");
    }

    /**
     * Nhãn hiệu ISO-BMFF được nhận là MP4.
     *
     * <p>Danh sách này hẹp có chủ ý. {@code isom}/{@code iso2}/{@code mp41}/{@code mp42} là nhãn
     * của tệp xuất từ máy tính; {@code avc1} và {@code mp4v} là H.264/MPEG-4 Visual; {@code M4V }
     * là nhãn iTunes nhưng vẫn là H.264. {@code qt  } (QuickTime/.mov) <b>không</b> có trong danh
     * sách — xem javadoc của lớp.</p>
     */
    private static final java.util.Set<String> MP4_BRANDS = java.util.Set.of(
            "isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "mp4v", "m4v", "dash");

    /** Nhãn hiệu của ảnh HEIF/HEIC — nhận ra chỉ để từ chối kèm hướng dẫn. */
    private static final java.util.Set<String> HEIF_BRANDS = java.util.Set.of(
            "heic", "heix", "heim", "heis", "hevc", "hevx", "mif1", "msf1", "avif", "avis");

    private static MediaRejectedException reject(String message) {
        return new MediaRejectedException(MediaProblemCodes.MEDIA_BAD_SIGNATURE, message);
    }

    private static int u(byte b) {
        return b & 0xFF;
    }

    private static boolean ascii(byte[] buf, int offset, String expected) {
        if (buf.length < offset + expected.length()) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if (buf[offset + i] != (byte) expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(byte[] buf, String needle) {
        byte[] n = needle.getBytes(StandardCharsets.ISO_8859_1);
        outer:
        for (int i = 0; i + n.length <= buf.length; i++) {
            for (int j = 0; j < n.length; j++) {
                if (buf[i + j] != n[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
