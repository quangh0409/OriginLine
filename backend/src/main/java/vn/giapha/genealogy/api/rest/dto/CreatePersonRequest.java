package vn.giapha.genealogy.api.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.giapha.genealogy.domain.PrivacyLevel;
import vn.giapha.shared.vo.Gender;

/**
 * Thân yêu cầu thêm nhân khẩu.
 *
 * <p>{@code generation} cố ý <b>không</b> có mặt: đời thứ do backend suy ra từ quan hệ cha-con để
 * không bao giờ lệch với đồ thị. Con gái và bên ngoại được ghi nhận đầy đủ, ngang bằng con trai -
 * đây là mặc định hệ thống (FR-1.8), không phải một tuỳ chọn.</p>
 *
 * @param initialRelationships nối ngay vào cây trong <b>cùng transaction</b> với việc tạo nhân
 *        khẩu; để trống thì nhân khẩu tồn tại nhưng chưa nằm trong cây và chưa hiện trên phả đồ
 * @param confirmTabooOverride cờ xác nhận <b>kỵ húy</b>. Giao diện không được mặc định {@code true}
 *        cho tiện: mất cảnh báo là mất luôn giá trị của FR-1.6.
 * @param note ghi chú nguồn dữ liệu, ví dụ "chép từ gia phả giấy 1998, trang 12"
 */
public record CreatePersonRequest(@NotEmpty @Valid List<PersonNameInput> names,
                                  @NotNull Gender gender,
                                  @NotNull Boolean isAlive,
                                  DateDualDto birth,
                                  DateDualDto death,
                                  String nativePlace,
                                  String currentPlaceProvince,
                                  String currentPlaceFull,
                                  String occupation,
                                  String biography,
                                  UUID primaryBranchId,
                                  ContactInfoDto contact,
                                  Map<String, Object> attributes,
                                  PrivacyLevel privacyLevel,
                                  @Valid List<RelationshipLinkInput> initialRelationships,
                                  Boolean confirmTabooOverride,
                                  Boolean confirmDuplicateOverride,
                                  @Size(max = 1000) String note) {
}
