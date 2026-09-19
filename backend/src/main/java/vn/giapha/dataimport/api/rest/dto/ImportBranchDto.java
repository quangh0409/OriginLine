package vn.giapha.dataimport.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Một chi/ngành mà người đang đăng nhập được nhập liệu vào.
 *
 * @param path {@code ltree}, ví dụ {@code goc.chi_at} — <b>căn cứ phạm vi duy nhất</b>. Giao diện
 *        không được suy phạm vi từ {@code id} hay từ tên.
 * @param canImport người gọi có quyền ghi trên chi này không. Danh sách trả về <i>cả</i> chi ngoài
 *        phạm vi (để Trưởng chi thấy mình đang ở đâu trong cả dòng họ) nhưng cờ này nói rõ chi nào
 *        bấm tải lên được — thay vì để giao diện đoán rồi nhận 403 sau khi người dùng đã chọn tệp.
 * @param openBatchId lô <b>đang dở</b> của chi này, nếu có. Đây là thứ làm cho màn đầu tiên nói
 *        đúng: một Trưởng chi quay lại sau hai hôm cần thấy ngay "bạn còn một lô đang đối soát",
 *        chứ không phải một nút "Tải tệp lên" sạch trơn dẫn họ đi nộp lô thứ hai. Vắng mặt với chi
 *        ngoài phạm vi người gọi — việc dở dang của chi khác không phải chuyện của màn này
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportBranchDto(UUID id, String name, String path, String kind, boolean canImport,
                              UUID openBatchId) {
}
