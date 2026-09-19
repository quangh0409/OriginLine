package vn.giapha.genealogy.api.rest.public_;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.giapha.genealogy.api.rest.GenealogyDtoMapper;
import vn.giapha.genealogy.api.rest.dto.PersonAccessMetaDto;
import vn.giapha.genealogy.api.rest.public_.dto.PublicPageDto;
import vn.giapha.genealogy.api.rest.public_.dto.PublicPersonDto;
import vn.giapha.genealogy.api.rest.public_.dto.PublicPersonSummaryDto;
import vn.giapha.genealogy.api.rest.public_.dto.PublicRelationDto;
import vn.giapha.genealogy.api.rest.public_.dto.PublicTreeDto;
import vn.giapha.genealogy.application.CallerRole;
import vn.giapha.genealogy.application.GenealogyProblemCodes;
import vn.giapha.genealogy.application.view.PageView;
import vn.giapha.genealogy.application.view.PersonAccessView;
import vn.giapha.genealogy.application.view.PersonSummaryView;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.application.view.RelationshipView;
import vn.giapha.genealogy.application.view.TreeDirection;
import vn.giapha.genealogy.application.view.TreeEdgeView;
import vn.giapha.genealogy.application.view.TreeNodeView;
import vn.giapha.genealogy.application.view.TreeProjectionView;
import vn.giapha.shared.exception.NotFoundException;

/**
 * <b>Cánh cửa duy nhất</b> dẫn từ view của tầng ứng dụng ra DTO công khai — và là chốt chặn cuối
 * cùng cho luật "Khách không thấy bất kỳ người còn sống nào" (BA v2 §10, Nghị định 13/2023).
 *
 * <h2>Vì sao kiểm lại một lần nữa, khi tầng ứng dụng đã lọc rồi</h2>
 * {@code PrivacyTierService.tierFor()} trả {@code T1} cho Khách nhìn người còn sống <b>thay vì từ
 * chối</b>. Hệ thống hiện an toàn chỉ vì mọi lối vào đều gọi {@code canSee} trước — nghĩa là
 * <i>"gọi canSee trước"</i> là một <b>bất biến bảo mật không được kiểu dữ liệu bảo vệ</b>. Bề mặt
 * công khai mở thêm một loạt lối vào mới, nên nó không đặt cược vào bất biến ấy: mỗi bản ghi sắp
 * rời khỏi tiến trình đều bị hỏi lại một câu duy nhất — {@code alive} có phải {@code false} không.
 *
 * <p>Câu hỏi ấy cố ý là một phép so sánh trực tiếp trên {@code alive}, <b>không</b> phải một phép
 * so tầng. Bẫy anh em: {@code VisibleTier.atLeast()} trả {@code true} cho <i>mọi</i> so sánh khi
 * tier là {@code PUBLIC}, mà người đã khuất luôn ở {@code PUBLIC} — nên bất kỳ phép kiểm nào dựa
 * trên {@code tier.atLeast(...)} đều vô nghĩa ở đây.</p>
 *
 * <h2>Hai kiểu phản ứng, khác nhau có chủ ý</h2>
 * <ul>
 *   <li><b>Một hồ sơ đơn lẻ</b> mà lại là người còn sống ⇒ ném {@code 404}. Không phải {@code 403}:
 *       trả 403 là tự xác nhận người đó tồn tại — đúng thứ mà việc lọc đang giấu.</li>
 *   <li><b>Trong danh sách / phả đồ</b> ⇒ lặng lẽ loại bỏ, đúng như thể bản ghi không có. Ném lỗi
 *       ở đây sẽ biến sự tồn tại của một người còn sống thành thứ dò được bằng cách quan sát endpoint
 *       nào bị hỏng.</li>
 * </ul>
 * Cả hai trường hợp đều ghi {@code log.error}: bản ghi người còn sống chạm tới lớp này nghĩa là một
 * lớp phòng thủ phía trên đã thủng, và đó là sự cố đáng dựng cảnh báo chứ không phải chuyện thường.
 */
@Component
public class PublicVisibilityGuard {

    private static final Logger log = LoggerFactory.getLogger(PublicVisibilityGuard.class);

    /** Thông điệp 404 dùng chung: giống hệt nhau cho "không có thật" và "không được phép biết". */
    private static final String NOT_FOUND_MESSAGE =
            "Khong tim thay nhan khau cong khai voi dinh danh ";

    // =====================================================================================
    // Hồ sơ đơn lẻ
    // =====================================================================================

    /**
     * Hồ sơ công khai của một người đã khuất.
     *
     * @throws NotFoundException nếu {@code view} rỗng, hoặc — trường hợp không được phép xảy ra —
     *                           là người còn sống
     */
    public PublicPersonDto person(PersonView view) {
        if (view == null) {
            throw new NotFoundException(GenealogyProblemCodes.NOT_FOUND, NOT_FOUND_MESSAGE + "yeu cau");
        }
        requireDeceased(view.id(), view.alive());
        PersonAccessMetaDto meta = guestMeta(view);
        return new PublicPersonDto(
                view.id(),
                view.names() == null ? List.of()
                        : view.names().stream().map(GenealogyDtoMapper::toDto).toList(),
                view.displayName(),
                view.gender(),
                view.generation(),
                false,
                GenealogyDtoMapper.toDto(view.birth()),
                GenealogyDtoMapper.toDto(view.death()),
                view.nativePlace(),
                view.biography(),
                view.avatarKey(),
                GenealogyDtoMapper.toDto(view.primaryBranch()),
                relations(view.relationships()),
                meta);
    }

    /**
     * {@code meta} của Khách — <b>chép</b> từ {@code PersonView.access()}, không tính lại.
     *
     * <h2>Vì sao không tự dựng một {@code meta} "của Khách" tại đây</h2>
     * Tự dựng nghĩa là viết lại luật phân quyền bằng tay ở một chỗ thứ hai, và dự án vừa tốn công
     * xoá đúng hai bản chép luật riêng tư như vậy. Giá trị này do {@code PrivacyTierService.accessOf}
     * tính, trên {@code CallerContext} đã bị {@code PublicGuestScope.asGuest} hạ về Khách — nên nó
     * <i>đã</i> là quyền của Khách; việc duy nhất còn lại là kiểm chứng điều đó.
     *
     * <h2>Kiểm chứng, và vì sao sai thì phải chết chứ không được sửa lặng lẽ</h2>
     * {@code meta} không phải của Khách chỉ có một nguyên nhân: bước hạ ngữ cảnh đã không chạy.
     * Khi đó <b>toàn bộ phản hồi</b> — chứ không riêng {@code meta} — được tính theo vai của người
     * mang token, mà controller lại đang gắn {@code Cache-Control: public} lên nó. "Sửa" mỗi
     * {@code meta} rồi trả về là giấu đi đúng triệu chứng của một sự cố rò rỉ qua proxy. Vì vậy ở
     * đây ném lỗi và ghi {@code log.error}.
     *
     * @throws IllegalStateException khi {@code access} rỗng hoặc không phải quyền của Khách
     */
    private PersonAccessMetaDto guestMeta(PersonView view) {
        PersonAccessView access = view.access();
        if (access == null) {
            log.error("Ho so cong khai {} khong mang PersonAccessView - tang ung dung da doi hop dong",
                    view.id());
            throw new IllegalStateException(
                    "PersonView cho be mat cong khai phai mang access cua Khach");
        }
        if (access.callerRole() != CallerRole.GUEST
                || access.canEdit() || access.canDelete() || access.canRequestCorrection()
                || access.isSelf()) {
            log.error("RO RI BI CHAN: be mat cong khai tinh quyen theo vai {} (edit={}, delete={},"
                            + " correction={}, self={}) cho ho so {} - PublicGuestScope da khong chay",
                    access.callerRole(), access.canEdit(), access.canDelete(),
                    access.canRequestCorrection(), access.isSelf(), view.id());
            throw new IllegalStateException(
                    "Be mat cong khai phai chay voi ngu canh Khach, xem PublicGuestScope");
        }
        return GenealogyDtoMapper.toDto(access);
    }

    // =====================================================================================
    // Danh sách
    // =====================================================================================

    /**
     * Trang kết quả tìm kiếm công khai.
     *
     * <p>Không mang {@code totalElements} sang: xem {@link PublicPageDto}. {@code hasNext} lấy từ
     * tầng ứng dụng nhưng bị {@code &&} với trần trang của cổng công khai ở controller, nên kẻ quét
     * không lần được tới cuối danh sách.</p>
     */
    public PublicPageDto<PublicPersonSummaryDto> page(PageView<PersonSummaryView> page) {
        List<PublicPersonSummaryDto> items = new ArrayList<>();
        for (PersonSummaryView item : page.items()) {
            if (dropIfAlive(item)) {
                continue;
            }
            items.add(summary(item));
        }
        return new PublicPageDto<>(items, new PublicPageDto.PublicPageMetaDto(
                page.page().page(), page.page().size(), page.page().hasNext()));
    }

    // =====================================================================================
    // Phả đồ
    // =====================================================================================

    /**
     * Phả đồ công khai: bỏ người còn sống, bỏ mọi cạnh chạm vào họ, và bỏ cả các id tham chiếu tới
     * họ trong {@code parentIds}/{@code spouseIds}.
     *
     * <p>Bỏ id tham chiếu là bước dễ quên nhất và cũng là chỗ rò rỉ tinh vi nhất: giữ lại một
     * {@code parentId} trỏ tới node không có trong {@code nodes[]} chẳng khác gì nói "cha của người
     * này còn sống, và đây là định danh của ông ấy".</p>
     *
     * @param requestedDepth độ sâu client đã xin — căn cứ duy nhất để suy ra {@code expandable}
     */
    public PublicTreeDto tree(TreeProjectionView view, int requestedDepth) {
        Set<UUID> visible = new LinkedHashSet<>();
        List<TreeNodeView> keep = new ArrayList<>();
        for (TreeNodeView node : view.nodes()) {
            if (node.person() == null || dropIfAlive(node.person())) {
                continue;
            }
            visible.add(node.id());
            keep.add(node);
        }

        Set<UUID> truncated = Set.copyOf(view.meta().truncatedNodeIds() == null
                ? List.of() : view.meta().truncatedNodeIds());
        TreeDirection direction = view.meta().direction();

        List<PublicTreeDto.PublicTreeNodeDto> nodes = new ArrayList<>();
        for (TreeNodeView node : keep) {
            nodes.add(new PublicTreeDto.PublicTreeNodeDto(
                    node.id(),
                    summary(node.person()),
                    node.depth(),
                    onlyVisible(node.parentIds(), visible),
                    onlyVisible(node.spouseIds(), visible),
                    expandable(node, requestedDepth, direction, truncated)));
        }

        List<PublicTreeDto.PublicTreeEdgeDto> edges = new ArrayList<>();
        for (TreeEdgeView edge : view.edges()) {
            if (!visible.contains(edge.source()) || !visible.contains(edge.target())) {
                continue;
            }
            edges.add(new PublicTreeDto.PublicTreeEdgeDto(edge.id(), edge.source(), edge.target(),
                    edge.relType(), edge.heirKind(), edge.spouseOrder()));
        }

        if (!visible.contains(view.rootId())) {
            // Gốc bị lọc: không tiết lộ rằng nó tồn tại. Tầng ứng dụng đã ném 404 ở tình huống này,
            // nhưng đường về cũng phải tự đứng vững nếu tầng đó đổi.
            log.error("Goc pha do cong khai khong hien duoc nhung projection van tra ve: {}",
                    view.rootId());
            throw new NotFoundException(GenealogyProblemCodes.NOT_FOUND,
                    NOT_FOUND_MESSAGE + view.rootId());
        }

        return new PublicTreeDto(view.rootId(), nodes, edges,
                new PublicTreeDto.PublicTreeMetaDto(view.meta().depth(), direction, nodes.size(),
                        edges.size(), view.meta().truncated(), true));
    }

    // =====================================================================================
    // Nội bộ
    // =====================================================================================

    private PublicPersonSummaryDto summary(PersonSummaryView view) {
        return new PublicPersonSummaryDto(view.id(), view.displayName(), view.nameHanNom(),
                view.gender(), view.generation(), false, view.birthYear(), view.deathYear(),
                GenealogyDtoMapper.toDto(view.primaryBranch()), view.nativePlace(), view.avatarKey(),
                view.matchedNameType());
    }

    private List<PublicRelationDto> relations(List<RelationshipView> views) {
        if (views == null || views.isEmpty()) {
            return List.of();
        }
        List<PublicRelationDto> result = new ArrayList<>(views.size());
        for (RelationshipView view : views) {
            result.add(new PublicRelationDto(view.id(), view.fromPersonId(), view.toPersonId(),
                    view.relType(), view.heirKind(), view.spouseOrder()));
        }
        return result;
    }

    /** {@code true} ⇒ bản ghi phải biến mất khỏi phản hồi. */
    private boolean dropIfAlive(PersonSummaryView view) {
        if (!view.alive()) {
            return false;
        }
        log.error("RO RI BI CHAN: nguoi con song {} lot toi bien API cong khai trong mot danh sach",
                view.id());
        return true;
    }

    private void requireDeceased(UUID personId, boolean alive) {
        if (!alive) {
            return;
        }
        log.error("RO RI BI CHAN: nguoi con song {} lot toi bien API cong khai o ho so don le",
                personId);
        throw new NotFoundException(GenealogyProblemCodes.NOT_FOUND, NOT_FOUND_MESSAGE + personId);
    }

    private List<UUID> onlyVisible(List<UUID> ids, Set<UUID> visible) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().filter(visible::contains).toList();
    }

    /**
     * Node còn mở rộng được hay không — suy ra <b>chỉ từ tham số yêu cầu và tập node hiện ra</b>.
     *
     * <p>Không dùng {@code hasMoreDescendants} của tầng ứng dụng làm nguồn duy nhất: với Khách,
     * {@code TreeProjectionService} cố ý đặt số con bằng số con <i>đang hiện</i> nên cờ ấy luôn
     * {@code false} và cổng công khai sẽ mất hẳn khả năng đi sâu. Thay vào đó, node nằm ở rìa độ
     * sâu đã xin (hoặc bị cắt vì chạm {@code maxNodes}) thì coi là mở rộng được — kết luận này rút
     * ra từ chính câu hỏi của client nên không nói gì về người bị ẩn.</p>
     */
    private boolean expandable(TreeNodeView node, int requestedDepth, TreeDirection direction,
                               Set<UUID> truncated) {
        if (node.hasMoreDescendants() || truncated.contains(node.id())) {
            return true;
        }
        if (requestedDepth <= 0) {
            return true;
        }
        int distance = Math.abs(node.depth());
        boolean atDescendantEdge = direction != TreeDirection.ANCESTORS && node.depth() > 0;
        boolean atAncestorEdge = direction != TreeDirection.DESCENDANTS && node.depth() < 0;
        return distance >= requestedDepth && (atDescendantEdge || atAncestorEdge);
    }
}
