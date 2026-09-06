package vn.giapha.genealogy.application.command;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.giapha.genealogy.domain.ContactInfo;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.PrivacyLevel;
import vn.giapha.shared.vo.Gender;

/**
 * Thêm một nhân khẩu, kèm khả năng nối ngay vào cây trong <b>cùng một transaction</b>.
 *
 * <p>{@code generation} cố ý vắng mặt: đời thứ được suy ra từ {@link #links()} chứ không nhận từ
 * client, nên không bao giờ lệch với đồ thị. Nhân khẩu tạo mà không có link nào thì tồn tại nhưng
 * <b>chưa nằm trong cây</b> và không xuất hiện trên phả đồ cho tới khi được nối.</p>
 *
 * @param confirmTabooOverride cờ xác nhận <b>kỵ húy</b>: người dùng đã xem danh sách va chạm ở lần
 *        gọi trước và vẫn quyết định ghi. Việc ghi đè được lưu vào {@code audit_log} - đây là cảnh
 *        báo chứ không phải lệnh cấm, quyền quyết định thuộc về dòng họ.
 * @param note ghi chú nguồn dữ liệu, ví dụ "chép từ gia phả giấy 1998, trang 12"
 */
public record AddPersonCommand(List<PersonName> names,
                               Gender gender,
                               boolean alive,
                               LifeDate birth,
                               LifeDate death,
                               String nativePlace,
                               String currentPlaceProvince,
                               String currentPlaceFull,
                               String occupation,
                               String biography,
                               UUID primaryBranchId,
                               ContactInfo contact,
                               Map<String, Object> attributes,
                               PrivacyLevel privacyLevel,
                               List<RelationshipLinkCommand> links,
                               boolean confirmTabooOverride,
                               boolean confirmDuplicateOverride,
                               String note) {

    public AddPersonCommand {
        names = names == null ? List.of() : List.copyOf(names);
        links = links == null ? List.of() : List.copyOf(links);
    }
}
