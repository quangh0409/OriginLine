package vn.giapha.media.domain;

import java.time.Duration;

/**
 * <b>Một chỗ duy nhất</b> cho mọi con số trần của đường tải tệp lên.
 *
 * <h2>Vì sao hằng số phải nằm ở đúng một tệp</h2>
 * Cùng bài học mà {@code dataimport.ImportLimits} + {@code ImportMultipartLimitTest} đã trả giá để
 * học: trần nghiệp vụ và trần hạ tầng nằm ở hai tệp khác loại thì trình biên dịch không bắt được
 * lúc chúng lệch nhau, và triệu chứng là <b>một thông báo sai trông như đúng</b>. Ở đây các con số
 * còn phải khớp với ba nơi nữa — {@code contracts/openapi.yaml}, câu trả lời của
 * {@code POST /api/v1/media/upload-tickets}, và câu tiếng Việt in ra khi từ chối. {@code
 * MediaLimitsContractTest} ghim rằng chúng không trôi khỏi nhau.
 *
 * <h2>Các con số, và người ký tên sau chúng</h2>
 * Đây là <b>đề xuất kỹ thuật đã chốt với chủ dự án</b> cho đợt này, không phải ước lượng: mỗi trần
 * đều có một tình huống hỏng cụ thể ở phía sau và một câu trả lời cho câu hỏi "vì sao không lớn
 * hơn".
 */
public final class MediaLimits {

    // =====================================================================================
    // Ảnh
    // =====================================================================================

    /**
     * Trần một tấm ảnh: <b>8 MiB</b>.
     *
     * <p>Một ảnh JPEG 12 MP chụp bằng điện thoại phổ thông rơi vào 3–5 MB, nên 8 MiB đã là dư cho
     * mọi ảnh chụp thật. Không nới lên 20 MB vì thứ lọt qua ở khoảng ấy không phải ảnh chụp mà là
     * ảnh 48 MP chưa nén hoặc ảnh scan 600 dpi — và <b>không có bước chuyển mã nào</b> ở đợt này,
     * nên đúng số byte ấy sẽ đi ngược xuống máy của một người trong họ đang dùng 3G.</p>
     */
    public static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;

    // =====================================================================================
    // Video — hai trần, và trần thứ hai mới là trần thật
    // =====================================================================================

    /**
     * Trần một video: <b>100 MiB</b>.
     *
     * <h2>Đây là NGÂN SÁCH TẢI XUỐNG, không phải ngân sách tải lên</h2>
     * Quyết định đã chốt của chủ dự án: <b>nhận tệp gốc, không chuyển mã</b>. Hệ quả trực tiếp là
     * mỗi byte tải lên sẽ được tải xuống nguyên vẹn bởi <i>mọi</i> người đọc bài — trong đó có con
     * cháu ở nước ngoài mở bằng dữ liệu di động. 100 MiB ở đường truyền 6 Mbps là khoảng hai phút
     * chờ; 200 MiB là bốn phút, và ở mốc ấy người ta đóng trang.
     *
     * <p>Con số này <b>ăn khớp với trần thời lượng</b>: 100 MiB / 120 s ≈ 7 Mbps, thừa cho 1080p
     * quay bằng điện thoại. Một clip 4K 2 phút sẽ vượt trần dung lượng và bị từ chối — đúng ý đồ,
     * và thông báo nói thẳng cách chữa (quay/xuất lại ở 1080p, hoặc cắt ngắn).</p>
     */
    public static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024;

    /**
     * Trần thời lượng một video: <b>120 giây</b>.
     *
     * <h2>Vì sao cần trần thời lượng khi đã có trần dung lượng</h2>
     * Vì hai trần bắt hai thứ khác nhau. Dung lượng bắt <i>băng thông</i>; thời lượng bắt
     * <i>thể loại</i>. Không có trần thời lượng thì một bản ghi hình 40 phút lễ giỗ nén rất mạnh
     * vẫn lọt qua 100 MiB — và lúc đó trang chủ của dòng họ biến thành một kho video, thứ mà đợt
     * này cố ý <b>không</b> xây (không chuyển mã, không ảnh bìa, không phát theo đoạn).
     *
     * <p>Hai phút đủ cho một đoạn rước kiệu, một lời phát biểu ngắn, một vòng quay từ đường. Dài
     * hơn thì đưa lên nền tảng video rồi dán liên kết vào thân bài — rẻ hơn cho cả dòng họ.</p>
     *
     * <p><b>Trần này chỉ có nghĩa nếu thời lượng ĐO ĐƯỢC.</b> Xem {@link VideoHeaderProbe}: ta đọc
     * nó từ header container (hộp {@code mvhd} của MP4 / phần tử {@code Duration} của WebM), không
     * tin con số client khai và không gọi ffmpeg. Đọc không ra thì <b>từ chối</b> — một trần không
     * đo được là một trần không tồn tại.</p>
     */
    public static final int MAX_VIDEO_SECONDS = 120;

    // =====================================================================================
    // Số tệp trên một bài
    // =====================================================================================

    /**
     * Số tệp đính kèm tối đa trên một bài: <b>12</b>.
     *
     * <p>Bài viết là một bài viết, không phải một an-bum. 12 đủ cho bộ ảnh một lễ giỗ mà vẫn giữ
     * {@code PostDto} ở kích thước hợp lý — mỗi phần tử chở theo một URL đã ký dài vài trăm byte,
     * nên một trang chủ 10 bài × 12 tệp đã là 120 URL trong một phản hồi.</p>
     */
    public static final int MAX_MEDIA_PER_POST = 12;

    // =====================================================================================
    // Chữ thay ảnh
    // =====================================================================================

    /**
     * Độ dài tối đa của chữ thay ảnh: <b>300 ký tự</b>.
     *
     * <p>WCAG khuyến nghị chữ thay ảnh ngắn gọn; mô tả dài thuộc về thân bài. 300 ký tự là chỗ cho
     * một câu tiếng Việt đầy đủ dấu ("Con cháu chi Giáp dâng hương tại từ đường ngày giỗ Tổ") mà
     * vẫn ngắn hơn ngưỡng mà trình đọc màn hình bắt đầu làm người nghe mệt.</p>
     */
    public static final int MAX_ALT_LENGTH = 300;

    // =====================================================================================
    // Thời hạn
    // =====================================================================================

    /**
     * Hạn của URL {@code PUT} đã ký: <b>15 phút</b>.
     *
     * <p>Một video 100 MiB trên đường lên 2 Mbps mất khoảng 7 phút. 15 phút cho gấp đôi biên —
     * ngắn hơn (5 phút) thì một lần tải hợp lệ hỏng ở 80% và người dùng không hiểu vì sao. Dài hơn
     * thì một URL lọt ra ngoài còn ghi được vào kho lâu hơn mức cần thiết.</p>
     */
    public static final Duration UPLOAD_TICKET_TTL = Duration.ofMinutes(15);

    /**
     * Hạn của URL {@code GET} đã ký: <b>10 phút</b>.
     *
     * <p>Đủ để một trang tải xong và một video bắt đầu phát (S3 kiểm hạn lúc <i>mở</i> yêu cầu,
     * không kiểm suốt lúc truyền). Ngắn đủ để một URL bị dán vào nhóm chat ngừng hoạt động trước
     * khi nó đi xa — đây chính là lý do bucket để {@code private} và không có lối đọc ẩn danh.</p>
     */
    public static final Duration VIEW_URL_TTL = Duration.ofMinutes(10);

    /**
     * Ân hạn trước khi dọn một phiếu {@code PENDING} đã quá hạn: <b>1 giờ</b> (gấp 4 lần
     * {@link #UPLOAD_TICKET_TTL}).
     *
     * <p>Biên này chặn một cuộc đua có thật: người dùng tải xong ở phút thứ 14, yêu cầu xác nhận
     * đang trên đường thì công việc dọn chạy và xoá mất đối tượng. Gấp bốn lần hạn phiếu là đủ rộng
     * để cuộc đua ấy không xảy ra và vẫn đủ hẹp để kho không tích rác.</p>
     */
    public static final Duration PENDING_GRACE = Duration.ofHours(1);

    /**
     * Ân hạn trước khi dọn một tệp {@code READY} đã <b>mất chủ</b>: <b>24 giờ</b>.
     *
     * <h2>Vì sao có ân hạn, khi mà gỡ bài là một chiều</h2>
     * Máy trạng thái của bài không có mũi tên nào quay lại từ {@code WITHDRAWN}, nên xét thuần
     * logic thì xoá ngay cũng đúng. Ân hạn ở đây phục vụ <b>người vận hành</b>, không phục vụ máy:
     * một Trưởng chi gỡ nhầm bài lúc 10 giờ tối có một ngày để người quản trị lấy tệp ra khỏi kho
     * trước khi nó biến mất vĩnh viễn. Một ngày là cái giá rẻ nhất mua được điều đó.
     *
     * <p><b>Ngoại lệ có chủ ý:</b> gỡ theo một đơn báo vi phạm <b>không</b> có ân hạn — xem
     * {@code MediaReportService}. Một tấm ảnh đang vi phạm riêng tư phải ngừng được phục vụ
     * <i>ngay</i>, và đó chính là điều kiện làm cho quyết định "ảnh đi theo quyền của bài" an
     * toàn.</p>
     */
    public static final Duration ORPHAN_GRACE = Duration.ofHours(24);

    /** Số đối tượng tối đa một lượt dọn xử lý. Giữ giao dịch ngắn và công việc đêm đoán trước được. */
    public static final int GC_BATCH_SIZE = 500;

    private MediaLimits() {
    }

    /** Trần dung lượng áp cho một loại tệp. */
    public static long maxBytes(MediaKind kind) {
        return kind == MediaKind.VIDEO ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
    }
}
