package vn.giapha.membership.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.membership.application.command.ReviewChangeRequestCommand;
import vn.giapha.membership.application.command.SubmitChangeRequestCommand;
import vn.giapha.membership.domain.ChangeRequest;
import vn.giapha.membership.domain.ChangeRequestStatus;
import vn.giapha.membership.domain.MemberScope;
import vn.giapha.membership.domain.event.ChangeRequestApprovedEvent;
import vn.giapha.membership.domain.event.ChangeRequestRejectedEvent;
import vn.giapha.membership.domain.event.CorrectionPayload;
import vn.giapha.membership.domain.port.BranchLookupPort;
import vn.giapha.membership.domain.port.ChangeRequestRepository;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.BranchPath;

/**
 * Luồng đề nghị – duyệt đính chính dữ liệu phả hệ (FR luồng W6 §2).
 *
 * <h2>Thành viên đề nghị, Trưởng chi quyết</h2>
 * Thành viên biết rõ nhất về nhánh nhà mình nhưng không được ghi thẳng vào cây. Đề nghị + duyệt giữ
 * được cả hai: kiến thức của người trong cuộc và trách nhiệm của người có thẩm quyền.
 *
 * <h2>Quyền duyệt soi ltree, không chỉ soi vai</h2>
 * Đây là chỗ dễ sai nhất của cả context. Chuỗi kiểm tra là:
 * <ol>
 *   <li>xác định <b>chi đích</b> của yêu cầu — lấy từ {@code target_branch_id}, không có thì suy từ
 *       chi chính của nhân khẩu bị ảnh hưởng;</li>
 *   <li>phân giải chi đó thành một {@link BranchPath} thật;</li>
 *   <li>giao cho {@link BranchScopeGuard#requireReviewAccess} so path ấy với các chi được giao,
 *       bằng ngữ nghĩa {@code @>} của {@code ltree}.</li>
 * </ol>
 * Một Trưởng chi của {@code goc.chi_giap} duyệt được {@code goc.chi_giap.nganh_truong} nhưng
 * <b>không</b> duyệt được {@code goc.chi_at} — dù token của cả hai trường hợp giống hệt nhau.
 *
 * <h2>Chi đích không phân giải được thì từ chối, không mở</h2>
 * Yêu cầu không xác định được chi ({@code target_branch_id} trống và nhân khẩu chưa gắn chi) chỉ vai
 * toàn dòng họ mới duyệt được. Đây là lựa chọn có chủ ý: dữ liệu thiếu phải làm quyền <b>hẹp lại</b>,
 * không bao giờ được nới ra.
 */
@Service
public class ChangeRequestService {

    private static final Logger log = LoggerFactory.getLogger(ChangeRequestService.class);

    private static final String ENTITY = "ChangeRequest";

    private final ChangeRequestRepository requests;
    private final MemberScopeService scopes;
    private final BranchScopeGuard guard;
    private final BranchLookupPort branches;
    private final AuditTrailService audit;
    private final ApplicationEventPublisher events;

    public ChangeRequestService(ChangeRequestRepository requests, MemberScopeService scopes,
                                BranchScopeGuard guard, BranchLookupPort branches,
                                AuditTrailService audit, ApplicationEventPublisher events) {
        this.requests = requests;
        this.scopes = scopes;
        this.guard = guard;
        this.branches = branches;
        this.audit = audit;
        this.events = events;
    }

    /**
     * Gửi đề nghị. Mọi thành viên đã có tài khoản đều gửi được — <b>không</b> kiểm phạm vi ở bước
     * này.
     *
     * <p>Cố ý như vậy: một người con gái đã lấy chồng xa vẫn phải báo được rằng ngày mất của cụ
     * ghi sai, dù chi của cụ không phải chi cô ấy đang sinh hoạt. Cửa kiểm <i>phạm vi</i> là ở bước
     * duyệt, và ở đó nó chặt.</p>
     *
     * <p><b>Cửa kiểm nội dung thì ngược lại — nó ở ngay đây.</b> Payload phải khớp hợp đồng đóng
     * {@link CorrectionPayload}. Để đến lúc duyệt mới phát hiện sai khoá nghĩa là người gửi biết
     * mình gõ nhầm sau <i>một tuần</i>, khi đã quên mình gõ gì; còn Trưởng chi thì nhận một đề nghị
     * không dùng được và không có cách nào sửa hộ.</p>
     */
    @Transactional
    public ChangeRequestView submit(SubmitChangeRequestCommand command) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);

        UUID targetBranchId = command.targetBranchId();
        if (targetBranchId == null && command.personId() != null) {
            targetBranchId = resolveBranchIdOfPerson(command.personId());
        }

        Map<String, Object> payload = validatedPayload(command);

        ChangeRequest request = ChangeRequest.submit(UUID.randomUUID(), command.type(),
                command.personId(), targetBranchId, payload, command.reason(),
                caller.appUserId());
        ChangeRequest saved = requests.save(request);

        audit.record(ENTITY, saved.id().toString(), AuditAction.CREATE,
                null, saved.auditSnapshot(), saved.auditSnapshot().keySet().stream().toList(),
                command.reason());
        log.info("Yeu cau dinh chinh {} ({}) duoc gui boi app_user {}",
                saved.id(), saved.type(), caller.appUserId());
        return ChangeRequestView.from(saved);
    }

    /**
     * Duyệt hoặc từ chối. Kiểm quyền hai chiều nằm trọn trong {@link #requireReviewer}.
     *
     * <h2>Duyệt và áp dụng nằm trong CÙNG một transaction</h2>
     * {@link ChangeRequestApprovedEvent} được phát bằng {@code publishEvent} thường, nên bên nhận
     * ({@code genealogy.application.ChangeRequestApplier}) chạy <b>đồng bộ, trong ngăn xếp lời gọi
     * này</b>, tức là trong chính transaction đang mở. Áp dụng hỏng — lệch phiên bản, nhân khẩu đã
     * bị xoá mềm, trùng kỵ húy — thì ngoại lệ lan ngược lên đây và cả trạng thái {@code APPROVED}
     * lẫn dòng audit {@code APPROVE} cùng bị rollback. Yêu cầu ở lại {@code PENDING}.
     *
     * <p><b>Cái giá:</b> Trưởng chi nhận lỗi thay vì màn hình "đã duyệt", và phải xử lý xung đột.
     * Đổi lại, hệ thống không bao giờ rơi vào trạng thái mà bộ này sinh ra để chấm dứt: yêu cầu ghi
     * {@code APPROVED} mà gia phả không hề đổi. Giữa "người duyệt phải bấm lại" và "cuốn gia phả
     * nói dối", chọn cái thứ nhất.</p>
     *
     * <p>Thông báo cho người gửi thì <b>không</b> được nằm trong transaction này — bên
     * {@code notification} phải nghe bằng {@code @TransactionalEventListener(AFTER_COMMIT)}, nếu
     * không một cú gửi Zalo hỏng sẽ kéo đổ cả việc duyệt.</p>
     */
    @Transactional
    public ChangeRequestView review(ReviewChangeRequestCommand command) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);

        ChangeRequest request = load(command.changeRequestId());
        requireOpen(request);
        requireReviewer(caller, request);

        Instant now = Instant.now();
        try {
            if (command.approve()) {
                request.approve(caller.appUserId(), command.note(), now);
            } else {
                request.reject(caller.appUserId(), command.note(), now);
            }
        } catch (IllegalStateException ex) {
            throw new ForbiddenException(MembershipProblemCodes.SELF_REVIEW_FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED, ex.getMessage(), ex);
        }

        ChangeRequest saved = requests.save(request);
        audit.record(ENTITY, saved.id().toString(),
                command.approve() ? AuditAction.APPROVE : AuditAction.REJECT,
                null, saved.auditSnapshot(), List.of("status", "reviewerId", "reviewedAt"),
                command.note());

        if (command.approve()) {
            events.publishEvent(new ChangeRequestApprovedEvent(saved.id(), saved.type().name(),
                    saved.personId(), saved.targetBranchId(), saved.payload(),
                    caller.appUserId(), saved.requestedBy()));
        } else {
            events.publishEvent(new ChangeRequestRejectedEvent(saved.id(), saved.requestedBy(),
                    caller.appUserId(), command.note()));
        }
        log.info("Yeu cau dinh chinh {} -> {} boi app_user {}",
                saved.id(), saved.status(), caller.appUserId());
        return ChangeRequestView.from(saved);
    }

    /** Người gửi tự rút lại đề nghị của mình. */
    @Transactional
    public ChangeRequestView cancel(UUID changeRequestId) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);

        ChangeRequest request = load(changeRequestId);
        try {
            request.cancel(caller.appUserId(), Instant.now());
        } catch (IllegalStateException ex) {
            throw new ForbiddenException(MembershipProblemCodes.FORBIDDEN, ex.getMessage());
        }
        ChangeRequest saved = requests.save(request);
        audit.record(ENTITY, saved.id().toString(), AuditAction.UPDATE,
                null, saved.auditSnapshot(), List.of("status"), "Nguoi gui rut lai");
        return ChangeRequestView.from(saved);
    }

    /** Đề nghị của chính người gọi. {@code payload} giữ nguyên — đó là dữ liệu họ tự gửi lên. */
    @Transactional(readOnly = true)
    public List<ChangeRequestView> mine(int page, int size) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);
        int limit = clamp(size);
        return requests.byRequester(caller.appUserId(), limit, Math.max(page, 0) * limit).stream()
                .map(ChangeRequestView::from)
                .toList();
    }

    /**
     * Hàng đợi chờ duyệt <b>trong phạm vi được giao</b>.
     *
     * <p>Trưởng chi không có phân công nào sẽ nhận danh sách rỗng — đúng ý. Danh sách rỗng ở đây
     * mang nghĩa "không có phạm vi nào", tuyệt đối không được hiểu ngược thành "xem được tất".</p>
     */
    @Transactional(readOnly = true)
    public List<ChangeRequestView> pendingForReview(int page, int size) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);
        if (!caller.role().canReview()) {
            throw new ForbiddenException(MembershipProblemCodes.FORBIDDEN,
                    "Chi Truong chi, Hoi dong Toc bieu hoac Quan tri he thong xem duoc hang doi duyet");
        }
        int limit = clamp(size);
        return requests.pendingInScope(caller.managedBranches(), caller.isClanWide(),
                        limit, Math.max(page, 0) * limit).stream()
                .map(ChangeRequestView::from)
                .toList();
    }

    /** Số yêu cầu đang chờ trong phạm vi — cho badge trên giao diện. */
    @Transactional(readOnly = true)
    public long countPendingForReview() {
        MemberScope caller = scopes.currentMemberScope();
        if (caller.appUserId() == null || !caller.role().canReview()) {
            return 0L;
        }
        return requests.countPendingInScope(caller.managedBranches(), caller.isClanWide());
    }

    /**
     * Một yêu cầu cụ thể. {@code payload} chỉ hiện với người gửi và người có quyền duyệt nó — xem
     * {@link ChangeRequestView#redacted()}.
     */
    @Transactional(readOnly = true)
    public ChangeRequestView byId(UUID changeRequestId) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);
        ChangeRequest request = load(changeRequestId);

        if (caller.appUserId().equals(request.requestedBy())) {
            return ChangeRequestView.from(request);
        }
        BranchPath target = targetPathOf(request);
        if (caller.canReview(target)) {
            return ChangeRequestView.from(request);
        }
        if (caller.isClanWide()) {
            return ChangeRequestView.from(request);
        }
        // Khong du quyen xem noi dung: van cho biet yeu cau ton tai, nhung khong lo payload.
        return ChangeRequestView.from(request).redacted();
    }

    @Transactional(readOnly = true)
    public List<ChangeRequestView> byPerson(UUID personId, ChangeRequestStatus status) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);
        BranchPath target = branches.branchOfPerson(personId).orElse(null);
        guard.requireReviewAccess(caller, target);
        return requests.byPerson(personId, status).stream()
                .map(ChangeRequestView::from)
                .toList();
    }

    // -------------------------------------------------------------------------------------
    // Nội bộ
    // -------------------------------------------------------------------------------------

    /**
     * Cửa kiểm quyền duyệt. Đây là phương thức mà mọi lối duyệt <b>bắt buộc</b> đi qua.
     *
     * <p>Chi đích không phân giải được thì {@code target} là {@code null}, và
     * {@link BranchScopeGuard#requireWriteAccess} chỉ cho vai toàn dòng họ đi tiếp.</p>
     */
    private void requireReviewer(MemberScope caller, ChangeRequest request) {
        BranchPath target = targetPathOf(request);
        guard.requireReviewAccess(caller, target);
    }

    /**
     * Chi đích của một yêu cầu, dạng {@code ltree} path.
     *
     * <p>Ưu tiên {@code target_branch_id} đã chốt lúc gửi. Yêu cầu cũ không có thì suy lại từ chi
     * chính của nhân khẩu — nhưng lưu ý đây là <i>chi hiện tại</i>: nếu nhân khẩu đã bị chuyển chi
     * sau khi yêu cầu được gửi, người duyệt sẽ là Trưởng chi mới. Đó là hành vi đúng: người chịu
     * trách nhiệm về một nhân khẩu là người đang quản chi của nhân khẩu đó.</p>
     */
    private BranchPath targetPathOf(ChangeRequest request) {
        if (request.targetBranchId() != null) {
            return branches.pathOfBranch(request.targetBranchId()).orElse(null);
        }
        if (request.personId() != null) {
            return branches.branchOfPerson(request.personId()).orElse(null);
        }
        return null;
    }

    /**
     * Kiểm hợp đồng payload rồi <b>đóng dấu mốc phiên bản</b>.
     *
     * <p>Hai bước, theo đúng thứ tự:</p>
     * <ol>
     *   <li>{@link CorrectionPayload#violations} soi khoá và kiểu giá trị. Ở bước này
     *       {@code _baseVersion} chưa bắt buộc — client cũ chưa gửi nó, và backend còn kịp tự đóng
     *       dấu.</li>
     *   <li>Thiếu {@code _baseVersion} thì đọc {@code person.version} <b>ngay lúc này</b> và ghi
     *       vào payload. Mốc chụp lúc gửi vẫn chặn được ghi đè mù: mọi thay đổi xảy ra trong lúc
     *       chờ duyệt đều làm {@code version} nhích lên và đề nghị sẽ bị từ chối áp dụng.</li>
     * </ol>
     *
     * <p>Giá trị do client gửi <b>luôn thắng</b> giá trị backend tự đọc: nó là phiên bản người dùng
     * thật sự nhìn thấy trên màn hình, còn con số backend đọc chỉ là xấp xỉ.</p>
     *
     * <p>Không đọc được {@code person.version} (nhân khẩu không tồn tại) mà client cũng không gửi
     * thì <b>từ chối</b>. Bỏ trống mốc phiên bản là mở lại đúng lỗ hổng ghi đè mù.</p>
     */
    private Map<String, Object> validatedPayload(SubmitChangeRequestCommand command) {
        String type = command.type().name();
        Map<String, Object> payload = command.payload();

        List<String> problems = CorrectionPayload.violations(type, payload, false);
        if (!problems.isEmpty()) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Noi dung de nghi dinh chinh khong hop le: " + String.join("; ", problems));
        }
        if (!CorrectionPayload.isApplicable(type)
                || CorrectionPayload.baseVersion(payload) != null) {
            return payload;
        }

        Long current = branches.versionOfPerson(command.personId()).orElse(null);
        if (current == null) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Khong doc duoc phien ban hien tai cua nhan khau " + command.personId()
                            + "; hay gui kem " + CorrectionPayload.BASE_VERSION_KEY
                            + " lay tu ETag cua lan GET ho so gan nhat");
        }
        log.debug("Dong dau {}={} cho de nghi tren nhan khau {}",
                CorrectionPayload.BASE_VERSION_KEY, current, command.personId());
        return CorrectionPayload.withBaseVersion(payload, current);
    }

    /**
     * Chốt chi đích ngay lúc gửi.
     *
     * <p>Lưu lại khoá chi thay vì để trống giúp hàng đợi duyệt lọc được bằng một câu SQL
     * {@code ltree} duy nhất, thay vì phải nối sang {@code person} cho từng dòng.</p>
     */
    private UUID resolveBranchIdOfPerson(UUID personId) {
        return branches.branchIdOfPerson(personId).orElse(null);
    }

    private ChangeRequest load(UUID id) {
        return requests.byId(id).orElseThrow(() -> new NotFoundException(
                MembershipProblemCodes.NOT_FOUND,
                "Khong tim thay yeu cau dinh chinh voi dinh danh " + id));
    }

    private void requireOpen(ChangeRequest request) {
        if (request.status().isFinal()) {
            throw new DomainException(MembershipProblemCodes.CHANGE_REQUEST_CLOSED,
                    "Yeu cau dinh chinh da o trang thai " + request.status() + ", khong xu ly lai duoc");
        }
    }

    private static int clamp(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }
}
