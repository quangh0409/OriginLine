package vn.giapha.dataimport.infrastructure.excel.template;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Tệp mẫu đã sinh xong: nội dung, tên tệp <b>có dấu tiếng Việt</b>, và câu
 * {@code Content-Disposition} để tầng REST chỉ việc gắn vào phản hồi.
 *
 * <h2>Vì sao tên tệp lại là chỗ hay hỏng nhất</h2>
 * Bên trong {@code .xlsx} là XML UTF-8 nên "Nguyễn" không bao giờ thành "Nguyá»…n" — nhưng
 * <b>tên tệp không nằm trong tệp</b>. Nó đi qua một tiêu đề HTTP, và tiêu đề HTTP theo RFC 7230 là
 * ISO-8859-1. Đặt thẳng {@code filename="Mẫu nhập liệu.xlsx"} thì trình duyệt nhận được
 * {@code MÃ¡Â»Âu nhÃ¡ÂºÂp liÃ¡Â»Âu.xlsx} và người dùng thấy một tên rác trong thư mục Tải về.
 *
 * <p>Cách chữa đúng là RFC 5987/6266: gửi <b>cả hai</b> dạng.</p>
 * <pre>
 *   Content-Disposition: attachment; filename="Mau nhap lieu - Chi At.xlsx";
 *                        filename*=UTF-8''M%E1%BA%ABu%20nh%E1%BA%ADp%20li%E1%BB%87u...
 * </pre>
 * <ul>
 *   <li>{@code filename=} — bản <b>bỏ dấu</b>, thuần ASCII, cho trình duyệt cũ. Không được để ký
 *       tự ngoài ASCII lọt vào đây; đó chính là cái làm hỏng tên tệp.</li>
 *   <li>{@code filename*=} — bản thật, UTF-8 phần trăm-hoá. Mọi trình duyệt hiện hành đọc bản này
 *       trước và người dùng Windows thấy đúng "Mẫu nhập liệu - Chi Ất.xlsx".</li>
 * </ul>
 *
 * <p><b>NFC.</b> Tên tệp được chuẩn hoá NFC trước khi mã hoá. Không làm thì "Ất" từ macOS đi ra
 * dưới dạng NFD và Windows hiện đúng nhưng mọi phép so chuỗi đều sai — cùng một họ bẫy mà
 * {@code TextNormalizer} đã dựng lên để chống.</p>
 *
 * @param noiDung nội dung tệp {@code .xlsx}
 * @param tenTep  tên tệp có dấu, đã NFC, đã bỏ ký tự Windows cấm
 */
public record ExcelTemplateFile(byte[] noiDung, String tenTep) {

    public static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Ký tự Windows không cho phép trong tên tệp — thay bằng gạch ngang thay vì bỏ hẳn. */
    private static final String CAM = "[\\\\/:*?\"<>|\\p{Cntrl}]";

    public ExcelTemplateFile {
        noiDung = noiDung == null ? new byte[0] : noiDung.clone();
        tenTep = donTen(tenTep);
    }

    @Override
    public byte[] noiDung() {
        return noiDung.clone();
    }

    /** Giá trị đầy đủ của tiêu đề {@code Content-Disposition}. */
    public String contentDisposition() {
        return "attachment; filename=\"" + boDau(tenTep) + "\"; filename*=UTF-8''"
                + phanTramHoa(tenTep);
    }

    /** Số byte — tiện cho {@code Content-Length} và cho log. */
    public int kichThuoc() {
        return noiDung.length;
    }

    private static String donTen(String raw) {
        String s = raw == null || raw.isBlank() ? "Mau nhap lieu.xlsx" : raw;
        s = Normalizer.normalize(s, Normalizer.Form.NFC).replaceAll(CAM, "-").trim();
        return s;
    }

    /**
     * Bản dự phòng thuần ASCII. Bỏ dấu <b>và</b> ép {@code đ/Đ} — {@code Normalizer} không tách
     * được chữ đ vì nó là một ký tự riêng chứ không phải d cộng dấu, nên bỏ bước này thì tên tệp
     * "Chi Đông" vẫn còn một ký tự ngoài ASCII và cả tiêu đề lại hỏng.
     */
    static String boDau(String s) {
        String kd = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd').replace('Đ', 'D');
        // Con sot gi ngoai ASCII (chu Han trong ten chi chang han) thi thay bang dau gach.
        return kd.replaceAll("[^\\x20-\\x7E]", "_").replace('"', '\'');
    }

    private static String phanTramHoa(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) {
            int c = b & 0xFF;
            boolean anToan = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '.' || c == '_' || c == '~';
            if (anToan) {
                sb.append((char) c);
            } else {
                sb.append('%').append(String.format(Locale.ROOT, "%02X", c));
            }
        }
        return sb.toString();
    }
}
