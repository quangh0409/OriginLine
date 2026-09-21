package vn.giapha.media.domain;

import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;

/**
 * Đọc <b>thời lượng</b> một video từ header container — không ffmpeg, không chuyển mã, không đọc
 * hết tệp.
 *
 * <h2>Vì sao lớp này phải tồn tại</h2>
 * Quyết định đã chốt của chủ dự án là <b>nhận tệp gốc, không chuyển mã</b>. Nhưng cũng chính quyết
 * định ấy đòi một <b>trần thời lượng</b>, và một trần chỉ có nghĩa nếu đại lượng nó chặn <i>đo
 * được</i>. Hai lối sai đã bị loại:
 * <ul>
 *   <li><b>Tin con số client khai.</b> Bằng không có trần: ai gửi được JSON thì gửi được số 5.</li>
 *   <li><b>Gọi ffmpeg.</b> Chủ dự án đã nói không, và nó kéo theo một tiến trình con chạy trên
 *       tệp không tin cậy — một bề mặt tấn công hoàn toàn khác.</li>
 * </ul>
 * Lối thứ ba là lối này: đọc <b>vài chục KB</b> ở đầu (và nếu cần, ở cuối) đối tượng qua một yêu
 * cầu {@code GET} có khoảng, rồi bóc thời lượng ra khỏi siêu dữ liệu mà chính container đã ghi
 * sẵn. Rẻ, không phụ thuộc tiến trình ngoài, và đúng với mọi tệp do điện thoại hay công cụ xuất
 * video thông thường tạo ra.
 *
 * <h2>Đây là phép QUÉT HEADER, không phải phép giải mã container đầy đủ — nói thẳng giới hạn</h2>
 * <ul>
 *   <li><b>MP4:</b> tìm chuỗi {@code mvhd} rồi đọc {@code timescale}/{@code duration} ngay sau đó.
 *       Không đi cây hộp từ gốc, vì hộp {@code moov} của tệp chưa "faststart" nằm ở <b>cuối</b>
 *       tệp — nên ta quét đầu trước, không thấy thì quét cuối. Một phép đi cây đúng chuẩn sẽ phải
 *       nhảy nhiều lượt {@code GET} có khoảng và vẫn kết thúc ở đúng hai chỗ ấy.</li>
 *   <li><b>WebM:</b> tìm mã phần tử EBML {@code 0x2AD7B1} ({@code TimecodeScale}) và
 *       {@code 0x4489} ({@code Duration}) trong phần đầu. Phần tử {@code Info} của một tệp WebM
 *       thực tế luôn nằm ngay sau header, trước cụm {@code Cluster} đầu tiên.</li>
 * </ul>
 *
 * <p><b>Đọc không ra thì từ chối</b> ({@link MediaProblemCodes#MEDIA_DURATION_UNKNOWN}), không
 * "cho qua vì chắc là ngắn". Đây là lựa chọn đóng-khi-nghi-ngờ, cùng tinh thần với
 * {@code PrivacyConsent} mặc định {@code PRIVATE}: một trần im lặng bỏ qua tệp nó không hiểu là
 * một trần chỉ chặn được người trung thực.</p>
 */
public final class VideoHeaderProbe {

    /**
     * Số byte đọc ở mỗi đầu: <b>256 KiB</b>.
     *
     * <p>Hộp {@code moov} của một tệp điện thoại quay 2 phút rơi vào vài chục KB (bảng chỉ mục
     * khung hình). 256 KiB chứa trọn nó ở gần như mọi tệp thật, mà vẫn nhỏ hơn nhiều so với cái
     * giá của một lượt tải toàn tệp về máy chủ — thứ mà cả kiến trúc này được dựng để tránh.</p>
     */
    public static final int PROBE_BYTES = 256 * 1024;

    private VideoHeaderProbe() {
    }

    /**
     * @param contentType kiểu MIME <b>đã dò bằng chữ ký byte</b> ({@link MediaSignature})
     * @param head        {@value #PROBE_BYTES} byte đầu của đối tượng
     * @param tail        {@value #PROBE_BYTES} byte cuối; có thể {@code null} khi tệp đủ nhỏ để
     *                    {@code head} đã phủ hết
     * @return thời lượng theo mili-giây
     * @throws MediaRejectedException khi không đọc được
     */
    public static long durationMillis(String contentType, byte[] head, byte[] tail) {
        OptionalLong found = switch (contentType) {
            case MediaSignature.VIDEO_MP4 -> firstOf(mp4(head), mp4(tail));
            case MediaSignature.VIDEO_WEBM -> firstOf(webm(head), webm(tail));
            default -> OptionalLong.empty();
        };
        if (found.isEmpty() || found.getAsLong() <= 0) {
            throw new MediaRejectedException(MediaProblemCodes.MEDIA_DURATION_UNKNOWN,
                    "Không đọc được thời lượng của đoạn video này, nên hệ thống không thể kiểm"
                            + " được nó có vượt giới hạn " + MediaLimits.MAX_VIDEO_SECONDS
                            + " giây hay không. Hãy mở video bằng một ứng dụng dựng phim rồi xuất"
                            + " lại thành MP4 (H.264) và tải lên lần nữa.");
        }
        return found.getAsLong();
    }

    private static OptionalLong firstOf(OptionalLong a, OptionalLong b) {
        return a.isPresent() ? a : b;
    }

    // =====================================================================================
    // MP4 / ISO-BMFF — hộp mvhd
    // =====================================================================================

    /**
     * Bố cục {@code mvhd} ngay sau bốn byte tên hộp:
     * <pre>
     *   version(1) flags(3)
     *   version == 0 : creation(4) modification(4) timescale(4) duration(4)
     *   version == 1 : creation(8) modification(8) timescale(4) duration(8)
     * </pre>
     * {@code timescale} là số đơn vị trên một giây; {@code duration} tính theo đơn vị ấy.
     */
    private static OptionalLong mp4(byte[] buf) {
        if (buf == null) {
            return OptionalLong.empty();
        }
        int at = indexOf(buf, "mvhd");
        if (at < 0) {
            return OptionalLong.empty();
        }
        int p = at + 4;
        if (p + 1 > buf.length) {
            return OptionalLong.empty();
        }
        int version = buf[p] & 0xFF;
        p += 4;                                   // bo qua version(1) + flags(3)
        long timescale;
        long duration;
        if (version == 1) {
            p += 16;                              // creation(8) + modification(8)
            if (p + 12 > buf.length) {
                return OptionalLong.empty();
            }
            timescale = u32(buf, p);
            duration = u64(buf, p + 4);
        } else {
            p += 8;                               // creation(4) + modification(4)
            if (p + 8 > buf.length) {
                return OptionalLong.empty();
            }
            timescale = u32(buf, p);
            duration = u32(buf, p + 4);
        }
        if (timescale <= 0 || duration <= 0) {
            return OptionalLong.empty();
        }
        // 0xFFFFFFFF la gia tri "khong xac dinh" ma mot so cong cu ghi ra khi dang ghi do dang.
        if (version == 0 && duration == 0xFFFFFFFFL) {
            return OptionalLong.empty();
        }
        if (duration > Long.MAX_VALUE / 1000L) {
            // Tran so: coi nhu dai vo han. Tra ve mot gia tri chac chan vuot tran thoi luong de
            // nguoi dung nhan cau "video dai qua", khong phai cau "khong doc duoc thoi luong".
            return OptionalLong.of(Long.MAX_VALUE / 1000L);
        }
        return OptionalLong.of(duration * 1000L / timescale);
    }

    // =====================================================================================
    // WebM / Matroska — Info › TimecodeScale + Duration
    // =====================================================================================

    /**
     * {@code Duration} là số dấu phẩy động (4 hoặc 8 byte) tính theo đơn vị {@code TimecodeScale}
     * nano-giây. {@code TimecodeScale} mặc định là 1 000 000 ns (= 1 ms) và hầu hết tệp giữ mặc
     * định ấy, nhưng ta vẫn đọc nếu nó có mặt — một tệp đặt 100 000 mà bị hiểu theo mặc định sẽ ra
     * thời lượng lớn gấp 10 và bị từ chối oan.
     */
    private static OptionalLong webm(byte[] buf) {
        if (buf == null) {
            return OptionalLong.empty();
        }
        long timecodeScale = 1_000_000L;
        int ts = indexOf(buf, new byte[] {0x2A, (byte) 0xD7, (byte) 0xB1});
        if (ts >= 0) {
            long v = ebmlUnsigned(buf, ts + 3);
            if (v > 0) {
                timecodeScale = v;
            }
        }
        int at = indexOf(buf, new byte[] {0x44, (byte) 0x89});
        if (at < 0) {
            return OptionalLong.empty();
        }
        int p = at + 2;
        if (p >= buf.length) {
            return OptionalLong.empty();
        }
        int size = ebmlSmallSize(buf[p] & 0xFF);
        p++;
        double raw;
        if (size == 4 && p + 4 <= buf.length) {
            raw = Float.intBitsToFloat((int) u32(buf, p));
        } else if (size == 8 && p + 8 <= buf.length) {
            raw = Double.longBitsToDouble(u64(buf, p));
        } else {
            return OptionalLong.empty();
        }
        if (!(raw > 0) || Double.isInfinite(raw) || Double.isNaN(raw)) {
            return OptionalLong.empty();
        }
        double millis = raw * timecodeScale / 1_000_000.0d;
        // Bien tinh tao: mot cum byte ngau nhien trung 0x4489 co the ra mot so vo ly. Gioi han o
        // 24 gio — qua no thi coi nhu khong doc duoc, va duong tran se tu choi vi khong doc duoc
        // chu khong vi "dai qua". Hai cau tu choi khac nhau dan nguoi dung di hai huong khac nhau.
        if (millis < 1 || millis > 24L * 3600 * 1000) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.round(millis));
    }

    /** Kích thước của một giá trị EBML mã hoá theo vint — chỉ cần nhánh 1 byte đầu. */
    private static int ebmlSmallSize(int first) {
        if ((first & 0x80) != 0) {
            return first & 0x7F;
        }
        return -1;
    }

    /** Đọc một số nguyên EBML (vint size rồi big-endian bytes) tại {@code p}. */
    private static long ebmlUnsigned(byte[] buf, int p) {
        if (p >= buf.length) {
            return -1;
        }
        int size = ebmlSmallSize(buf[p] & 0xFF);
        if (size <= 0 || size > 8 || p + 1 + size > buf.length) {
            return -1;
        }
        long v = 0;
        for (int i = 0; i < size; i++) {
            v = (v << 8) | (buf[p + 1 + i] & 0xFF);
        }
        return v;
    }

    // =====================================================================================
    // Tiện ích
    // =====================================================================================

    private static long u32(byte[] b, int p) {
        return ((long) (b[p] & 0xFF) << 24) | ((b[p + 1] & 0xFF) << 16)
                | ((b[p + 2] & 0xFF) << 8) | (b[p + 3] & 0xFF);
    }

    private static long u64(byte[] b, int p) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (b[p + i] & 0xFF);
        }
        return v;
    }

    private static int indexOf(byte[] haystack, String needle) {
        return indexOf(haystack, needle.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
