package vn.giapha.dataimport.domain.port;

import java.util.Set;

/**
 * Cổng tra danh mục <b>mã tỉnh / quốc gia</b> ({@code place_division}).
 *
 * <h2>Vì sao mã địa danh phải có mặt ngay từ lần nhập đầu tiên</h2>
 * {@code person.native_place} là VARCHAR(255) tự do. Không có bảng mã nào. Vì vậy báo cáo dân số
 * (FR-4.3) <b>không dựng được</b>: "Hà Nội", "TP. Hà Nội" và "Hanoi" là ba nhóm khác nhau với mọi
 * phép gộp, và không có cách nào hoà giải sau khi cả dòng họ đã gõ xong. Quyết muộn nghĩa là cả
 * dòng họ phải nhập lại nơi ở — nên cột mã vào mẫu Excel từ ngày đầu, chứ không đợi tới đợt làm
 * báo cáo.
 *
 * <p><b>Danh mục rỗng là trạng thái hợp lệ</b> và bộ kiểm phải tự tắt luật mã tỉnh khi gặp nó:
 * nội dung danh mục là dữ liệu pháp quy do quản trị nạp, không phải thứ chép tay vào migration.
 * Chặn người nhập vì một bảng chưa được nạp là lỗi của ta, không phải của họ.</p>
 */
public interface PlaceCodePort {

    /** Toàn bộ mã hợp lệ, nạp một lượt. Rỗng nghĩa là danh mục chưa được nạp. */
    Set<String> allCodes();
}
