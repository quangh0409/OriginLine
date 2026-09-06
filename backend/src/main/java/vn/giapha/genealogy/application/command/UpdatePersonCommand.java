package vn.giapha.genealogy.application.command;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.giapha.genealogy.domain.ContactInfo;
import vn.giapha.genealogy.domain.FieldChange;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.PrivacyLevel;
import vn.giapha.shared.vo.Gender;

/**
 * Sửa một phần hồ sơ nhân khẩu.
 *
 * <p>Mỗi trường là một {@link FieldChange} chứ không phải một giá trị có thể {@code null}, vì
 * "không gửi" và "gửi null" là hai ý định khác nhau mà {@code null} không diễn đạt nổi: vắng mặt
 * là giữ nguyên, có giá trị là ghi đè, có tên trong {@code clearFields} là xoá trắng. Tầng api
 * dịch từ JSON sang ba trạng thái này, domain nhận vào thứ đã rõ nghĩa.</p>
 *
 * @param expectedVersion phiên bản lấy từ {@code ETag} của lần {@code GET} trước; lệch nghĩa là
 *        người khác đã sửa hồ sơ này, trả {@code 409 OPTIMISTIC_LOCK_CONFLICT}
 * @param names nếu có mặt thì <b>thay thế toàn bộ</b> danh sách tên, không phải cộng thêm
 * @param alive chuyển từ {@code true} sang {@code false} là <b>báo mất</b>, nên gửi kèm
 *        {@code death}. Vắng mặt mà có {@code death} thì backend <b>suy ra</b> là đã mất (Phương án
 *        B của Hội đồng Tộc biểu — xem {@code LifeStatusResolver}); gửi kèm {@code alive = true}
 *        cùng một ngày mất là mâu thuẫn tường minh và bị từ chối {@code VALIDATION_FAILED}
 * @param deleted dùng để khôi phục bản ghi đã xoá mềm; chỉ {@code ADMIN} và {@code COUNCIL}
 */
public record UpdatePersonCommand(UUID personId,
                                  Long expectedVersion,
                                  FieldChange<List<PersonName>> names,
                                  FieldChange<Gender> gender,
                                  FieldChange<Boolean> alive,
                                  FieldChange<Boolean> deleted,
                                  FieldChange<LifeDate> birth,
                                  FieldChange<LifeDate> death,
                                  FieldChange<String> nativePlace,
                                  FieldChange<String> currentPlaceProvince,
                                  FieldChange<String> currentPlaceFull,
                                  FieldChange<String> occupation,
                                  FieldChange<String> biography,
                                  FieldChange<String> avatarKey,
                                  FieldChange<UUID> primaryBranchId,
                                  FieldChange<ContactInfo> contact,
                                  FieldChange<Map<String, Object>> attributes,
                                  FieldChange<PrivacyLevel> privacyLevel,
                                  boolean confirmTabooOverride,
                                  String note) {
}
