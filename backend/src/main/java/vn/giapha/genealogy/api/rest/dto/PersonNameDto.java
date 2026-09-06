package vn.giapha.genealogy.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import vn.giapha.genealogy.domain.NameType;

/**
 * Một lớp tên của nhân khẩu.
 *
 * <p>Người Việt truyền thống có <b>nhiều tên</b> chứ không phải một: húy, tự, hiệu, thụy, thường
 * gọi, pháp danh. Đây là khác biệt nền tảng so với mô hình "một cột full_name" của phần mềm phương
 * Tây, và là lý do bảng {@code person_name} là quan hệ 1-N.</p>
 *
 * @param nameUnaccented dạng không dấu do CSDL sinh (generated column), phục vụ tìm kiếm.
 *        <b>Chỉ đọc</b>: client gửi lên sẽ bị bỏ qua, vì sinh tay là mở đường cho bản không dấu
 *        lệch với tên có dấu.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PersonNameDto(UUID id,
                            NameType nameType,
                            String fullName,
                            String nameHanNom,
                            String nameUnaccented,
                            @JsonProperty("isPrimary") boolean isPrimary,
                            String note) {
}
