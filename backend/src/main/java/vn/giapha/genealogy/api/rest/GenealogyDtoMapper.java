package vn.giapha.genealogy.api.rest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.giapha.genealogy.api.rest.dto.BranchRefDto;
import vn.giapha.genealogy.api.rest.dto.ContactInfoDto;
import vn.giapha.genealogy.api.rest.dto.DateDualDto;
import vn.giapha.genealogy.api.rest.dto.PageDto;
import vn.giapha.genealogy.api.rest.dto.PersonAccessMetaDto;
import vn.giapha.genealogy.api.rest.dto.PersonDto;
import vn.giapha.genealogy.api.rest.dto.PersonNameDto;
import vn.giapha.genealogy.api.rest.dto.PersonSummaryDto;
import vn.giapha.genealogy.api.rest.dto.RelationshipDto;
import vn.giapha.genealogy.api.rest.dto.PrivacySettingsDto;
import vn.giapha.genealogy.api.rest.dto.TreeProjectionDto;
import vn.giapha.genealogy.application.view.BranchRef;
import vn.giapha.genealogy.application.view.PageView;
import vn.giapha.genealogy.application.view.PersonAccessView;
import vn.giapha.genealogy.application.view.PersonSummaryView;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.application.view.RelationshipView;
import vn.giapha.genealogy.application.view.TreeProjectionView;
import vn.giapha.genealogy.domain.ContactInfo;
import vn.giapha.genealogy.domain.PrivacyConsent;
import vn.giapha.genealogy.domain.PrivacyFieldGroup;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.LunarDate;

/**
 * Chuyển view của tầng application thành DTO của contract.
 *
 * <h2>Vì sao không trả thẳng view ra HTTP</h2>
 * Tên trường của contract và tên trường của mô hình đọc cố ý khác nhau ở vài chỗ ({@code isAlive}
 * và {@code alive}, {@code avatarUrl} và {@code avatarKey}, {@code meta} và {@code access}). Có một
 * lớp dịch tường minh nghĩa là đổi tên trong nội bộ không làm vỡ client, và ngược lại - đúng tinh
 * thần contract-first.
 *
 * <p><b>Bất biến phải giữ:</b> giá trị {@code null} ở view phải ra thành trường <b>vắng mặt</b>
 * trong JSON (mọi DTO đều {@code @JsonInclude(NON_NULL)}), chứ không bao giờ thành {@code null},
 * chuỗi rỗng hay {@code "***"}.</p>
 */
public final class GenealogyDtoMapper {

    private GenealogyDtoMapper() {
    }

    public static PersonDto toDto(PersonView view) {
        return toDto(view, Map.of());
    }

    /**
     * Như {@link #toDto(PersonView)} nhưng gắn thêm tóm tắt của <b>đầu kia</b> vào từng cạnh quan hệ.
     *
     * <p>{@code otherEnds} phải đã đi qua bộ lọc phân tầng riêng tư của <b>chính người gọi hiện
     * tại</b> - xem {@code RelationshipSummaryLoader}. Đừng bao giờ dựng map này từ thực thể hay từ
     * một bản cache dùng chung: nội dung tóm tắt phụ thuộc người gọi, và một bản của vai này rơi vào
     * tay vai khác là rò rỉ dữ liệu không để lại dấu vết nào trong log.</p>
     *
     * @param otherEnds tóm tắt đã lọc, tra theo id nhân khẩu; id thiếu trong map thì cạnh chỉ mang id
     */
    public static PersonDto toDto(PersonView view, Map<UUID, PersonSummaryDto> otherEnds) {
        if (view == null) {
            return null;
        }
        Map<UUID, PersonSummaryDto> others = otherEnds == null ? Map.of() : otherEnds;
        return new PersonDto(
                view.id(),
                view.names() == null ? null : view.names().stream()
                        .map(GenealogyDtoMapper::toDto).toList(),
                view.displayName(),
                contractGender(view.gender()),
                view.generation(),
                view.alive(),
                view.deleted(),
                toDto(view.birth()),
                toDto(view.death()),
                view.nativePlace(),
                view.currentPlaceProvince(),
                view.currentPlaceFull(),
                view.occupation(),
                view.biography(),
                view.avatarKey(),
                toDto(view.primaryBranch()),
                toDto(view.contact()),
                view.attributes(),
                toDto(view.privacyConsent()),
                view.createdAt(),
                view.updatedAt(),
                view.version(),
                view.relationships() == null || view.relationships().isEmpty() ? null
                        : view.relationships().stream()
                                .map(rel -> toDto(rel, view.id(), others)).toList(),
                toDto(view.access()));
    }

    public static PersonSummaryDto toDto(PersonSummaryView view) {
        if (view == null) {
            return null;
        }
        return new PersonSummaryDto(view.id(), view.displayName(), view.nameHanNom(),
                contractGender(view.gender()),
                view.generation(), view.alive(), view.birthYear(), view.deathYear(),
                toDto(view.primaryBranch()), view.nativePlace(), view.avatarKey(),
                view.matchedNameType());
    }

    public static PageDto<PersonSummaryDto> toDto(PageView<PersonSummaryView> page) {
        return new PageDto<>(page.items().stream().map(GenealogyDtoMapper::toDto).toList(),
                new PageDto.PageMetaDto(page.page().page(), page.page().size(),
                        page.page().totalElements(), page.page().totalPages(),
                        page.page().hasNext(), page.page().sort()));
    }

    public static TreeProjectionDto toDto(TreeProjectionView view) {
        List<TreeProjectionDto.TreeNodeDto> nodes = view.nodes().stream()
                .map(node -> new TreeProjectionDto.TreeNodeDto(node.id(), toDto(node.person()),
                        node.depth(), node.parentIds(), node.spouseIds(), node.childCount(),
                        node.hasMoreDescendants(), node.badges()))
                .toList();
        List<TreeProjectionDto.TreeEdgeDto> edges = view.edges().stream()
                .map(edge -> new TreeProjectionDto.TreeEdgeDto(edge.id(), edge.source(), edge.target(),
                        edge.relType(), edge.heirKind(), edge.spouseOrder(), edge.validTo()))
                .toList();
        TreeProjectionDto.TreeMetaDto meta = new TreeProjectionDto.TreeMetaDto(view.meta().depth(),
                view.meta().direction(), view.meta().nodeCount(), view.meta().edgeCount(),
                view.meta().truncated(), view.meta().truncatedNodeIds(), view.meta().generatedAt(),
                view.meta().fromCache());
        return new TreeProjectionDto(view.rootId(), nodes, edges, meta);
    }

    public static RelationshipDto toDto(RelationshipView view) {
        return toDto(view, null, Map.of());
    }

    /**
     * Cạnh quan hệ kèm tóm tắt của đầu kia xét theo {@code subjectId}.
     *
     * <p>{@code subjectId} là nhân khẩu <b>đang được xem</b>, không phải một trong hai đầu cạnh nói
     * chung: cùng một cạnh cha–con hiện ra là "cha" trên hồ sơ người con và là "con" trên hồ sơ
     * người cha, nên "đầu kia" chỉ có nghĩa khi biết đang đứng ở đâu mà nhìn.</p>
     */
    public static RelationshipDto toDto(RelationshipView view, UUID subjectId,
                                        Map<UUID, PersonSummaryDto> otherEnds) {
        if (view == null) {
            return null;
        }
        PersonSummaryDto other = null;
        if (subjectId != null && otherEnds != null && !otherEnds.isEmpty()) {
            UUID otherId = view.fromPersonId() == null || view.toPersonId() == null ? null
                    : subjectId.equals(view.fromPersonId()) ? view.toPersonId()
                            : subjectId.equals(view.toPersonId()) ? view.fromPersonId() : null;
            other = otherId == null ? null : otherEnds.get(otherId);
        }
        return new RelationshipDto(view.id(), view.fromPersonId(), view.toPersonId(), view.relType(),
                view.heirKind(), view.spouseOrder(), view.validFrom(), view.validTo(), view.note(),
                other);
    }

    /**
     * Thu một {@link PersonView} <b>đã lọc</b> về dạng tóm tắt.
     *
     * <p>Không lọc lại gì cả, và đó là chủ ý: mọi trường ở đây đều đã do
     * {@code PrivacyTierService.toView} quyết định. Nhân bản luật phân tầng ở tầng api là cách chắc
     * chắn nhất để hai bản luật lệch nhau sau vài lần sửa, và bản lệch ấy sẽ là bản rò rỉ.</p>
     */
    public static PersonSummaryDto toSummary(PersonView view) {
        if (view == null) {
            return null;
        }
        String hanNom = null;
        if (view.names() != null) {
            hanNom = view.names().stream().filter(PersonName::primary).findFirst()
                    .map(PersonName::hanNom).orElse(null);
        }
        Integer birthYear = view.birth() == null ? null : view.birth().year().orElse(null);
        Integer deathYear = view.death() == null ? null : view.death().year().orElse(null);
        return new PersonSummaryDto(view.id(), view.displayName(), hanNom,
                contractGender(view.gender()), view.generation(), view.alive(), birthYear, deathYear,
                toDto(view.primaryBranch()), view.nativePlace(), view.avatarKey(), null);
    }

    public static PersonNameDto toDto(PersonName name) {
        // name_unaccented là generated column của Postgres, domain cố ý không giữ - sinh tay ở đây
        // là mở đường cho bản không dấu lệch với tên có dấu.
        return new PersonNameDto(name.id(), name.type(), name.fullName(), name.hanNom(), null,
                name.primary(), name.note());
    }

    public static BranchRefDto toDto(BranchRef ref) {
        return ref == null ? null : new BranchRefDto(ref.id(), ref.name(), ref.path(), ref.region());
    }

    /**
     * Bản đồng thuận riêng tư ra DTO. {@code null} ở view nghĩa là người gọi không được đọc khối
     * này (chỉ chính chủ và {@code ADMIN} được), nên nó phải <b>vắng mặt</b> khỏi JSON.
     */
    public static PrivacySettingsDto toDto(PrivacyConsent consent) {
        if (consent == null) {
            return null;
        }
        return new PrivacySettingsDto(
                consent.scopeOf(PrivacyFieldGroup.OCCUPATION),
                consent.scopeOf(PrivacyFieldGroup.RESIDENCE_PROVINCE),
                consent.scopeOf(PrivacyFieldGroup.RESIDENCE_FULL),
                consent.scopeOf(PrivacyFieldGroup.CONTACT),
                consent.scopeOf(PrivacyFieldGroup.BIRTH_DETAIL_AND_PHOTO));
    }

    public static ContactInfoDto toDto(ContactInfo contact) {
        return contact == null ? null
                : new ContactInfoDto(contact.phone(), contact.email(), contact.zaloId());
    }

    public static PersonAccessMetaDto toDto(PersonAccessView access) {
        return access == null ? null : new PersonAccessMetaDto(access.visibleTier(), access.canEdit(),
                access.canDelete(), access.canRequestCorrection(), access.isSelf(), access.callerRole());
    }

    /**
     * Thu hẹp giới tính về đúng tập giá trị của contract.
     *
     * <p>Domain có thêm {@code OTHER} nhưng cả {@code openapi.yaml} lẫn {@code schema.graphqls} chỉ
     * khai báo {@code MALE|FEMALE|UNKNOWN}. Trả một giá trị ngoài enum sẽ làm hỏng client đang
     * {@code switch} trên nó, và với GraphQL thì hỏng ngay ở khâu tuần tự hoá. Ánh xạ về
     * {@code UNKNOWN} là lựa chọn an toàn tạm thời - việc bổ sung {@code OTHER} vào contract cần
     * BA chốt vì đó là thay đổi phá vỡ.</p>
     */
    private static Gender contractGender(Gender gender) {
        return gender == Gender.OTHER ? Gender.UNKNOWN : gender;
    }

    /** Mốc song lịch; {@code canChi} và nhãn hiển thị thuộc context {@code calendar}, chưa nối dây. */
    public static DateDualDto toDto(LifeDate date) {
        if (date == null) {
            return null;
        }
        LunarDate lunar = date.lunar();
        DateDualDto.LunarDateDto lunarDto = lunar == null ? null
                : new DateDualDto.LunarDateDto(lunar.year(), lunar.month(), lunar.day(),
                        lunar.leapMonth(), null, null);
        return new DateDualDto(date.solar(), lunarDto, date.precision());
    }
}
