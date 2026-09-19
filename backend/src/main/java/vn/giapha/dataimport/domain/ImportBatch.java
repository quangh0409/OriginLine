package vn.giapha.dataimport.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Một <b>lô nhập liệu</b>: một lần Trưởng chi tải tệp lên.
 *
 * <p>POJO thuần. Entity JPA tương ứng nằm ở {@code infrastructure.jpa}.</p>
 *
 * @param fileSha256 <b>chỉ làm đúng một việc</b>: chặn bấm hai lần. Tính đúng đắn không đến từ mã
 *        băm — sửa một ô là đổi mã băm, mà sửa một ô rồi tải lại chính là bước đối soát bình
 *        thường của quy trình. Đừng bao giờ dùng mã băm để quyết định "tệp này đã kiểm rồi".
 * @param objectKey khoá đối tượng của tệp gốc trên MinIO. Tệp gốc giữ lại vĩnh viễn là thứ rẻ nhất
 *        trong cả đường ống và có giá trị lớn nhất khi phải truy lại: nó phân biệt được "họ chép
 *        sai sổ" với "ta phân tích sai tệp". {@code null} khi kho đối tượng chưa được nối vào.
 * @param warningsDigest vân tay của tập cảnh báo ở <b>lần kiểm gần nhất</b>. Xem
 *        {@link #daXemHetCanhBao()}.
 * @param warningsAcknowledgedBy <b>ai</b> tick "đã xem cảnh báo". Cái tick ấy mở khoá nút ghi 400
 *        người vào phả, nên nó phải có chủ.
 * @param warningsAcknowledgedDigest vân tay của tập cảnh báo mà người ấy <b>đã đọc</b>.
 * @param rolledBackAt lô đã được gỡ khỏi phả hay chưa. <b>Không</b> phải một trạng thái: một lô đã
 *        gỡ vẫn mang {@code COMMITTED} vì nó <i>đã từng</i> vào phả, và {@code committedAt} là mốc
 *        so sánh của mọi phép kiểm "ai đã động vào sau khi ghi".
 * @param version bộ đếm sửa đổi của dòng; đi ra hợp đồng để giao diện phát hiện được lô đã bị một
 *        tab khác sửa giữa chừng.
 */
public record ImportBatch(UUID id,
                          UUID branchId,
                          UUID uploadedBy,
                          SourceKind sourceKind,
                          BatchStatus status,
                          String originalFilename,
                          String objectKey,
                          String fileSha256,
                          long fileSizeBytes,
                          int personRowCount,
                          int marriageRowCount,
                          int blockingCount,
                          int warningCount,
                          int createCount,
                          int updateCount,
                          String warningsDigest,
                          Instant warningsAcknowledgedAt,
                          UUID warningsAcknowledgedBy,
                          String warningsAcknowledgedDigest,
                          Instant validatedAt,
                          Instant committedAt,
                          UUID committedBy,
                          Instant rolledBackAt,
                          String failureReason,
                          Instant createdAt,
                          long version) {

    /** Còn lỗi chặn thì không cho bấm duyệt. Cảnh báo thì cho — sau khi người nhập tick đã xem. */
    public boolean coTheDuyet() {
        return status.sanSangGhi() && blockingCount == 0 && daXemHetCanhBao();
    }

    /**
     * Người nhập đã xem <b>đúng tập cảnh báo đang có</b> chưa.
     *
     * <h2>Vì sao không chỉ hỏi "đã tick chưa"</h2>
     * Đối soát là một vòng lặp: tick → sửa tệp → kiểm lại → sửa nữa. Nếu lần kiểm sau sinh ra cảnh
     * báo <b>mới</b> mà cái tick cũ vẫn còn hiệu lực thì người duyệt đang xác nhận những thứ họ
     * chưa từng thấy — và tệ nhất là chính hệ thống nói dối thay họ, vì nhật ký vẫn ghi "đã xác
     * nhận".
     *
     * <p>Ngược lại, kiểm lại mà tập cảnh báo <b>y nguyên</b> thì không bắt tick lại: bắt tick lại
     * một danh sách không đổi là cách chắc chắn để lần thứ ba người ta tick mà không đọc.</p>
     *
     * <p>Vân tay tính từ mã · trang · dòng · cột <b>và câu chữ</b> của từng cảnh báo — xem
     * {@code WarningDigest} để biết vì sao câu chữ nằm trong đó và vì sao {@code context} thì
     * không.</p>
     */
    public boolean daXemHetCanhBao() {
        if (warningCount == 0) {
            return true;
        }
        if (warningsAcknowledgedAt == null) {
            return false;
        }
        return Objects.equals(warningsDigest, warningsAcknowledgedDigest);
    }

    /** Lô còn đang trong quy trình đối soát — chưa chốt và chưa bị lô mới thay thế. */
    public boolean dangMo() {
        return !status.daChot();
    }

    /** Lô đã vào phả rồi lại được gỡ ra. Trạng thái vẫn là {@code COMMITTED}, xem javadoc trường. */
    public boolean daGo() {
        return rolledBackAt != null;
    }

    public ImportBatch withStatus(BatchStatus value, String reason) {
        return new ImportBatch(id, branchId, uploadedBy, sourceKind, value, originalFilename,
                objectKey, fileSha256, fileSizeBytes, personRowCount, marriageRowCount,
                blockingCount, warningCount, createCount, updateCount, warningsDigest,
                warningsAcknowledgedAt, warningsAcknowledgedBy, warningsAcknowledgedDigest,
                validatedAt, committedAt, committedBy, rolledBackAt, reason, createdAt, version);
    }
}
