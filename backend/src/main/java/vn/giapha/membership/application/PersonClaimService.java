package vn.giapha.membership.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.membership.application.command.ReviewPersonClaimCommand;
import vn.giapha.membership.application.command.SubmitNewPersonClaimCommand;
import vn.giapha.membership.application.command.SubmitPersonClaimCommand;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.BranchSummary;
import vn.giapha.membership.domain.ClaimDuplicateSuspect;
import vn.giapha.membership.domain.Invitee;
import vn.giapha.membership.domain.MemberScope;
import vn.giapha.membership.domain.PersonClaim;
import vn.giapha.membership.domain.PersonClaimKind;
import vn.giapha.membership.domain.PersonClaimStatus;
import vn.giapha.membership.domain.RelativeKind;
import vn.giapha.membership.domain.port.AppUserRepository;
import vn.giapha.membership.domain.port.BranchLookupPort;
import vn.giapha.membership.domain.port.InviteeLookupPort;
import vn.giapha.membership.domain.port.PersonClaimRepository;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.BranchPath;
import vn.giapha.shared.vo.Gender;

/**
 * <b>Tự nhận mình trong phả</b> — gửi đơn · rút đơn · hàng chờ của Trưởng chi · duyệt và từ chối.
 *
 * <p>Đây là bước 4 và 5 của luồng đã chốt ở design 07 §1.1: người đăng ký bằng mã dòng họ
 * ({@link ClanInviteService}) xem được phả đồ ngay, tự nhận mình là ai, rồi <b>Trưởng chi hoặc vai
 * cao hơn duyệt</b> để thành thành viên đầy đủ.</p>
 *
 * <h2>Hai loại đơn, một máy trạng thái</h2>
 * {@link PersonClaimKind#EXISTING} trỏ vào một nhân khẩu đã có; duyệt thì chỉ <i>gắn</i> tài khoản.
 * {@link PersonClaimKind#NEW_PERSON} là lối "Tôi chưa có trong phả"; duyệt thì <b>tạo</b> nhân khẩu
 * rồi mới gắn.
 *
 * <h2>Năm ràng buộc của lối "Tôi chưa có trong phả" (§1.5), và chỗ đứng của từng ràng buộc</h2>
 * Lối này khác mọi lối khác ở một điểm: nó cho <b>một người chưa được duyệt</b> khởi tạo việc thêm
 * người vào gia phả. Làm ẩu thì ai cầm mã mời cũng bơm người lạ vào phả được.
 * <ol>
 *   <li><b>Không tạo nhân khẩu lúc gửi đơn — chỉ tạo khi duyệt.</b> Xoá mềm là luật tuyệt đối của
 *       dự án, nên tạo trước rồi xoá sau để lại một <i>node ma</i> cho mỗi đơn bị từ chối. Chỗ
 *       đứng: {@link #submitNew} không gọi {@link ClaimPersonWriterPort}; chỉ {@link #review} gọi.
 *       Ràng buộc {@code ck_person_claim_shape} và {@code ck_person_claim_created} của V16 canh
 *       điều đó ở CSDL, và {@code PersonClaim} canh ở domain.</li>
 *   <li><b>Bắt buộc chỉ ra người thân đã có trong phả.</b> Không có nó thì nhân khẩu mới thành node
 *       mồ côi: không tính được đời, không tra được danh xưng. Chỗ đứng: {@link #submitNew} bắt
 *       buộc {@code relativePersonId}, và {@code PersonClaim#chuaCoTrongPha} từ chối thiếu.</li>
 *   <li><b>Chạy qua bộ dò trùng trước khi đưa cho Trưởng chi.</b> Người khai rất có thể <i>đã</i>
 *       có trong phả dưới một tên khác. Chỗ đứng: {@link #submitNew} gọi {@link ClaimScreeningPort}
 *       và <b>lưu ảnh chụp</b> vào đơn.</li>
 *   <li><b>Ghi nhân khẩu và ghi quan hệ trong cùng một transaction.</b> Chỗ đứng:
 *       {@link #review} là {@code @Transactional} và {@link ClaimPersonWriterPort} không tự mở
 *       transaction, nên nó <i>nhập</i> vào transaction ấy.</li>
 *   <li><b>Người mới là người còn sống nên mọi nhóm riêng tư mặc định KÍN.</b> Chỗ đứng: không có
 *       chỗ nào — nó rơi ra từ mô hình V8. Đặt cờ ở đây là dựng một bản luật thứ hai.</li>
 * </ol>
 *
 * <h2>AI DUYỆT: Trưởng chi của chi mà đơn trỏ tới</h2>
 * Với đơn {@code EXISTING} là chi của nhân khẩu được nhận; với {@code NEW_PERSON} là chi của
 * <b>người thân được chỉ ra</b>, vì người mới chưa thuộc chi nào. Trưởng chi Ất không duyệt được
 * đơn trỏ vào người chi Bính. Phép so là {@code ltree} và nó chạy ở
 * {@link BranchScopeGuard#requireReviewAccess}; danh sách hàng chờ còn được lọc <b>ngay trong
 * SQL</b>, nên đơn của chi khác không bao giờ đi vào bộ nhớ tiến trình.
 *
 * <h2>Số điện thoại trên đơn đi về đâu</h2>
 * Design 07 §1.4 chốt "ghi vào hồ sơ nhân khẩu khi duyệt", và mô hình riêng tư V8 nhận nó đúng chỗ:
 * số điện thoại thuộc nhóm {@code contact}, mặc định <b>kín</b>, chính chủ tự mở. Nên
 * số ấy <b>vừa ở lại trên đơn vừa chảy vào hồ sơ</b>. Trên đơn ({@code person_claim.phone}) để
 * Trưởng chi đọc mà gọi kiểm chứng trước khi duyệt; vào hồ sơ ({@code person.contact}) khi duyệt,
 * qua cổng {@link ClaimPersonWriterPort#datSoDienThoaiNeuTrong} và <b>chỉ khi</b> hồ sơ chưa có số
 * nào — xem {@link #ghiSoDienThoaiVaoHoSo}. Không có đường ghi riêng nào ở context này: viết một
 * câu {@code UPDATE} ở đây là đúng thứ mà ràng buộc 4 cấm, và là đi vòng qua bộ lọc nhóm trường V8.
 */
@Service
public class PersonClaimService {

    private static final Logger log = LoggerFactory.getLogger(PersonClaimService.class);

    private static final String ENTITY = "PersonClaim";

    /**
     * Thông điệp <b>chung chung</b> cho mọi lý do khiến một nhân khẩu không nhận được.
     *
     * <h2>Vì sao không nói thẳng "người này đã có tài khoản"</h2>
     * Design 07 §1.4. Nói thẳng thì màn này thành <b>công cụ dò xem ai đã vào hệ thống</b>: gửi thử
     * lần lượt từng ô trên phả đồ và đọc mã lỗi là biết ai đã đăng ký, ai chưa. Với một dòng họ
     * 1.500 người thì đó là một danh sách có giá trị thật với người muốn mạo danh — họ sẽ nhắm vào
     * đúng những người <i>chưa</i> có tài khoản.
     *
     * <p>Cái giá phải trả là một người gửi đơn ngay tình nhận một câu mơ hồ. Chấp nhận được, vì
     * lối đi tiếp của họ vẫn đúng và vẫn khả thi: gọi Trưởng chi.</p>
     */
    private static final String KHONG_NHAN_DUOC =
            "Khong gui don cho nguoi nay duoc. Hay chon mot o khac, hoac hoi Truong chi.";

    private final PersonClaimRepository claims;
    private final AppUserRepository appUsers;
    private final InviteeLookupPort persons;
    private final BranchLookupPort branches;
    private final MemberScopeService scopes;
    private final BranchScopeGuard guard;
    private final InvitationLinker linker;
    private final ClaimScreeningPort screening;
    private final ClaimPersonWriterPort personWriter;
    private final AuditTrailService audit;
    private final int maxRejected;
    private final int maxAttempts;

    @SuppressWarnings("java:S107")
    public PersonClaimService(PersonClaimRepository claims, AppUserRepository appUsers,
                              InviteeLookupPort persons, BranchLookupPort branches,
                              MemberScopeService scopes, BranchScopeGuard guard,
                              InvitationLinker linker, ClaimScreeningPort screening,
                              ClaimPersonWriterPort personWriter, AuditTrailService audit,
                              @Value("${giapha.membership.claim.max-rejected:3}") int maxRejected,
                              @Value("${giapha.membership.claim.max-attempts:10}") int maxAttempts) {
        this.claims = claims;
        this.appUsers = appUsers;
        this.persons = persons;
        this.branches = branches;
        this.scopes = scopes;
        this.guard = guard;
        this.linker = linker;
        this.screening = screening;
        this.personWriter = personWriter;
        this.audit = audit;
        this.maxRejected = maxRejected;
        this.maxAttempts = maxAttempts;
    }

    // -------------------------------------------------------------------------------------
    // Gửi đơn
    // -------------------------------------------------------------------------------------

    /**
     * "Tôi là người này trong phả".
     *
     * <h2>Ba ca biên được chặn <b>lúc gửi</b>, không để dành tới lúc duyệt</h2>
     * <ul>
     *   <li><b>Nhận nhầm người đã khuất</b> — chặn cứng, và nói thẳng. Người đã khuất vốn là dữ
     *       liệu công khai nên nói thẳng ở bước này không lộ thêm gì, và câu trả lời mơ hồ ở đây
     *       chỉ làm người dùng bấm lại đúng ô ấy.</li>
     *   <li><b>Nhận nhầm người đã có tài khoản</b> — chặn, nhưng {@link #KHONG_NHAN_DUOC} nói
     *       <i>chung chung</i>.</li>
     *   <li><b>Nhân khẩu đã xoá mềm</b> — cùng câu chung chung ấy: nó vốn không hiện trên phả đồ,
     *       nên một đơn trỏ vào nó là một lần đoán khoá, và xác nhận "có tồn tại nhưng đã xoá" là
     *       trả lời đúng câu hỏi người đoán đang hỏi.</li>
     * </ul>
     * Cả ba đều phát hiện được ngay khi bấm, và người gửi cần biết ngay — chứ không phải sau một
     * tuần chờ rồi nhận một lời từ chối.
     */
    @Transactional
    public PersonClaimView submitExisting(SubmitPersonClaimCommand command) {
        if (command == null || command.personId() == null) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Phai chon mot nhan khau trong pha");
        }
        MemberScope caller = requireUnlinkedMember();

        Invitee target = persons.byId(command.personId())
                .orElseThrow(() -> new NotFoundException(MembershipProblemCodes.NOT_FOUND,
                        "Khong tim thay nhan khau " + command.personId()));

        if (!target.alive()) {
            // CHAN CUNG. Khong ai can mot tai khoan de "la" mot nguoi da khuat, va ho so nguoi da
            // khuat von cong khai nen noi thang khong lo them gi.
            throw new DomainException(MembershipProblemCodes.CLAIM_TARGET_UNAVAILABLE,
                    "Khong the nhan minh la mot nguoi da khuat");
        }
        if (target.deleted() || appUsers.byPersonId(command.personId()).isPresent()) {
            // MOT CAU TRA LOI CHUNG CHO HAI LY DO — xem javadoc KHONG_NHAN_DUOC.
            log.info("Tu choi don nhan minh cua app_user {} vao person {} (da xoa: {})",
                    caller.appUserId(), command.personId(), target.deleted());
            throw new DomainException(MembershipProblemCodes.CLAIM_TARGET_UNAVAILABLE,
                    KHONG_NHAN_DUOC);
        }

        PersonClaim claim = PersonClaim.nhanMinh(UUID.randomUUID(), caller.appUserId(),
                command.personId(), target.branchId(), command.phone(), command.introduction());
        return luuVaGhiNhatKy(claim, caller);
    }

    /**
     * "Tôi chưa có trong phả" — <b>lối ghi vào phả</b>, không phải một biểu mẫu liên hệ.
     *
     * <p>Đơn này <b>không tạo nhân khẩu nào</b> (ràng buộc 1). Nó chạy qua bộ dò trùng (ràng buộc
     * 3) và lưu ảnh chụp kết quả, để Trưởng chi thấy ngay <i>"có thể đây là người này"</i> thay vì
     * tạo ra một bản trùng — gộp nhầm hai người trong gia phả là loại lỗi rất khó gỡ, vì cả hai
     * nhánh con cháu đều đã treo vào node sai.</p>
     *
     * <p>Người thân được chỉ ra (ràng buộc 2) <b>được phép đã khuất</b> — "bố tôi là cụ X, cụ mất
     * năm 2019" là ca thường gặp nhất của cả lối này. Chỉ nhân khẩu đã <i>xoá mềm</i> mới bị từ
     * chối: nối một người sống vào một node đã bị gỡ khỏi phả là tạo ra đúng cái node mồ côi mà
     * ràng buộc 2 sinh ra để tránh.</p>
     */
    @Transactional
    public PersonClaimView submitNew(SubmitNewPersonClaimCommand command) {
        if (command == null || command.fullName() == null || command.fullName().isBlank()) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Phai khai ho ten cua nguoi can them vao pha");
        }
        if (command.relativePersonId() == null || command.relativeKind() == null) {
            // RANG BUOC 2. Khong co nguoi than thi nhan khau moi thanh node mo coi: khong gan vao
            // cay, khong tinh duoc doi, khong tra duoc danh xung — va khong biet ai duyet.
            throw new DomainException(MembershipProblemCodes.CLAIM_RELATIVE_UNUSABLE,
                    "Phai chi ra mot nguoi than da co trong pha (bo, me, hoac vo/chong)");
        }
        MemberScope caller = requireUnlinkedMember();

        Invitee relative = persons.byId(command.relativePersonId())
                .orElseThrow(() -> new NotFoundException(MembershipProblemCodes.NOT_FOUND,
                        "Khong tim thay nguoi than " + command.relativePersonId() + " trong pha"));
        if (relative.deleted()) {
            throw new DomainException(MembershipProblemCodes.CLAIM_RELATIVE_UNUSABLE,
                    KHONG_NHAN_DUOC);
        }
        if (relative.branchId() == null) {
            // Nguoi than chua gan chi thi khong xac dinh duoc AI DUYET, va nhan khau moi se khong
            // ke thua duoc chi nao. Noi that ly do: nguoi dung khong sua duoc, nhung Truong chi thi
            // sua duoc — va cau tra loi mo ho o day se khien ho khong bao giờ biet can sua gi.
            throw new DomainException(MembershipProblemCodes.CLAIM_RELATIVE_UNUSABLE,
                    "Nguoi than duoc chi ra chua duoc gan vao chi nao trong pha, "
                            + "nen chua xac dinh duoc ai duyet don. Hay bao Truong chi.");
        }

        UUID claimId = UUID.randomUUID();
        // RANG BUOC 3 — do trung TRUOC khi don toi tay Truong chi. Dung lai PersonScreeningService
        // qua cong ClaimScreeningPort; tuyet doi khong viet bo cham diem thu hai.
        List<ClaimDuplicateSuspect> suspects = screening.scanDuplicates(claimId.toString(),
                        command.fullName(), command.birthYear(), command.gender(),
                        relative.branchId()).stream()
                .map(PersonClaimService::toDomain)
                .toList();

        PersonClaim claim = PersonClaim.chuaCoTrongPha(claimId, caller.appUserId(),
                command.fullName().trim(), command.birthYear(), command.gender(),
                command.relativePersonId(), command.relativeKind(), relative.branchId(),
                command.phone(), command.introduction(), suspects);

        if (!suspects.isEmpty()) {
            log.info("Don chua co trong pha {} co {} nghi ngo trung nguoi — Truong chi se thay",
                    claimId, suspects.size());
        }
        return luuVaGhiNhatKy(claim, caller);
    }

    // -------------------------------------------------------------------------------------
    // Đọc
    // -------------------------------------------------------------------------------------

    /**
     * Đơn của chính người gọi — màn "đang chờ duyệt", <b>kèm hạn mức gửi lại</b>.
     *
     * <p>Hạn mức là thứ duy nhất ở đây không thuộc về một đơn nào, và nó là một giá trị <b>cấu hình
     * của máy chủ</b> ({@code giapha.membership.claim.max-rejected}). Client đoán con số ấy sẽ đúng
     * hôm nay và âm thầm sai ngày Hội đồng đổi cấu hình. Xem {@link MyPersonClaims.ClaimQuota}.</p>
     */
    @Transactional(readOnly = true)
    public MyPersonClaims mine(int page, int size) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);
        int limit = clamp(size);
        List<PersonClaimView> views =
                claims.byRequester(caller.appUserId(), limit, Math.max(page, 0) * limit).stream()
                        .map(this::toRequesterView)
                        .toList();
        return new MyPersonClaims(views, MyPersonClaims.ClaimQuota.of(
                claims.rejectedCountOf(caller.appUserId()), maxRejected));
    }

    /**
     * Hàng chờ duyệt — <b>đã lọc theo {@code ltree} ngay trong SQL</b>.
     *
     * <p>Lọc ở CSDL chứ không lọc sau khi đọc: đơn của chi khác không bao giờ đi vào bộ nhớ tiến
     * trình, nên không có chỗ nào để quên lọc. Và đơn mang số điện thoại của một người đang sống,
     * nên "quên lọc" ở đây không phải một lỗi hiển thị mà là một lần lộ dữ liệu cá nhân.</p>
     *
     * <p>Hai đơn cùng trỏ một nhân khẩu <b>đều xuất hiện</b>, không ưu tiên đơn gửi trước: trùng
     * tên trong dòng họ là chuyện thường, và người gửi trước chưa chắc là người đúng (§1.4).</p>
     */
    @Transactional(readOnly = true)
    public List<PersonClaimView> pendingForReview(int page, int size) {
        MemberScope caller = requireReviewer();
        int limit = clamp(size);
        return claims.pendingInScope(caller.managedBranches(), caller.isClanWide(), limit,
                        Math.max(page, 0) * limit).stream()
                .map(this::toReviewerView)
                .toList();
    }

    /**
     * Số đơn đang chờ trong phạm vi — huy hiệu trên thanh điều hướng.
     *
     * <h2>Trả {@code 0} cho người không có quyền duyệt, KHÔNG trả 403</h2>
     * Khác {@link #pendingForReview}, và cố ý. Huy hiệu này nằm trên thanh đầu trang của <i>mọi</i>
     * màn hình: nếu nó trả 403 thì giao diện phải biết vai của người dùng <b>trước khi</b> gọi, tức
     * phải chép luật phân quyền sang client — đúng thứ mà {@code MemberScopeService} sinh ra để
     * tránh. Và một huy hiệu bật lên dải đỏ là một lỗi trông như sự cố hệ thống.
     *
     * <p>Trả {@code 0} <b>không lộ gì</b>: con số ấy vốn là "có bao nhiêu đơn <i>bạn</i> duyệt
     * được", nên với người không duyệt được đơn nào thì 0 là câu trả lời <i>đúng</i>, không phải
     * một lời từ chối bị giấu đi. Phép lọc phạm vi vẫn nguyên — {@code managedBranches()} rỗng cho
     * ra 0 vì repository từ chối hiểu "không có phạm vi nào" thành "thấy tất".</p>
     */
    @Transactional(readOnly = true)
    public long countPendingForReview() {
        MemberScope caller = scopes.currentMemberScope();
        if (caller.isGuest() || caller.appUserId() == null || !caller.role().canReview()) {
            return 0L;
        }
        return claims.countPendingInScope(caller.managedBranches(), caller.isClanWide());
    }

    /**
     * Một đơn cụ thể — chỉ <b>người gửi</b> hoặc <b>người duyệt đúng phạm vi</b>.
     *
     * <p>Không có lối thứ ba. Đơn chở số điện thoại và vài dòng tự giới thiệu của một người đang
     * sống; mở cho "mọi thành viên đã đăng nhập" là biến hàng chờ duyệt thành một danh bạ.</p>
     */
    @Transactional(readOnly = true)
    public PersonClaimView byId(UUID claimId) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);
        PersonClaim claim = load(claimId);
        if (claim.requestedBy().equals(caller.appUserId())) {
            // Nguoi gui doc don cua chinh minh: KHONG kem anh chup do trung. Xem toRequesterView.
            return toRequesterView(claim);
        }
        guard.requireReviewAccess(caller, branchPathOf(claim));
        return toReviewerView(claim);
    }

    // -------------------------------------------------------------------------------------
    // Duyệt / từ chối / rút
    // -------------------------------------------------------------------------------------

    /**
     * Duyệt hoặc từ chối một đơn.
     *
     * <h2>Duyệt là một transaction, và nó làm bốn việc hoặc không làm gì cả</h2>
     * Với đơn {@code NEW_PERSON}: <b>tạo nhân khẩu</b> (cùng lúc với cạnh quan hệ vào người thân —
     * ràng buộc 4) · <b>gắn tài khoản</b> vào nhân khẩu ấy · <b>đóng đơn</b> · ghi nhật ký. Hỏng ở
     * bất kỳ bước nào thì cả bốn cùng cuộn lại, và đặc biệt là <b>không có nhân khẩu nào ở lại
     * trong phả</b> — điều mà ràng buộc 1 đòi hỏi và mà một node đã tạo thì xoá mềm không gỡ được.
     *
     * <h2>Gắn tài khoản ↔ nhân khẩu đi qua {@link InvitationLinker}, không phải một đường ghi mới</h2>
     * Design 07 §1.3 nói thẳng: dùng lại cơ chế của luồng mời cá nhân, đã chạy thật. Lý do thật sự
     * không phải tiết kiệm dòng mã mà là hai phép kiểm nằm trong đó — "tài khoản này đã gắn người
     * khác chưa" và "nhân khẩu này đã có tài khoản khác chưa" — mà phép thứ hai là một
     * <b>đường đua</b> chỉ đúng khi chạy cùng transaction với lần ghi.
     *
     * <h2>Duyệt xong thì các đơn còn lại cùng trỏ một người phải được ĐÓNG TƯỜNG MINH</h2>
     * §1.4 chốt rằng Trưởng chi thấy cả hai đơn rồi chọn. Hệ quả: đơn không được chọn không tự biến
     * mất. Để nó nằm lại {@code PENDING} là để lại một đơn <b>vĩnh viễn không duyệt được</b> (nhân
     * khẩu đã có tài khoản) làm nghẽn hàng chờ, và người gửi thì không bao giờ nhận được câu trả
     * lời. Xem {@link #dongCacDonConLai}.
     */
    @Transactional
    public PersonClaimView review(ReviewPersonClaimCommand command) {
        if (command == null || command.claimId() == null) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Phai chi dinh don can xu");
        }
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);

        PersonClaim claim = load(command.claimId());
        requireOpen(claim);
        // PHAM VI CHI. Truong chi At khong duyet duoc don tro vao nguoi chi Binh.
        guard.requireReviewAccess(caller, branchPathOf(claim));

        Instant now = Instant.now();
        if (!command.approve()) {
            return tuChoi(claim, caller, command.note(), now);
        }
        return duyet(claim, caller, command.note(), now);
    }

    /** Người gửi tự rút lại đơn của mình — và nhờ đó gửi được đơn khác ngay. */
    @Transactional
    public PersonClaimView cancel(UUID claimId) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);
        PersonClaim claim = load(claimId);
        try {
            claim.cancel(caller.appUserId(), Instant.now());
        } catch (IllegalStateException ex) {
            throw new ForbiddenException(MembershipProblemCodes.FORBIDDEN, ex.getMessage());
        }
        PersonClaim saved = claims.save(claim);
        audit.record(ENTITY, saved.id().toString(), AuditAction.UPDATE, null,
                saved.auditSnapshot(), List.of("status"), "Nguoi gui rut lai don");
        return toRequesterView(saved);
    }

    // -------------------------------------------------------------------------------------
    // Nội bộ — duyệt
    // -------------------------------------------------------------------------------------

    private PersonClaimView duyet(PersonClaim claim, MemberScope reviewer, String note,
                                  Instant now) {
        UUID nhanKhauMoi = claim.kind().taoNhanKhauKhiDuyet() ? taoNhanKhau(claim) : null;
        UUID nhanKhau = nhanKhauMoi != null ? nhanKhauMoi : claim.personId();

        try {
            claim.approve(reviewer.appUserId(), note, now, nhanKhauMoi);
        } catch (IllegalStateException ex) {
            throw new ForbiddenException(MembershipProblemCodes.SELF_REVIEW_FORBIDDEN,
                    ex.getMessage());
        }

        // DUONG GHI DUY NHAT noi app_user voi person — xem javadoc InvitationLinker#linkExistingAccount.
        AppUser linked = linker.linkExistingAccount(claim.requestedBy(), nhanKhau,
                "Ghep tai khoan qua don tu nhan " + claim.id());

        ghiSoDienThoaiVaoHoSo(claim, nhanKhau);

        PersonClaim saved = claims.save(claim);
        audit.record(ENTITY, saved.id().toString(), AuditAction.APPROVE, null,
                saved.auditSnapshot(), List.of("status", "reviewerId", "createdPersonId"), note);

        if (saved.kind() == PersonClaimKind.EXISTING) {
            dongCacDonConLai(saved, reviewer, now);
        }
        log.info("Duyet don {} ({}): app_user {} ghep voi person {} boi app_user {}",
                saved.id(), saved.kind(), linked.id(), nhanKhau, reviewer.appUserId());
        return toReviewerView(saved);
    }

    /**
     * Tạo nhân khẩu cho một đơn {@code NEW_PERSON} — <b>đây là lần đầu tiên và duy nhất</b> luồng
     * này chạm vào phả.
     *
     * <p>Chi của nhân khẩu mới lấy theo chi của <b>người thân được chỉ ra</b>: người mới chưa thuộc
     * chi nào, và đó cũng chính là chi mà Trưởng chi đang duyệt có quyền trên đó — nên phép kiểm
     * quyền bên trong {@code genealogy} sẽ cho qua vì đúng lý do, không phải vì tình cờ.</p>
     *
     * <p>Với cạnh hôn phối, vế nào đứng ở đầu {@code from} được suy từ <b>giới tính tự khai</b>:
     * {@code spouse_order} ("vợ thứ mấy") gắn vào người chồng, nên đảo vế là gán thứ tự vợ cho
     * người vợ. Giới tính không khai thì mặc định người mới đứng vế vợ — sai một chiều còn sửa
     * được bằng một lệnh đổi cạnh, còn gán nhầm {@code spouse_order} thì phải đánh số lại cả dãy.</p>
     */
    private UUID taoNhanKhau(PersonClaim claim) {
        RelativeKind kind = claim.relativeKind();
        UUID chaId = kind == RelativeKind.FATHER ? claim.relativePersonId() : null;
        UUID meId = kind == RelativeKind.MOTHER ? claim.relativePersonId() : null;
        UUID vongChongId = kind == RelativeKind.SPOUSE ? claim.relativePersonId() : null;
        boolean laChong = vongChongId != null && claim.declaredGender() == Gender.MALE;

        return personWriter.themNhanKhauTuDon(new ClaimPersonWriterPort.NewPersonFromClaim(
                claim.declaredName(), claim.declaredGender(), claim.declaredBirthYear(),
                claim.targetBranchId(), chaId, meId, vongChongId, laChong,
                "Them vao pha khi duyet don tu nhan " + claim.id()));
    }

    /**
     * Số điện thoại trên đơn chảy vào {@code person.contact} khi duyệt — <b>chỉ khi</b> hồ sơ chưa
     * có số nào.
     *
     * <h2>Vì sao ghi, và vì sao chỉ ghi khi còn trống</h2>
     * Design 07 §1.4 chốt "ghi vào hồ sơ nhân khẩu khi duyệt". Số này đã qua một lần kiểm chứng
     * thật — Trưởng chi gọi nó trước khi bấm Duyệt — nên nó là dữ liệu liên hệ tốt nhất hệ thống
     * có về người này, và là thứ khiến lời nhắc giỗ gửi tới được. Nhưng một số <i>đã có</i> trong
     * hồ sơ cũng là số Trưởng chi đã đặt, nên ghi đè lặng lẽ là mất một dữ liệu đã kiểm mà không
     * ai thấy. Luật "chỉ khi còn trống" nằm ở {@link ClaimPersonWriterPort#datSoDienThoaiNeuTrong},
     * không có bản sao ở đây.
     *
     * <h2>Lối tắt bị từ chối, nói rõ để không ai thử lại</h2>
     * Nhét số vào {@code person.attributes} (JSONB tự do, mà {@code ImportedPersonDraft}
     * <i>có</i> chở được) sẽ chạy ngay — và sẽ đặt số điện thoại của một người đang sống vào đúng
     * một cột <b>không</b> đi qua bộ lọc nhóm trường V8. Lối tắt ấy phá đúng điều khoản mà cả yêu
     * cầu này dựa vào. Đường đúng là cổng {@code datSoDienThoaiNeuTrong}, nơi giá trị đi qua
     * {@code UpdatePersonService} như mọi lần sửa hồ sơ khác.
     *
     * <h2>Nhật ký ghi RẰNG đã đổi, không ghi SỐ</h2>
     * {@code Person#auditSnapshot()} cố ý không mang {@code contact}, và dòng nhật ký ở đây cũng
     * vậy: liên hệ là dữ liệu Tầng 3, {@code audit_log} không được thành một bản sao không kiểm
     * soát của nó.
     */
    private void ghiSoDienThoaiVaoHoSo(PersonClaim claim, UUID personId) {
        boolean daGhi = personWriter.datSoDienThoaiNeuTrong(personId, claim.phone(),
                "So dien thoai nguoi khai, da kiem chung khi duyet don tu nhan " + claim.id());
        if (daGhi) {
            log.info("Duyet don {}: ghi so dien thoai nguoi khai vao ho so nhan khau {}",
                    claim.id(), personId);
        } else {
            log.info("Duyet don {}: ho so nhan khau {} da co so dien thoai, giu nguyen",
                    claim.id(), personId);
        }
        audit.record(ENTITY, claim.id().toString(), AuditAction.UPDATE, null,
                Map.of("phoneWritten", String.valueOf(daGhi), "personId", String.valueOf(personId)),
                List.of("phone"),
                daGhi ? "Ghi so dien thoai nguoi khai vao ho so nhan khau"
                      : "Ho so nhan khau da co so dien thoai; giu nguyen so cu");
    }

    /**
     * Đóng các đơn còn lại cùng trỏ một nhân khẩu, sau khi một đơn đã được duyệt.
     *
     * <p>Không phải dọn dẹp cho gọn: một đơn nằm lại {@code PENDING} trên một nhân khẩu đã có tài
     * khoản là đơn <b>vĩnh viễn không duyệt được</b> — mọi lần bấm Duyệt sẽ ném
     * {@code PERSON_ALREADY_LINKED} — nên nó nghẽn hàng chờ mãi mãi và người gửi không bao giờ nhận
     * được câu trả lời.</p>
     *
     * <p>Nhánh {@code cancel}: nếu đơn còn lại là của chính người đang duyệt thì luật "không tự
     * duyệt đơn của mình" sẽ chặn {@code reject}. Ca ấy hiếm nhưng có thật (một Trưởng chi cũng có
     * thể chưa được ghép vào phả), và để nó ném ra thì cả lần duyệt hợp lệ bị cuộn lại vì một đơn
     * phụ.</p>
     */
    private void dongCacDonConLai(PersonClaim approved, MemberScope reviewer, Instant now) {
        List<PersonClaim> others = claims.othersClaiming(approved.personId(), approved.id(),
                PersonClaimStatus.PENDING);
        if (others.isEmpty()) {
            return;
        }
        String lyDo = "Nhan khau nay da duoc ghep voi mot don khac da duoc duyet";
        for (PersonClaim other : others) {
            if (other.requestedBy().equals(reviewer.appUserId())) {
                other.cancel(reviewer.appUserId(), now);
            } else {
                other.reject(reviewer.appUserId(), lyDo, now);
            }
            PersonClaim saved = claims.save(other);
            audit.record(ENTITY, saved.id().toString(), AuditAction.REJECT, null,
                    saved.auditSnapshot(), List.of("status", "reviewerId"), lyDo);
        }
        log.info("Dong {} don con lai cung tro vao person {} sau khi duyet don {}",
                others.size(), approved.personId(), approved.id());
    }

    private PersonClaimView tuChoi(PersonClaim claim, MemberScope reviewer, String note,
                                   Instant now) {
        try {
            claim.reject(reviewer.appUserId(), note, now);
        } catch (IllegalStateException ex) {
            throw new ForbiddenException(MembershipProblemCodes.SELF_REVIEW_FORBIDDEN,
                    ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED, ex.getMessage(), ex);
        }
        PersonClaim saved = claims.save(claim);
        audit.record(ENTITY, saved.id().toString(), AuditAction.REJECT, null,
                saved.auditSnapshot(), List.of("status", "reviewerId"), note);
        log.info("Tu choi don {} boi app_user {}", saved.id(), reviewer.appUserId());
        return toReviewerView(saved);
    }

    // -------------------------------------------------------------------------------------
    // Nội bộ — phép kiểm chung
    // -------------------------------------------------------------------------------------

    /**
     * Người gửi đơn phải là <b>một tài khoản chưa ghép nhân khẩu nào</b>, chưa có đơn đang chờ,
     * chưa bị từ chối quá số lần, và chưa gửi quá tổng số đơn cho phép.
     *
     * <p>Bốn phép kiểm này gom vào một chỗ vì cả hai loại đơn đều cần đủ bốn, và chép rời ra thì
     * một lối sẽ thiếu một phép — gần như chắc chắn là phép đếm, vì nó là phép duy nhất không hiện
     * ra trong lần thử đầu tiên.</p>
     *
     * <h2>Vì sao có TRẦN TỔNG SỐ ĐƠN bên cạnh trần số lần bị từ chối</h2>
     * {@code rejectedCountOf} cố ý <b>không</b> đếm đơn tự rút: người gõ nhầm rồi rút không phải là
     * người đang dò. Nhưng hệ quả là <b>rút đơn thì miễn phí</b>, và "gửi → đọc → rút → lặp" trở
     * thành một vòng lặp vô hạn không tốn gì — lối này đi thẳng qua mọi phép đếm khác, vì đơn bị
     * rút không để lại vết nào ở bộ đếm nào.
     *
     * <p>Hai trần vì hai câu hỏi khác nhau: {@code max-rejected} hỏi "Trưởng chi đã nói không mấy
     * lần" (thấp, hiện ra trên màn hình, là một phán quyết của con người);
     * {@code max-attempts} hỏi "tài khoản này đã gõ vào cửa bao nhiêu lần" (cao hơn nhiều, chỉ chạm
     * tới khi có người lặp máy móc). Gộp hai con số vào một trần sẽ hoặc khoá nhầm người tự rút
     * đơn vì gõ nhầm, hoặc để ngỏ vòng lặp kia.</p>
     */
    private MemberScope requireUnlinkedMember() {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);

        if (caller.personId() != null) {
            throw new DomainException(MembershipProblemCodes.ACCOUNT_ALREADY_LINKED,
                    "Tai khoan cua ban da duoc ghep voi mot nhan khau trong pha");
        }
        if (claims.openForRequester(caller.appUserId()).isPresent()) {
            throw new DomainException(MembershipProblemCodes.CLAIM_ALREADY_OPEN,
                    "Ban dang co mot don cho duyet. Hay rut don ay truoc khi gui don khac.");
        }
        // TRAN TONG SO DON — dem CA don da rut. Xem javadoc phuong thuc.
        int daGui = claims.totalCountOf(caller.appUserId());
        if (daGui >= maxAttempts) {
            log.warn("App_user {} da gui {} don, cham tran tong so lan gui", caller.appUserId(),
                    daGui);
            throw new DomainException(MembershipProblemCodes.CLAIM_LIMIT_REACHED,
                    "Ban da gui " + daGui + " don tu nhan. Hay lien he Truong chi de duoc ho tro"
                            + " truc tiep.");
        }
        int daBiTuChoi = claims.rejectedCountOf(caller.appUserId());
        if (daBiTuChoi >= maxRejected) {
            // GIOI HAN GUI LAI (§1.4). Khong gioi han thi man nay thanh cach do dung nguoi bang
            // cach thu lan luot: gui don nhan ong A, bi tu choi, gui tiep ong B, cho toi khi trung.
            log.warn("App_user {} da bi tu choi {} lan, chan gui don moi",
                    caller.appUserId(), daBiTuChoi);
            throw new DomainException(MembershipProblemCodes.CLAIM_LIMIT_REACHED,
                    "Don cua ban da bi tu choi " + daBiTuChoi + " lan. "
                            + "Hay lien he Truong chi de duoc ho tro truc tiep.");
        }
        return caller;
    }

    private MemberScope requireReviewer() {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);
        if (!caller.role().canReview()) {
            throw new ForbiddenException(MembershipProblemCodes.FORBIDDEN,
                    "Chi Truong chi, Hoi dong Toc bieu hoac Quan tri he thong duoc duyet don tu nhan");
        }
        return caller;
    }

    private PersonClaimView luuVaGhiNhatKy(PersonClaim claim, MemberScope caller) {
        PersonClaim saved = claims.save(claim);
        audit.record(ENTITY, saved.id().toString(), AuditAction.CREATE, null,
                saved.auditSnapshot(), saved.auditSnapshot().keySet().stream().toList(),
                "Gui don tu nhan minh trong pha");
        log.info("Don tu nhan {} ({}) duoc gui boi app_user {}, chi dich {}",
                saved.id(), saved.kind(), caller.appUserId(), saved.targetBranchId());
        return toRequesterView(saved);
    }

    private PersonClaim load(UUID claimId) {
        return claims.byId(claimId).orElseThrow(() -> new NotFoundException(
                MembershipProblemCodes.NOT_FOUND, "Khong tim thay don " + claimId));
    }

    private static void requireOpen(PersonClaim claim) {
        if (claim.status().isFinal()) {
            throw new DomainException(MembershipProblemCodes.CLAIM_CLOSED,
                    "Don da o trang thai " + claim.status() + ", khong xu ly lai duoc");
        }
    }

    /**
     * Path {@code ltree} của chi đích.
     *
     * <p>Trả {@code null} khi chi đích rỗng hoặc đã bị xoá — và {@code BranchScopeGuard} coi
     * {@code null} là "chỉ vai toàn dòng họ được đụng". Đó là hướng an toàn đúng: "không có chi"
     * tuyệt đối không được hiểu thành "thuộc mọi chi".</p>
     */
    private BranchPath branchPathOf(PersonClaim claim) {
        return Optional.ofNullable(claim.targetBranchId())
                .flatMap(branches::pathOfBranch)
                .orElse(null);
    }

    /**
     * Dựng bản hiển thị của một đơn, kèm ba khối tra cứu mà màn hình cần.
     *
     * <h2>{@code competingClaimIds} là một chốt nghiệp vụ, không phải một tiện ích hiển thị</h2>
     * Design 07 §1.4 chốt rằng hai người cùng nhận một nhân khẩu thì Trưởng chi thấy <b>cả hai</b>
     * rồi chọn. V16 <i>đã</i> làm đúng phần của mình khi từ chối một chỉ mục duy nhất trên nhân
     * khẩu đang có đơn chờ — nhờ thế "ai gửi trước thắng" không thành luật.
     *
     * <p>Nhưng nếu máy chủ không <b>đánh dấu</b> sự tranh chấp thì quyết định ấy mất tác dụng ở
     * đúng ca nó sinh ra để phục vụ: đơn thứ hai nằm ở trang 2 của hàng chờ là <b>vô hình</b>, và
     * Trưởng chi duyệt đơn duy nhất mình nhìn thấy — tức là "ai gửi trước thắng", chỉ qua một đường
     * vòng. Trường này là chỗ ý ấy được nói nốt.</p>
     *
     * <p>Chỉ trả <b>khoá</b>, không trả nội dung đơn kia: đơn kia chở số điện thoại của một người
     * thứ ba, và người đọc có thể là người gửi đơn này chứ không phải Trưởng chi. Giao diện cầm
     * khoá rồi gọi {@code GET /person-claims/&#123;id&#125;}, nơi phép kiểm quyền thật sự chạy.</p>
     */
    /**
     * Bản đơn cho <b>người duyệt</b> — kèm ảnh chụp dò trùng và các đơn đối thủ.
     *
     * <p>Chỉ gọi được sau khi {@code guard.requireReviewAccess} đã chạy cho đúng chi của đơn.</p>
     */
    private PersonClaimView toReviewerView(PersonClaim claim) {
        return toView(claim, true);
    }

    /**
     * Bản đơn cho <b>người gửi</b> — <b>không</b> có {@code duplicateSuspects} và
     * {@code competingClaimIds}.
     *
     * <h2>Vì sao hai trường ấy không đi ra màn của người gửi</h2>
     * {@link vn.giapha.membership.domain.ClaimDuplicateSuspect} cố ý không mang tên hay năm sinh —
     * nhưng danh sách {@code signals} thì mang: {@code NAM_SINH_KHOP} · {@code NAM_MAT_KHOP} ·
     * {@code CUNG_CHI} · {@code CUNG_NGUYEN_QUAN} · {@code DOI_*}. Ghép lại, một tài khoản
     * <b>tự đăng ký, chưa được duyệt</b> chỉ cần đoán một cái tên cộng một năm sinh là biết được
     * trong chi B có một người <i>còn sống</i> trùng năm sinh và trùng nguyên quán — dữ liệu Tầng 2
     * (BA v2 §10) rò ra qua một kênh phụ kiểu bộ đếm, không qua bộ lọc phân tầng nào.
     *
     * <p>{@code competingClaimIds} thì nói "có người khác cũng đang nhận chính ô này" — một tín
     * hiệu về hoạt động của người thứ ba, và là thứ người gửi không cần để làm gì: quyết định đã
     * chốt là <b>Trưởng chi</b> thấy cả hai đơn rồi chọn.</p>
     *
     * <p>Người gửi <b>không mất gì</b>: cả hai trường là công cụ của màn duyệt. Xem
     * {@link PersonClaimView} về cách đọc sự vắng mặt ấy.</p>
     */
    private PersonClaimView toRequesterView(PersonClaim claim) {
        return toView(claim, false);
    }

    private PersonClaimView toView(PersonClaim claim, boolean forReviewer) {
        String name = appUsers.byId(claim.requestedBy())
                .map(AppUser::displayName)
                .orElse(null);
        BranchSummary branch = claim.targetBranchId() == null ? null
                : branches.summaryOfBranch(claim.targetBranchId()).orElse(null);
        List<UUID> tranhChap = !forReviewer || claim.personId() == null || !claim.status().isOpen()
                ? List.of()
                : claims.othersClaiming(claim.personId(), claim.id(), PersonClaimStatus.PENDING)
                        .stream().map(PersonClaim::id).toList();
        return PersonClaimView.from(claim, name, claims.totalCountOf(claim.requestedBy()), branch,
                tranhChap, forReviewer);
    }

    private static int clamp(int size) {
        return Math.min(Math.max(size, 1), 200);
    }

    /**
     * Phép dịch <b>duy nhất</b> giữa kiểu đi qua ranh giới module và kiểu {@code PersonClaim} giữ.
     *
     * <p>Hai kiểu gần giống hệt nhau, và đó là cái giá đã biết của việc đảo phụ thuộc: bên hiện
     * thực cổng nằm <i>ngoài</i> {@code membership} nên nó chỉ được nhìn thấy
     * {@code membership.application}, còn {@code PersonClaim} thì ở {@code membership.domain}. Gộp
     * làm một nghĩa là mở cả gói {@code domain} ra ngoài — tức mở luôn {@code AppUser},
     * {@code Invitation}, {@code MemberScope}. Xem javadoc {@link ClaimScreeningPort}.</p>
     */
    private static ClaimDuplicateSuspect toDomain(ClaimScreeningPort.Suspect suspect) {
        return new ClaimDuplicateSuspect(suspect.personId(), suspect.score(), suspect.signals(),
                suspect.hint());
    }
}
