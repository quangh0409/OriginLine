package vn.giapha.genealogy.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.application.command.LinkRelationshipCommand;
import vn.giapha.genealogy.application.view.RelationshipView;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.RelType;
import vn.giapha.genealogy.domain.Relationship;
import vn.giapha.genealogy.domain.event.RelationshipLinkedEvent;
import vn.giapha.genealogy.domain.port.AuditPort;
import vn.giapha.genealogy.domain.port.BranchRepository;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.genealogy.domain.port.RelationshipRepository;
import vn.giapha.genealogy.domain.port.TreeCachePort;
import vn.giapha.genealogy.domain.port.TreeGraphPort;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.PersonId;

/**
 * Nối hai nhân khẩu bằng một cạnh quan hệ.
 *
 * <h2>Bất biến quan trọng nhất của cả context</h2>
 * <b>Cạnh trong đồ thị AGE và dòng {@code relationship} được ghi trong CÙNG một transaction.</b>
 * Đồ thị là nguồn chân lý, bảng là bản chiếu để có khoá ngoại, nhật ký và truy vấn SQL thuần.
 * Ghi một bên mà thiếu bên kia thì không có gì báo lỗi, nhưng phả đồ và báo cáo sẽ nói hai điều
 * khác nhau - và không ai biết bên nào đúng. Vì thế {@link #attach} không tự mở transaction: nó
 * <b>bắt buộc</b> phải chạy bên trong transaction của use case gọi nó.
 *
 * <h2>Đời thứ</h2>
 * Nối cha-con xong thì con được đặt đời thứ = đời cha + 1, nhưng <b>chỉ khi con chưa có đời thứ</b>.
 * Đánh số lại cả cây con là một nghiệp vụ khác hẳn (ảnh hưởng hàng trăm bản ghi, cần audit riêng);
 * âm thầm làm việc đó trong một lệnh nối cạnh là cách chắc chắn để một hôm nào đó cả nhánh cây
 * nhảy đời mà không ai giải thích được vì sao.
 */
@Service
public class LinkRelationshipService {

    private static final Logger log = LoggerFactory.getLogger(LinkRelationshipService.class);

    private final PersonRepository persons;
    private final RelationshipRepository relationships;
    private final TreeGraphPort graph;
    private final BranchRepository branches;
    private final AuditPort audit;
    private final TreeCachePort treeCache;
    private final PrivacyTierService privacy;
    private final GenealogyAccessGuard guard;
    private final DomainEventPublisher events;

    public LinkRelationshipService(PersonRepository persons, RelationshipRepository relationships,
                                   TreeGraphPort graph, BranchRepository branches, AuditPort audit,
                                   TreeCachePort treeCache, PrivacyTierService privacy,
                                   GenealogyAccessGuard guard, DomainEventPublisher events) {
        this.persons = persons;
        this.relationships = relationships;
        this.graph = graph;
        this.branches = branches;
        this.audit = audit;
        this.treeCache = treeCache;
        this.privacy = privacy;
        this.guard = guard;
        this.events = events;
    }

    /** Use case đầy đủ: kiểm quyền trên cả hai đầu cạnh, ghi cạnh, ghi nhật ký, dọn cache. */
    @Transactional
    public RelationshipView link(LinkRelationshipCommand cmd) {
        CallerContext caller = privacy.caller();
        Person from = load(cmd.fromPersonId());
        Person to = load(cmd.toPersonId());
        // Arrays.asList chứ không phải List.of: nhân khẩu chưa gắn chi có branchId null, và
        // List.of ném NPE ngay tại đó.
        BranchDirectory dir = BranchDirectory.load(branches,
                java.util.Arrays.asList(from.primaryBranchId(), to.primaryBranchId()));
        guard.requireWriteAccess(caller, dir.pathOf(from.primaryBranchId()));
        guard.requireWriteAccess(caller, dir.pathOf(to.primaryBranchId()));

        Relationship rel = attach(cmd);
        treeCache.evictAll();
        return RelationshipView.of(rel);
    }

    /**
     * Ghi cạnh vào <b>cả</b> đồ thị lẫn bản chiếu, cộng một dòng {@code audit_log}.
     *
     * <p>Cố ý <b>không</b> có {@code @Transactional} và <b>không</b> kiểm quyền: đây là phần dùng
     * chung với {@code AddPersonService}, nơi transaction và việc kiểm quyền đã được mở ở use case
     * cha. Gọi thẳng phương thức này từ ngoài một transaction là tạo ra dữ liệu lệch.</p>
     */
    Relationship attach(LinkRelationshipCommand cmd) {
        validate(cmd);
        Relationship rel = build(cmd);
        // Thứ tự: đồ thị trước, bản chiếu sau. Cả hai nằm trong cùng transaction nên thứ tự không
        // đổi kết quả, nhưng đặt đồ thị trước thì lỗi vi phạm chu trình lộ ra sớm hơn.
        writeGraphEdge(cmd, rel);
        relationships.save(rel);
        syncGenerationOfChild(cmd);

        audit.record("Relationship", rel.id().toString(), AuditPort.Action.LINK_RELATIONSHIP,
                null, auditSnapshot(rel), List.of("relType", "fromPersonId", "toPersonId"), cmd.note());
        events.publish(new RelationshipLinkedEvent(rel.id(), rel.fromPersonId(), rel.toPersonId(),
                rel.relType().name()));
        log.info("Da noi quan he {} giua {} va {}", rel.relType(), rel.fromPersonId(), rel.toPersonId());
        return rel;
    }

    private void validate(LinkRelationshipCommand cmd) {
        if (cmd.fromPersonId() == null || cmd.toPersonId() == null || cmd.relType() == null) {
            throw new DomainException(GenealogyProblemCodes.INVALID_RELATIONSHIP,
                    "Canh quan he phai co du hai dau va loai quan he");
        }
        if (cmd.fromPersonId().equals(cmd.toPersonId())) {
            throw new DomainException(GenealogyProblemCodes.INVALID_RELATIONSHIP,
                    "Mot nguoi khong the co quan he voi chinh minh");
        }
        if (!persons.exists(PersonId.of(cmd.fromPersonId()))) {
            throw notFound(cmd.fromPersonId());
        }
        if (!persons.exists(PersonId.of(cmd.toPersonId()))) {
            throw notFound(cmd.toPersonId());
        }
        if (cmd.relType().isParentEdge()) {
            if (relationships.existsParentEdge(cmd.fromPersonId(), cmd.toPersonId())) {
                throw new DomainException(GenealogyProblemCodes.INVALID_RELATIONSHIP,
                        "Canh cha/me - con giua hai nguoi nay da ton tai");
            }
            // Con lại là tổ tiên của cha thì cây có chu trình: đi xuống mãi cũng quay về chính
            // mình, và mọi phép duyệt (LCA, danh xưng, phả đồ) sẽ chạy vô tận.
            if (graph.isAncestorOf(cmd.toPersonId(), cmd.fromPersonId())) {
                throw new GenealogyConflictException(GenealogyProblemCodes.RELATIONSHIP_CYCLE,
                        "Quan he cha/me - con nay tao ra chu trinh trong pha he");
            }
        }
    }

    private Relationship build(LinkRelationshipCommand cmd) {
        UUID id = UUID.randomUUID();
        try {
            return switch (cmd.relType()) {
                case PARENT_BIO, PARENT_ADOPT -> Relationship
                        .parent(id, cmd.fromPersonId(), cmd.toPersonId(),
                                cmd.relType() == RelType.PARENT_ADOPT)
                        .note(cmd.note());
                case SPOUSE -> Relationship
                        .spouse(id, cmd.fromPersonId(), cmd.toPersonId(), cmd.spouseOrder(),
                                cmd.validFrom(), cmd.validTo())
                        .note(cmd.note());
                case HEIR -> Relationship
                        .heir(id, cmd.fromPersonId(), cmd.toPersonId(), cmd.heirKind())
                        .note(cmd.note());
            };
        } catch (IllegalArgumentException ex) {
            // Domain tự bảo vệ bất biến (spouseOrder chỉ cho SPOUSE, heirKind bắt buộc với HEIR).
            // Dịch sang mã nghiệp vụ để client biết sửa gì, thay vì nhận 500.
            throw new DomainException(GenealogyProblemCodes.INVALID_RELATIONSHIP, ex.getMessage(), ex);
        }
    }

    private void writeGraphEdge(LinkRelationshipCommand cmd, Relationship rel) {
        switch (cmd.relType()) {
            case PARENT_BIO, PARENT_ADOPT ->
                    graph.linkParent(cmd.fromPersonId(), cmd.toPersonId(), cmd.relType());
            case SPOUSE -> graph.linkSpouse(cmd.fromPersonId(), cmd.toPersonId(), rel.spouseOrder(),
                    cmd.validFrom() == null ? null : cmd.validFrom().toString(),
                    cmd.validTo() == null ? null : cmd.validTo().toString());
            case HEIR -> graph.linkHeir(cmd.fromPersonId(), cmd.toPersonId(), rel.heirKind());
        }
    }

    /** Đặt đời thứ cho người con nếu người đó chưa có - xem ghi chú ở javadoc của lớp. */
    private void syncGenerationOfChild(LinkRelationshipCommand cmd) {
        if (!cmd.relType().isParentEdge()) {
            return;
        }
        PersonId childId = PersonId.of(cmd.toPersonId());
        Person child = persons.byId(childId).orElse(null);
        if (child == null || child.generation() != null) {
            return;
        }
        Integer parentGeneration = persons.generationOf(PersonId.of(cmd.fromPersonId())).orElse(null);
        if (parentGeneration == null) {
            return;
        }
        child.placeInGeneration(parentGeneration + 1);
        persons.save(child);
        graph.syncPersonNode(child.rawId(), child.gender() == null ? null : child.gender().name(),
                child.generation(), child.isDeleted());
    }

    private Person load(UUID id) {
        return persons.byId(PersonId.of(id)).orElseThrow(() -> notFound(id));
    }

    private NotFoundException notFound(UUID id) {
        return new NotFoundException(GenealogyProblemCodes.NOT_FOUND,
                "Khong tim thay nhan khau voi dinh danh " + id);
    }

    /** Ảnh chụp cạnh cho nhật ký - không có trường nào thuộc Tầng 3 nên chép được nguyên vẹn. */
    private Map<String, Object> auditSnapshot(Relationship rel) {
        return Map.of(
                "id", rel.id().toString(),
                "fromPersonId", rel.fromPersonId().toString(),
                "toPersonId", rel.toPersonId().toString(),
                "relType", rel.relType().name(),
                "spouseOrder", String.valueOf(rel.spouseOrder()),
                "heirKind", String.valueOf(rel.heirKind()));
    }
}
