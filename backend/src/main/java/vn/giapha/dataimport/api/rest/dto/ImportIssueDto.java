package vn.giapha.dataimport.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.UUID;

/**
 * Một dòng trong bảng lỗi/cảnh báo.
 *
 * <h2>{@code context} giữ nguyên khoá tiếng Việt của cơ sở dữ liệu</h2>
 * {@code import_issue.context} là JSONB do từng luật tự soạn, mỗi luật một hình dạng. Dịch khoá
 * sang tiếng Anh ở tầng api có nghĩa là tầng api phải biết trước payload của cả mười ba luật, và
 * mỗi luật mới là một chỗ phải nhớ sửa hai nơi. Tệ hơn: bản xuất Excel và mọi công cụ truy vấn
 * thẳng cơ sở dữ liệu sẽ thấy một bộ tên khác hẳn với giao diện. Vì vậy khoá đi ra <b>nguyên
 * văn</b>:
 *
 * <ul>
 *   <li>{@code goiY} — mảng mã gần giống, đã xếp theo độ gần ({@code IMP_PARENT_NOT_FOUND},
 *       {@code IMP_HEIR_TARGET_NOT_FOUND}). Do máy chủ tính, vì chỉ máy chủ có cả tệp lẫn
 *       {@code person_external_ref} trong tay.</li>
 *   <li>{@code chuoi} — đường đi thật của vòng lặp, <b>đã đóng kín</b>, ví dụ
 *       {@code ["AT-05-012","AT-04-003","AT-05-012"]} ({@code IMP_CYCLE}).</li>
 *   <li>{@code cacDong} — các số dòng cùng dính một mã ({@code IMP_DUP_CODE}) hoặc cùng một bậc
 *       vợ ({@code IMP_SPOUSE_ORDER_CONFLICT}). Là <b>mảng</b>, không phải một số.</li>
 *   <li>{@code doiKhai} / {@code doiSuyRa} — đời người nhập gõ và đời hệ thống suy ra
 *       ({@code IMP_GENERATION_MISMATCH}).</li>
 * </ul>
 *
 * @param id khoá dòng trong {@code import_issue}. Giao diện dùng nó làm khoá danh sách — bộ ghép
 *        {@code (sheet, rowNo, code, field)} <b>không duy nhất</b> (hai luật khác nhau vẫn có thể
 *        cùng trỏ vào một ô), nên dựng khoá từ đó sẽ làm bảng lỗi nhảy chỗ mỗi lần kiểm lại
 * @param sheet {@code NHAN_KHAU} · {@code HON_PHOI} · {@code LO} — tên trang <b>thật</b> trong mẫu
 *        Excel; {@code LO} nghĩa là vấn đề của cả lô, không gắn với dòng nào
 * @param rowNo số dòng đúng như Excel đánh; {@code null} với {@code sheet = LO}
 * @param externalCode cột {@code Mã} của dòng, suy từ khu vực chờ để người nhập tra ngược vào sổ
 *        giấy mà không phải mở tệp ra đếm dòng
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportIssueDto(UUID id,
                             String severity,
                             String code,
                             String sheet,
                             Integer rowNo,
                             String field,
                             String externalCode,
                             String message,
                             Map<String, Object> context) {
}
