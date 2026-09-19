package vn.giapha.dataimport.api.rest.dto;

/**
 * Phản hồi của một lần ghi quyết định: <b>cặp vừa quyết</b>, và <b>lô sau khi tính lại</b>.
 *
 * <h2>Vì sao trả cả hai thay vì chỉ trả cặp</h2>
 * Một quyết định thay đổi bốn thứ trên màn hình cùng lúc: trạng thái của chính cặp ấy,
 * {@code undecidedDuplicateCount}, {@code plannedCreateCount}/{@code plannedUpdateCount} (gộp làm
 * một dòng chuyển từ "tạo mới" sang "cập nhật", hoặc biến mất hẳn), và {@code canApprove}. Trả mỗi
 * cặp thì giao diện phải gọi thêm một vòng để biết ba thứ còn lại, và trong lúc chưa gọi xong thì
 * nút duyệt hiện sai — người đối chiếu quyết cặp cuối cùng rồi nhìn nút vẫn xám.
 *
 * <p>Trả cả hai trong <b>một</b> vòng cũng loại bỏ hẳn một lớp lỗi đua: hai quyết định gửi đi gần
 * nhau không thể trả về hai con số đếm lệch thứ tự nữa, vì mỗi con số đi kèm đúng cái quyết định
 * sinh ra nó.</p>
 */
public record ImportDuplicateDecisionResultDto(ImportDuplicatePairDto pair, ImportBatchDto batch) {
}
