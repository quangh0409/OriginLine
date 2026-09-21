package vn.giapha.content.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.content.application.command.CreateHonourCommand;
import vn.giapha.content.application.command.HonourQuery;
import vn.giapha.content.application.command.ReviewHonourCommand;
import vn.giapha.content.application.command.UpdateHonourCommand;
import vn.giapha.content.domain.ContentStatus;
import vn.giapha.content.domain.Honour;
import vn.giapha.content.domain.port.HonourRepository;
import vn.giapha.membership.application.MemberScopeView;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.BranchPath;

/**
 * Vinh danh: khai → duyệt → hiển thị, và <b>lọc theo nhóm trường riêng tư thứ sáu</b>.
 *
 * <h2>Hai câu hỏi khác nhau, đừng trộn: "ai được XỬ LÝ" và "ai được XEM"</h2>
 * Đây là điểm thiết kế quan trọng nhất của lớp này, và bỏ qua nó thì luồng duyệt <b>không chạy</b>.
 *
 * <p>Nhóm {@code PrivacyFieldGroup.HONOUR} <b>mặc định KÍN</b> — đó là toàn bộ giá trị của mô hình
 * V8. Hệ quả cụ thể: một vinh danh vừa được khai cho một người còn sống, chưa ai bật công tắc, thì
 * theo bộ lọc riêng tư <i>không ai</i> xem được ngoài chính chủ và Hội đồng. Nếu áp bộ lọc ấy lên
 * mọi lối đọc thì <b>hàng đợi duyệt của Trưởng chi luôn rỗng</b> và bản ghi nằm đó vĩnh viễn.</p>
 *
 * <p>Vì thế hai câu hỏi được tách hẳn:</p>
 * <ul>
 *   <li><b>Xử lý</b> ({@code PENDING} · {@code WITHDRAWN}) — thấy được bởi <i>người khai</i> và
 *       <i>người duyệt trong phạm vi</i>, lọc bằng {@code ltree} <b>trong SQL</b>. Đây không phải
 *       công bố dữ liệu; đây là một hồ sơ đang được xét. Cùng tinh thần với
 *       {@code ChangeRequestView.redacted()}: nội dung đề nghị hiện với người gửi và người có
 *       quyền duyệt nó, không hiện với ai khác.</li>
 *   <li><b>Xem</b> ({@code PUBLISHED}) — <b>bắt buộc</b> đi qua
 *       {@code PersonVisibility.allows(HONOUR)} của {@code genealogy}, cho từng nhân khẩu một.
 *       Người đã khuất công khai; người còn sống theo đúng ý chí của chính họ.</li>
 * </ul>
 *
 * <p><b>Hệ quả phải nói thẳng cho người vận hành:</b> duyệt một vinh danh của người còn sống chưa
 * bật công tắc thì nó <i>vẫn không hiện ra với ai</i> ngoài chính chủ. Đó không phải lỗi — đó
 * chính là điều nhóm trường thứ sáu được thêm vào để làm: quyền công bố nằm ở chủ thể, còn quyền
 * duyệt chỉ nói "bản ghi này có thật".</p>
 *
 * <h2>Chi/ngành luôn là chi HIỆN TẠI của nhân khẩu</h2>
 * Không chụp lại (xem {@code Honour}). Vì thế mọi phép kiểm phạm vi ở đây hỏi
 * {@code ContentAccessGuard.branchOfPerson}, không đọc một cột nào của bảng {@code honour}. Người
 * chịu trách nhiệm về một nhân khẩu là người đang quản chi của nhân khẩu đó — cùng lựa chọn với
 * {@code ChangeRequestService.targetPathOf}.
 */
@Service
public class HonourService {

    private static final Logger log = LoggerFactory.getLogger(HonourService.class);

    private static final String ENTITY = "Honour";

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final HonourRepository honours;
    private final ContentAccessGuard access;
    private final AuthorDirectory persons;
    private final ReviewerDirectory reviewers;
    private final AuditTrailService audit;

    public HonourService(HonourRepository honours, ContentAccessGuard access,
                         AuthorDirectory persons, ReviewerDirectory reviewers,
                         AuditTrailService audit) {
        this.honours = honours;
        this.access = access;
        this.persons = persons;
        this.reviewers = reviewers;
        this.audit = audit;
    }

    // =====================================================================================
    // Ghi
    // =====================================================================================

    /**
     * Khai một vinh danh. Bản ghi sinh ra ở {@link ContentStatus#PENDING}.
     *
     * <p>Người khai phải <b>đã được duyệt vào phả</b>, cùng luật với bài viết và cùng lý do: khai
     * công cho người trong họ là việc của người trong họ. Không có phép kiểm phạm vi ở bước này —
     * cố ý, giống {@code ChangeRequestService.submit}: một người con gái lấy chồng xa vẫn phải khai
     * được tấm bằng của cha mình, dù chi của cha không phải chi cô ấy đang sinh hoạt. Cửa kiểm
     * <i>phạm vi</i> ở bước duyệt, và ở đó nó chặt.</p>
     */
    @Transactional
    public HonourView create(CreateHonourCommand command) {
        MemberScopeView caller = access.requireProvisioned();
        access.requireAuthorPerson(caller);
        requireSubjectExists(command.personId());

        Honour honour;
        try {
            honour = Honour.submit(UUID.randomUUID(), command.personId(), command.kind(),
                    command.title(), command.year(), command.issuer(), command.description(),
                    caller.appUserId());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new DomainException(ContentProblemCodes.VALIDATION_FAILED, ex.getMessage(), ex);
        }
        Honour saved = honours.save(honour);

        audit.record(ENTITY, saved.id().toString(), AuditAction.CREATE, null,
                saved.auditSnapshot(), List.of("kind", "title", "year", "issuer", "description"),
                null);
        log.info("Vinh danh {} ({}) duoc khai cho nhan khau {} boi app_user {}",
                saved.id(), saved.kind(), saved.personId(), caller.appUserId());
        return view(saved, caller);
    }

    /** Sửa. Người khai (khi còn {@code PENDING}) hoặc người duyệt trong phạm vi. */
    @Transactional
    public HonourView update(UpdateHonourCommand command) {
        MemberScopeView caller = access.requireProvisioned();
        Honour honour = load(command.honourId());
        requireEditor(caller, honour);
        requireVersion(honour, command.expectedVersion());

        try {
            honour.edit(command.kind(), command.title(), command.year(), command.issuer(),
                    command.description());
        } catch (IllegalStateException ex) {
            throw new ContentConflictException(ContentProblemCodes.CONTENT_CLOSED, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new DomainException(ContentProblemCodes.VALIDATION_FAILED, ex.getMessage(), ex);
        }
        Honour saved = honours.save(honour);

        audit.record(ENTITY, saved.id().toString(), AuditAction.UPDATE, null,
                saved.auditSnapshot(), changedFields(command), null);
        return view(saved, caller);
    }

    /** Duyệt hoặc từ chối. <b>Cửa kiểm phạm vi.</b> */
    @Transactional
    public HonourView review(ReviewHonourCommand command) {
        MemberScopeView caller = access.requireProvisioned();
        Honour honour = load(command.honourId());
        requireReviewer(caller, honour);

        Instant now = Instant.now();
        try {
            if (command.approve()) {
                honour.approve(caller.appUserId(), now);
            } else {
                honour.reject(caller.appUserId(), command.note(), now);
            }
        } catch (IllegalStateException ex) {
            throw selfReviewOrClosed(ex);
        } catch (IllegalArgumentException ex) {
            throw new DomainException(ContentProblemCodes.VALIDATION_FAILED, ex.getMessage(), ex);
        }
        Honour saved = honours.save(honour);

        audit.record(ENTITY, saved.id().toString(),
                command.approve() ? AuditAction.APPROVE : AuditAction.REJECT, null,
                saved.auditSnapshot(), List.of("status", "reviewedBy", "reviewedAt"),
                command.note());
        log.info("Vinh danh {} -> {} boi app_user {}", saved.id(), saved.status(),
                caller.appUserId());
        return view(saved, caller);
    }

    /** <b>Xoá mềm.</b> Hàng ở lại, cờ {@code is_deleted} bật. Không có lệnh xoá nào. */
    @Transactional
    public void softDelete(UUID honourId) {
        MemberScopeView caller = access.requireProvisioned();
        Honour honour = load(honourId);
        requireEditor(caller, honour);

        try {
            honour.softDelete();
        } catch (IllegalStateException ex) {
            throw new ContentConflictException(ContentProblemCodes.CONTENT_CLOSED, ex.getMessage(), ex);
        }
        Honour saved = honours.save(honour);

        audit.record(ENTITY, saved.id().toString(), AuditAction.SOFT_DELETE, null,
                saved.auditSnapshot(), List.of("isDeleted"), null);
        log.info("Vinh danh {} bi go (xoa mem) boi app_user {}", saved.id(), caller.appUserId());
    }

    // =====================================================================================
    // Đọc
    // =====================================================================================

    /**
     * Tra cứu. <b>Hai tầng lọc, và cả hai đều bắt buộc.</b>
     *
     * <ol>
     *   <li><b>Trong SQL</b> — trạng thái × phạm vi {@code ltree} × chưa xoá mềm. Bản ghi của chi
     *       khác không bao giờ đi vào bộ nhớ tiến trình.</li>
     *   <li><b>Ở đây</b> — nhóm trường riêng tư thứ sáu, hỏi {@code genealogy} cho từng nhân khẩu.
     *       Không thể đẩy tầng này xuống SQL: câu trả lời phụ thuộc cả vào <i>người gọi</i> (chính
     *       chủ? Hội đồng? cùng chi?) lẫn vào <i>chủ thể</i> (còn sống? vị thành niên?), và bản
     *       luật ấy sống ở {@code PrivacyTierService}. Chép nó vào một mệnh đề WHERE là dựng bản
     *       luật riêng tư thứ hai.</li>
     * </ol>
     *
     * <p><b>Cái giá của tầng thứ hai:</b> {@code totalElements} đếm ở SQL, tức <i>trước</i> khi lọc
     * riêng tư, nên một trang có thể trả về ít phần tử hơn {@code size} mà vẫn còn trang sau. Đó là
     * đánh đổi có chủ ý: đếm sau khi lọc thì phải nạp toàn bộ bảng lên để đếm. Giao diện phải dựa
     * vào {@code hasNext}, không dựa vào phép nhân {@code size × totalPages}.</p>
     */
    @Transactional(readOnly = true)
    public ContentPage<HonourView> search(HonourQuery query) {
        MemberScopeView caller = access.requireProvisioned();
        int size = clamp(query.size());
        int page = Math.max(query.page(), 0);

        List<Honour> rows = honours.search(query.personId(), query.kind(), query.branchId(),
                query.status(), caller.appUserId(), caller.managedBranches(), caller.clanWide(),
                size, page * size);
        long total = honours.count(query.personId(), query.kind(), query.branchId(),
                query.status(), caller.appUserId(), caller.managedBranches(), caller.clanWide());

        return new ContentPage<>(views(rows, caller), page, size, total);
    }

    /** Một bản ghi. Không được phép xem ⇒ {@code 404}, không phải {@code 403}. */
    @Transactional(readOnly = true)
    public HonourView byId(UUID honourId) {
        MemberScopeView caller = access.requireProvisioned();
        Honour honour = load(honourId);
        if (honour.isDeleted()) {
            throw NotFoundException.of(ENTITY, honourId);
        }
        Map<UUID, UUID> nhanKhauCuaNguoiDuyet = reviewers.personIdsOf(
                java.util.Collections.singletonList(honour.reviewedBy()));
        Set<UUID> canHoi = new LinkedHashSet<>();
        canHoi.add(honour.personId());
        canHoi.addAll(nhanKhauCuaNguoiDuyet.values());
        PersonLens lens = persons.load(canHoi);
        if (!disclosable(honour, caller, lens)) {
            throw NotFoundException.of(ENTITY, honourId);
        }
        return toView(honour, caller, lens, nhanKhauCuaNguoiDuyet);
    }

    /** Số bản ghi chờ duyệt trong phạm vi — cho badge trên giao diện. */
    @Transactional(readOnly = true)
    public long countPendingForReview() {
        MemberScopeView caller = access.caller();
        if (caller.appUserId() == null) {
            return 0L;
        }
        return honours.countPendingInScope(caller.managedBranches(), caller.clanWide());
    }

    // =====================================================================================
    // Nội bộ
    // =====================================================================================

    private Honour load(UUID honourId) {
        if (honourId == null) {
            throw NotFoundException.of(ENTITY, null);
        }
        return honours.byId(honourId).orElseThrow(() -> NotFoundException.of(ENTITY, honourId));
    }

    /**
     * Chủ thể phải tồn tại <b>và người khai phải được biết là nó tồn tại</b>.
     *
     * <p>Điểm thứ hai mới là điểm đáng nói: nếu chỉ kiểm khoá ngoại thì màn khai vinh danh trở
     * thành máy dò — gửi thử một {@code personId} rồi đọc mã lỗi là biết nhân khẩu ấy có thật hay
     * không. Đi qua {@link PersonLens} làm cả hai ca ("không có" và "không được biết") ra cùng một
     * câu trả lời, đúng nguyên tắc của BA v2 §10.</p>
     */
    private void requireSubjectExists(UUID personId) {
        if (personId == null || !persons.loadOne(personId).visible(personId)) {
            throw new DomainException(ContentProblemCodes.VALIDATION_FAILED,
                    "Khong tim thay nhan khau duoc vinh danh");
        }
    }

    /** Người khai (khi bản ghi còn mở) hoặc người duyệt trong phạm vi. */
    private void requireEditor(MemberScopeView caller, Honour honour) {
        boolean creator = caller.appUserId() != null
                && caller.appUserId().equals(honour.createdBy());
        if (creator && !honour.status().isFinal()) {
            return;
        }
        requireReviewer(caller, honour);
    }

    /**
     * Cửa kiểm quyền duyệt. Chi đích là chi <b>hiện tại</b> của nhân khẩu được vinh danh.
     *
     * <p>Nhân khẩu chưa gắn chi ⇒ {@code null} ⇒ chỉ vai toàn dòng họ đi tiếp được.</p>
     */
    private void requireReviewer(MemberScopeView caller, Honour honour) {
        access.requireReviewer(caller, access.branchOfPerson(honour.personId()));
    }

    /**
     * <b>Bộ lọc nhóm trường thứ sáu.</b> Xem javadoc của lớp: chỉ bản ghi {@code PUBLISHED} mới bị
     * áp, vì nó là thứ duy nhất đang được <i>công bố</i>.
     *
     * <p>Bản ghi {@code PENDING}/{@code WITHDRAWN} lọt được tới đây thì SQL đã chứng minh người gọi
     * là người khai hoặc người duyệt trong phạm vi — không cần và không được lọc thêm, nếu không
     * hàng đợi duyệt sẽ luôn rỗng đúng vào lúc nhóm trường này phát huy tác dụng.</p>
     */
    private boolean disclosable(Honour honour, MemberScopeView caller, PersonLens lens) {
        if (!lens.visible(honour.personId())) {
            // Khong duoc biet nhan khau ton tai thi cung khong duoc biet vinh danh cua ho ton tai.
            return false;
        }
        if (honour.status() != ContentStatus.PUBLISHED) {
            return true;
        }
        return lens.honourVisible(honour.personId());
    }

    private List<HonourView> views(List<Honour> rows, MemberScopeView caller) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, UUID> nhanKhauCuaNguoiDuyet = reviewers.personIdsOf(
                rows.stream().map(Honour::reviewedBy).toList());

        Set<UUID> canHoi = new LinkedHashSet<>();
        rows.forEach(honour -> canHoi.add(honour.personId()));
        canHoi.addAll(nhanKhauCuaNguoiDuyet.values());
        PersonLens lens = persons.load(canHoi);

        List<HonourView> views = new ArrayList<>(rows.size());
        for (Honour honour : rows) {
            if (disclosable(honour, caller, lens)) {
                views.add(toView(honour, caller, lens, nhanKhauCuaNguoiDuyet));
            }
        }
        return views;
    }

    private HonourView view(Honour honour, MemberScopeView caller) {
        Map<UUID, UUID> nhanKhauCuaNguoiDuyet = reviewers.personIdsOf(
                java.util.Collections.singletonList(honour.reviewedBy()));
        Set<UUID> canHoi = new LinkedHashSet<>();
        canHoi.add(honour.personId());
        canHoi.addAll(nhanKhauCuaNguoiDuyet.values());
        return toView(honour, caller, persons.load(canHoi), nhanKhauCuaNguoiDuyet);
    }

    /**
     * Ten chu the VA ten nguoi duyet deu di qua dung mot bo loc.
     *
     * <p>Checklist §2 doi ghi ro "ai duyet va luc nao"; cot {@code reviewed_by} la khoa tai khoan
     * nen phai di duong {@code app_user → person → PersonDisclosureService}. Doc thang bang
     * {@code person} cho nhanh la dung cai bay ma {@code RelationshipSummaryLoader} da chan.
     * Khong tra duoc ten thi {@code null}, KHONG bia mot chuoi thay the.</p>
     */
    private HonourView toView(Honour honour, MemberScopeView caller, PersonLens lens,
                              Map<UUID, UUID> nhanKhauCuaNguoiDuyet) {
        BranchPath path = access.branchOfPerson(honour.personId());
        UUID branchId = access.branchIdOfPerson(honour.personId());
        boolean canReview = honour.status() == ContentStatus.PENDING
                && !caller.appUserId().equals(honour.createdBy())
                && access.canReview(caller, path);
        String tenNguoiDuyet = lens.displayName(nhanKhauCuaNguoiDuyet.get(honour.reviewedBy()));
        return HonourView.of(honour, lens.displayName(honour.personId()), tenNguoiDuyet,
                branchId, access.branchName(branchId), canReview);
    }

    private void requireVersion(Honour honour, Long expectedVersion) {
        if (expectedVersion == null) {
            throw new ContentPreconditionException(
                    "Thieu phien ban ky vong; hay lay ETag tu lan doc gan nhat truoc khi sua");
        }
        if (honour.version() != expectedVersion) {
            throw new ContentConflictException(ContentProblemCodes.OPTIMISTIC_LOCK_CONFLICT,
                    "Vinh danh da bi nguoi khac sua (phien ban " + honour.version()
                            + ", ban gui " + expectedVersion + "); hay tai lai roi thu lai");
        }
    }

    private DomainException selfReviewOrClosed(IllegalStateException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("tu duyet")) {
            return new ForbiddenException(ContentProblemCodes.SELF_REVIEW_FORBIDDEN, message);
        }
        return new ContentConflictException(ContentProblemCodes.CONTENT_CLOSED, message, ex);
    }

    private static List<String> changedFields(UpdateHonourCommand command) {
        List<String> fields = new ArrayList<>();
        if (command.kind() != null) {
            fields.add("kind");
        }
        if (command.title() != null) {
            fields.add("title");
        }
        if (command.year() != null) {
            fields.add("year");
        }
        if (command.issuer() != null) {
            fields.add("issuer");
        }
        if (command.description() != null) {
            fields.add("description");
        }
        return fields;
    }

    private static int clamp(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
