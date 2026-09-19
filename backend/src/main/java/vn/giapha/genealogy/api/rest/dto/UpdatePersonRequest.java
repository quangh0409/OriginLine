package vn.giapha.genealogy.api.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.giapha.shared.vo.Gender;

/**
 * Thân yêu cầu sửa nhân khẩu.
 *
 * <h2>Ngữ nghĩa cập nhật - chốt rõ để backend và frontend không lệch</h2>
 * <ul>
 *   <li>trường <b>vắng mặt</b> trong body: giữ nguyên;</li>
 *   <li>trường có giá trị: ghi đè;</li>
 *   <li>muốn <b>xoá trắng</b>: liệt kê tên trường trong {@code clearFields}.</li>
 * </ul>
 *
 * <p>Cố ý không dùng {@code null} để xoá: qua các lớp JSON client, "gửi null" và "không gửi" lẫn
 * vào nhau quá dễ. Bản thân record này cũng không phân biệt được hai trạng thái đó - vì vậy
 * controller đọc thẳng danh sách khoá có mặt từ cây JSON và truyền xuống, thay vì đoán từ
 * {@code null}.</p>
 *
 * <p>{@code names} nếu gửi là <b>thay thế toàn bộ</b> danh sách tên, không phải cộng thêm; muốn
 * thêm một tên thì gửi lại cả danh sách cũ cộng tên mới.</p>
 *
 * <p><b>{@code privacy} thì ngược lại: hợp nhất, không thay thế.</b> Nhóm trường vắng mặt trong
 * khối {@code privacy} giữ nguyên mức hiện có — đúng ngữ nghĩa PATCH của phần còn lại. Giao diện
 * năm công tắc chỉ cần gửi công tắc vừa gạt. Muốn đóng hết thì đưa {@code "privacy"} vào
 * {@code clearFields}: cả năm nhóm về {@code PRIVATE}.</p>
 */
public record UpdatePersonRequest(@Valid List<PersonNameInput> names,
                                  Gender gender,
                                  Boolean isAlive,
                                  Boolean isDeleted,
                                  DateDualDto birth,
                                  DateDualDto death,
                                  String nativePlace,
                                  String currentPlaceProvince,
                                  String currentPlaceFull,
                                  String occupation,
                                  String biography,
                                  String avatarUrl,
                                  UUID primaryBranchId,
                                  ContactInfoDto contact,
                                  Map<String, Object> attributes,
                                  PrivacySettingsDto privacy,
                                  List<String> clearFields,
                                  Boolean confirmTabooOverride,
                                  @Size(max = 1000) String note) {

    public UpdatePersonRequest {
        clearFields = clearFields == null ? List.of() : List.copyOf(clearFields);
    }
}
