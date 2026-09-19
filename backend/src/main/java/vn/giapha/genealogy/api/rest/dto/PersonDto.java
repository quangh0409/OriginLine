package vn.giapha.genealogy.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.giapha.shared.vo.Gender;

/**
 * Hồ sơ nhân khẩu, <b>đã lọc theo phân tầng riêng tư</b>.
 *
 * <h2>Cùng một schema, khác tập trường</h2>
 * Nhờ {@code @JsonInclude(NON_NULL)}, trường bị ẩn <b>biến mất khỏi JSON</b> - không phải
 * {@code null}, không phải chuỗi rỗng, không phải {@code "***"}. Vắng mặt có <b>hai nguyên nhân
 * không phân biệt được</b>, và đó là chủ ý:
 * <ol>
 *   <li>dữ liệu không tồn tại (gia phả cổ thiếu thông tin);</li>
 *   <li>người gọi không đủ quyền xem trường đó.</li>
 * </ol>
 *
 * <p>Vì vậy giao diện <b>không được</b> render placeholder kiểu "dữ liệu bị ẩn" cho trường vắng
 * mặt - làm thế là tự tay tiết lộ điều mà việc lọc đang cố giấu.</p>
 *
 * @param isDeleted chỉ xuất hiện với {@code ADMIN}/{@code COUNCIL}; vai khác không thấy cả bản ghi
 * @param avatarUrl ảnh chân dung. Giai đoạn 1 trả <b>khoá đối tượng MinIO</b>; việc ký URL có hạn
 *        dùng thuộc về adapter lưu trữ chưa có ở workstream này (xem báo cáo bàn giao).
 * @param privacy bản đồng thuận riêng tư của chủ thể — năm nhóm trường, năm mức độc lập. Chỉ
 *        <b>chính chủ</b> và {@code ADMIN} nhận được khối này; với vai khác nó vắng mặt hoàn
 *        toàn, vì biết người khác đang siết quyền riêng tư cũng là một dạng rò rỉ.
 * @param version phiên bản optimistic locking, cũng là giá trị sinh {@code ETag}
 * @param relationships quan hệ trực tiếp một bậc, chỉ gồm cạnh mà đầu kia cũng hiển thị được
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PersonDto(UUID id,
                        List<PersonNameDto> names,
                        String displayName,
                        Gender gender,
                        Integer generation,
                        @JsonProperty("isAlive") boolean isAlive,
                        @JsonProperty("isDeleted") Boolean isDeleted,
                        DateDualDto birth,
                        DateDualDto death,
                        String nativePlace,
                        String currentPlaceProvince,
                        String currentPlaceFull,
                        String occupation,
                        String biography,
                        String avatarUrl,
                        BranchRefDto primaryBranch,
                        ContactInfoDto contact,
                        Map<String, Object> attributes,
                        PrivacySettingsDto privacy,
                        Instant createdAt,
                        Instant updatedAt,
                        Long version,
                        List<RelationshipDto> relationships,
                        PersonAccessMetaDto meta) {
}
