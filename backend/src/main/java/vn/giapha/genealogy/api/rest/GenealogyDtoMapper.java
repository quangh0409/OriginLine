package vn.giapha.genealogy.api.rest;

import java.util.List;
import vn.giapha.genealogy.api.rest.dto.BranchRefDto;
import vn.giapha.genealogy.api.rest.dto.ContactInfoDto;
import vn.giapha.genealogy.api.rest.dto.DateDualDto;
import vn.giapha.genealogy.api.rest.dto.PageDto;
import vn.giapha.genealogy.api.rest.dto.PersonAccessMetaDto;
import vn.giapha.genealogy.api.rest.dto.PersonDto;
import vn.giapha.genealogy.api.rest.dto.PersonNameDto;
import vn.giapha.genealogy.api.rest.dto.PersonSummaryDto;
import vn.giapha.genealogy.api.rest.dto.RelationshipDto;
import vn.giapha.genealogy.api.rest.dto.TreeProjectionDto;
import vn.giapha.genealogy.application.view.BranchRef;
import vn.giapha.genealogy.application.view.PageView;
import vn.giapha.genealogy.application.view.PersonAccessView;
import vn.giapha.genealogy.application.view.PersonSummaryView;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.application.view.RelationshipView;
import vn.giapha.genealogy.application.view.TreeProjectionView;
import vn.giapha.genealogy.domain.ContactInfo;
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
        if (view == null) {
            return null;
        }
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
                view.privacyLevel(),
                view.createdAt(),
                view.updatedAt(),
                view.version(),
                view.relationships() == null || view.relationships().isEmpty() ? null
                        : view.relationships().stream().map(GenealogyDtoMapper::toDto).toList(),
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
        return new RelationshipDto(view.id(), view.fromPersonId(), view.toPersonId(), view.relType(),
                view.heirKind(), view.spouseOrder(), view.validFrom(), view.validTo(), view.note());
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
