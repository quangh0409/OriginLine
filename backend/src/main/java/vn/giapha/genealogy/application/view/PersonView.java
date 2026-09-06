package vn.giapha.genealogy.application.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.giapha.genealogy.domain.ContactInfo;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.PrivacyLevel;
import vn.giapha.shared.vo.Gender;

/**
 * Hồ sơ nhân khẩu <b>đã lọc theo phân tầng riêng tư</b> — thứ duy nhất tầng api được phép trả ra.
 *
 * <p><b>Đọc kỹ trước khi thêm trường:</b> mọi trường ngoài {@code id}, {@code names},
 * {@code alive} và {@code access} đều có thể {@code null}, và {@code null} mang <b>hai nghĩa
 * không phân biệt được</b> — không có dữ liệu, hoặc người gọi không đủ quyền. Thêm bất kỳ cờ nào
 * cho phép phân biệt hai nghĩa đó là phá vỡ chính điều BA v2 §10 yêu cầu.</p>
 *
 * <p>Bản thân view này <b>không bao giờ được cache</b>: nội dung phụ thuộc người gọi, nên một bản
 * cache của vai này rơi vào tay vai khác là rò rỉ dữ liệu không để lại dấu vết trong log. Chỉ
 * khung xương cây (danh sách id) mới được cache.</p>
 *
 * @param names        các lớp tên; với người còn sống ở Tầng 1 chỉ còn tên chính, vì tên
 *                     húy/tự/hiệu là dữ liệu lễ nghi nhạy cảm
 * @param deleted      cờ xoá mềm; chỉ có giá trị với {@code ADMIN}/{@code COUNCIL}
 * @param birth        Tầng 2 chỉ nhận phần <b>năm</b> ({@code precision = YEAR}); ngày đầy đủ là Tầng 3
 * @param death        ngày mất song lịch — {@code death.lunar} là nguồn chân lý tính giỗ
 * @param avatarKey    khoá đối tượng trên MinIO (không phải blob, không phải URL đã ký)
 * @param relationships quan hệ trực tiếp một bậc, chỉ gồm cạnh mà đầu kia cũng được phép hiển thị
 */
public record PersonView(UUID id,
                         List<PersonName> names,
                         String displayName,
                         Gender gender,
                         Integer generation,
                         boolean alive,
                         Boolean deleted,
                         LifeDate birth,
                         LifeDate death,
                         String nativePlace,
                         String currentPlaceProvince,
                         String currentPlaceFull,
                         String occupation,
                         String biography,
                         String avatarKey,
                         BranchRef primaryBranch,
                         ContactInfo contact,
                         Map<String, Object> attributes,
                         PrivacyLevel privacyLevel,
                         Instant createdAt,
                         Instant updatedAt,
                         Long version,
                         List<RelationshipView> relationships,
                         PersonAccessView access) {

    /** Bản sao có thêm danh sách quan hệ — dựng sau vì quan hệ cần biết đầu kia có hiện được không. */
    public PersonView withRelationships(List<RelationshipView> value) {
        return new PersonView(id, names, displayName, gender, generation, alive, deleted, birth, death,
                nativePlace, currentPlaceProvince, currentPlaceFull, occupation, biography, avatarKey,
                primaryBranch, contact, attributes, privacyLevel, createdAt, updatedAt, version,
                value, access);
    }
}
