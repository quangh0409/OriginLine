package vn.giapha.dataimport.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Một lô nhập liệu, kèm <b>mọi con số đã đếm sẵn</b>.
 *
 * <h2>Backend đếm, client không đếm lại</h2>
 * Giao diện khoá/mở nút duyệt theo {@link #canApprove()} và ba con số
 * {@link #blockingCount()} / {@link #warningCount()} / {@link #undecidedDuplicateCount()}. Client
 * <b>không</b> được tự cộng lại từ danh sách lỗi: danh sách ấy có phân trang, nên hai nơi sẽ lệch
 * ngay ở lô thứ nhất có hơn một trang lỗi — và một con số lệch trên màn đối soát là con số phá vỡ
 * lòng tin của Trưởng chi vào cả bộ kiểm.
 *
 * <h2>{@code canApprove} là câu trả lời, không phải gợi ý</h2>
 * Nó đọc thẳng từ {@code ImportBatch.coTheDuyet()} — cùng một bất biến mà cổng duyệt phía máy chủ
 * áp dụng. Giao diện dựng nút theo nó thì không có cách nào để nút hiện ra trong lúc máy chủ sẽ
 * từ chối, và ngược lại.
 *
 * @param fileSha256 chỉ để chặn bấm hai lần. <b>Không</b> phải căn cứ đúng/sai: sửa một ô là đổi
 *        mã băm, mà sửa một ô rồi tải lại chính là bước đối soát bình thường
 * @param uploadedBy khoá {@code app_user}; tên người tải hiện <b>chưa</b> có ở hợp đồng này, xem
 *        ghi chú lệch hợp đồng trong {@code contracts/openapi.yaml}
 * @param warningsAcknowledgedBy <b>ai</b> đã tick "đã xem cảnh báo". Cái tick ấy mở khoá nút ghi
 *        400 người vào phả nên nó phải có chủ; một dấu thời gian vô danh không trả lời được câu
 *        duy nhất người ta sẽ hỏi về sau
 * @param committedBy tài khoản đã bấm ghi vào phả
 * @param rolledBackAt lô đã được gỡ khỏi phả lúc nào. <b>Không</b> phải một trạng thái: lô vẫn
 *        mang {@code COMMITTED} vì nó <i>đã từng</i> vào phả
 * @param version bộ đếm sửa đổi của dòng, để giao diện phát hiện lô đã bị một tab khác sửa
 * @param suspectDuplicateCount số cặp nghi trùng bộ kiểm tìm thấy
 * @param undecidedDuplicateCount trong đó bao nhiêu cặp người nhập chưa quyết
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportBatchDto(UUID id,
                             UUID branchId,
                             String branchName,
                             String branchPath,
                             String sourceKind,
                             String status,
                             String fileName,
                             String fileSha256,
                             long fileSizeBytes,
                             UUID uploadedBy,
                             Instant uploadedAt,
                             Instant validatedAt,
                             Instant committedAt,
                             Instant warningsAcknowledgedAt,
                             UUID warningsAcknowledgedBy,
                             UUID committedBy,
                             Instant rolledBackAt,
                             String failureReason,
                             int personRowCount,
                             int marriageRowCount,
                             int blockingCount,
                             int warningCount,
                             int plannedCreateCount,
                             int plannedUpdateCount,
                             int suspectDuplicateCount,
                             int undecidedDuplicateCount,
                             boolean canApprove,
                             long version) {
}
