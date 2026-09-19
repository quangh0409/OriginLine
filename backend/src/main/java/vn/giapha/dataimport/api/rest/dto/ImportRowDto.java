package vn.giapha.dataimport.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Một dòng đang nằm ở <b>khu vực chờ</b> — thứ người nhập đã gõ, chưa phải thứ trong phả.
 *
 * <h2>Vì sao màn xem trước đáng có</h2>
 * {@link #plannedAction()} trả lời câu hỏi duy nhất mà Trưởng chi thật sự lo khi bấm duyệt lần thứ
 * hai: "tải lại có sinh ra người trùng không". {@code UPDATE} nghĩa là mã này đã có chủ trong phả
 * và sẽ cập nhật đúng người ấy; {@code CREATE} nghĩa là sẽ thêm mới. Con số ấy suy từ
 * {@code person_external_ref}, không phải từ tên.
 *
 * <h2>Không có dữ liệu nào của phả ở đây</h2>
 * Mọi trường đều đọc từ tệp người nhập vừa nộp. {@link #resolvedPersonId()} là ngoại lệ duy nhất và
 * nó chỉ là một khoá: muốn biết người ấy là ai thì gọi {@code GET /api/v1/persons/{id}}, nơi bộ lọc
 * phân tầng riêng tư quyết định trường nào được trả.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportRowDto(int rowNo,
                           String externalCode,
                           String fullName,
                           String tabooName,
                           String posthumousName,
                           String hanNomName,
                           String gender,
                           Integer generation,
                           String fatherCode,
                           String motherCode,
                           String parentRel,
                           Boolean alive,
                           Integer birthYear,
                           String deathLunar,
                           String nativePlace,
                           String nativePlaceCode,
                           String heirOfCode,
                           String heirKind,
                           String plannedAction,
                           UUID resolvedPersonId) {
}
