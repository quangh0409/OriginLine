package vn.giapha.genealogy.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.UUID;
import vn.giapha.genealogy.domain.HeirKind;
import vn.giapha.genealogy.domain.RelType;

/**
 * Một cạnh quan hệ. Hướng luôn là {@code fromPersonId} tới {@code toPersonId}; ý nghĩa của hướng
 * phụ thuộc {@code relType} - với cạnh cha/mẹ thì {@code from} là cha/mẹ.
 *
 * <h2>Vì sao cạnh mang theo tóm tắt của đầu kia</h2>
 * Màn "Quan hệ" trong hồ sơ nhân khẩu cần <b>tên</b>, <b>đời</b> và <b>chi</b> của người ở đầu kia,
 * không phải một chuỗi UUID. Khi cạnh chỉ mang id, giao diện buộc phải gọi thêm một lượt
 * {@code /tree} chỉ để đọc một cái tên - tốn một vòng mạng cho mỗi lần mở hồ sơ, trên chính thiết
 * bị di động mà bà con hải ngoại đang dùng. GraphQL đã có tiền lệ đúng ở {@code SpouseLink}; REST
 * theo cùng hình dạng đó.
 *
 * <p><b>Bất biến bảo mật:</b> {@link #otherPerson} luôn là kết quả của <b>đúng bộ lọc phân tầng
 * riêng tư của người gọi hiện tại</b> (BA v2 §10) - nó được dựng từ một {@code PersonView} đã lọc,
 * không bao giờ dựng thẳng từ thực thể. Cạnh nào có đầu kia không hiển thị được thì cả cạnh đã bị
 * loại từ tầng application, nên trường này vắng mặt chứ không bao giờ là một tóm tắt "đã che".</p>
 *
 * @param spouseOrder thứ tự vợ/chồng cho <b>đa thê/đa phu</b>, 1 là vợ cả/chồng cả
 * @param validTo có giá trị nghĩa là quan hệ đã kết thúc (ly hôn hoặc một bên mất)
 * @param otherPerson tóm tắt của <b>đầu kia</b> xét theo hồ sơ đang xem; vắng mặt ở những lối ra
 *        không có khái niệm "hồ sơ đang xem" (ví dụ GraphQL, nơi client tự chọn {@code from}/
 *        {@code to})
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RelationshipDto(UUID id,
                              UUID fromPersonId,
                              UUID toPersonId,
                              RelType relType,
                              HeirKind heirKind,
                              Integer spouseOrder,
                              LocalDate validFrom,
                              LocalDate validTo,
                              String note,
                              PersonSummaryDto otherPerson) {

    /** Đầu kia của cạnh xét từ {@code subjectId}; {@code null} nếu id không thuộc cạnh này. */
    public UUID otherEnd(UUID subjectId) {
        if (subjectId == null) {
            return null;
        }
        if (subjectId.equals(fromPersonId)) {
            return toPersonId;
        }
        return subjectId.equals(toPersonId) ? fromPersonId : null;
    }
}
