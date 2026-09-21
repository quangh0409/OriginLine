package vn.giapha.membership.application;

import java.time.Duration;
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
import vn.giapha.membership.application.command.IssueClanInviteCommand;
import vn.giapha.membership.application.command.RegisterWithClanInviteCommand;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.ClanInviteCode;
import vn.giapha.membership.domain.ClanInviteRedemption;
import vn.giapha.membership.domain.ClanInviteUsability;
import vn.giapha.membership.domain.InvitationCode;
import vn.giapha.membership.domain.LoginIdentifier;
import vn.giapha.membership.domain.MemberScope;
import vn.giapha.membership.domain.port.BranchLookupPort;
import vn.giapha.membership.domain.port.ClanInviteCodeRepository;
import vn.giapha.membership.domain.port.ClanInviteRedemptionRepository;
import vn.giapha.membership.domain.port.IdentityAccount;
import vn.giapha.membership.domain.port.InviteeLookupPort;
import vn.giapha.membership.domain.port.IdentityProviderException;
import vn.giapha.membership.domain.port.IdentityProviderPort;
import vn.giapha.membership.domain.port.NewIdentityAccount;
import vn.giapha.membership.domain.port.SetPasswordLink;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.security.CurrentUser;
import vn.giapha.shared.security.CurrentUserProvider;

/**
 * <b>Mã mời dòng họ</b> — phát · xem · thu hồi · đăng ký bằng mã.
 *
 * <h2>Đây là cơ chế RIÊNG, không phải biến thể của {@link InvitationService}</h2>
 * Bảng {@code invitation} có {@code person_id NOT NULL}, và chính điều đó làm người nhận mã cá nhân
 * không phải chờ duyệt: Trưởng chi đã chỉ đích danh khi phát. Mã dòng họ thì cấp cho <b>cả họ</b>,
 * dùng được <b>nhiều lần</b>, và chỉ mở đúng hai cửa: <i>đăng ký tài khoản</i> và <i>xem phả đồ</i>.
 * Dùng xong vẫn phải tự nhận mình ({@link PersonClaimService}) rồi chờ Trưởng chi duyệt.
 *
 * <p>Giữ cả hai là đúng: mã cá nhân dành cho <b>các cụ lớn tuổi</b> — họ sẽ không tự đăng ký rồi tự
 * tìm mình trên phả đồ, Trưởng chi làm hộ từ đầu tới cuối. Mã dòng họ dành cho <b>người trẻ và
 * người ở xa</b>.</p>
 *
 * <h2>Nói thẳng một lần: mã mời MỚI LÀ ranh giới an toàn, không phải bước duyệt</h2>
 * Cấp mã cho cả họ nghĩa là <b>ai cầm được mã là xem được danh sách toàn bộ người đang sống của
 * dòng họ</b> — tên, đời, quan hệ. Và mã ấy <i>sẽ</i> lan: nó được dán vào nhóm Zalo, chuyển tiếp
 * cho người thân, chụp màn hình. Bước duyệt kiểm soát thứ khác: ai được gắn vào hồ sơ của ai, tức
 * quyền sửa phả và quyền đọc dữ liệu nhạy cảm của chính hồ sơ ấy.
 *
 * <p>Quyết định cấp mã cho cả họ là của chủ dự án (design 07 §8, quyết định 1) và lớp này làm theo.
 * Nhưng vì chỉ còn <em>một</em> lớp bảo vệ, lớp ấy phải quản được — <b>bốn chốt dưới đây là bắt
 * buộc, không phải tuỳ chọn</b>, và mỗi chốt có một chỗ duy nhất trong mã:</p>
 * <ol>
 *   <li><b>Có hạn dùng</b> — {@link #ttlOf}; {@code expires_at} là {@code NOT NULL} ở V16, không có
 *       giá trị "vô hạn". Một tờ giấy bỏ quên năm 2026 không được mở phả năm 2030.</li>
 *   <li><b>Thu hồi được</b> — {@link #revoke}; đóng lại <i>ngay</i> mà không ảnh hưởng người đã
 *       vào, vì tài khoản của họ không treo vào mã.</li>
 *   <li><b>Đếm lượt dùng</b> — {@code clan_invite_code.use_count}, tăng nguyên tử ở
 *       {@link ClanInviteRedeemer}. <b>Chốt quan trọng nhất và dễ bỏ qua nhất</b>: Hội đồng thấy mã
 *       đã dùng 400 lần trong khi dòng họ có 600 người thì <i>biết</i> mà thu hồi. Không có bộ đếm
 *       thì mã rò ra và mọi thứ trông vẫn bình thường.</li>
 *   <li><b>Giới hạn tần suất</b> — {@link InviteThrottle}, dùng chung bộ đếm với mã cá nhân. Mã
 *       ngắn để đọc qua điện thoại thì cũng ngắn để đoán.</li>
 * </ol>
 *
 * <p>Và một thứ thứ năm, rẻ và giúp nhiều: <b>ghi lại ai đã dùng mã nào</b>
 * ({@code clan_invite_redemption}), để khi có chuyện thì truy được người đưa mã ra ngoài.</p>
 *
 * <h2>PHÁT VÀ THU HỒI LÀ VIỆC CỦA HỘI ĐỒNG, không phải của Trưởng chi</h2>
 * {@link #issue} và {@link #revoke} gọi {@code requireClanWide}, <b>khác</b>
 * {@code InvitationService.issue} vốn cho Trưởng chi phát trong phạm vi chi mình. Hai chỗ cố ý khác
 * nhau vì <i>phạm vi hậu quả</i> khác nhau: một mã cá nhân rò ra mở đúng một hồ sơ; một mã dòng họ
 * rò ra mở <b>cả dòng họ</b>. Quyền phát phải đi cùng phạm vi hậu quả, và design 07 §1.3 nói thẳng
 * "Hội đồng phát và thu hồi".
 */
@Service
public class ClanInviteService {

    private static final Logger log = LoggerFactory.getLogger(ClanInviteService.class);

    private static final String ENTITY = "ClanInviteCode";

    /**
     * Chặn trên của hạn hiệu lực — <b>90 ngày</b>, dài hơn mã cá nhân (30) và có lý do.
     *
     * <p>Mã dòng họ phát ra ở một dịp có thật: lễ giỗ tổ, buổi họp họ, một đợt vận động con cháu ở
     * xa. Người ở nước ngoài nhận tin nhắn của người thân rồi vài tuần sau mới ngồi xuống đăng ký.
     * Hạn 7 ngày ở đây sẽ làm cả đợt vận động hỏng và Hội đồng sẽ phản ứng bằng cách phát lại mã
     * liên tục — tức là nhiều mã sống song song, đúng thứ mà chốt 1 sinh ra để tránh.</p>
     *
     * <p>Nhưng trần vẫn phải có: quá 90 ngày thì "có hạn" chỉ còn là chữ. Hội đồng muốn dài hơn thì
     * phát mã mới — và lần phát ấy để lại một dòng trong {@code audit_log}, còn một mã ba năm thì
     * không để lại gì cả.</p>
     */
    private static final int MAX_TTL_DAYS = 90;

    private final ClanInviteCodeRepository codes;
    private final ClanInviteRedemptionRepository redemptions;
    private final BranchLookupPort branches;
    private final InviteeLookupPort persons;
    private final MemberScopeService scopes;
    private final BranchScopeGuard guard;
    private final ClanInviteRedeemer redeemer;
    private final IdentityEnroller enroller;
    private final IdentityProviderPort identityProvider;
    private final IdentityReclaimPolicy reclaim;
    private final InviteThrottle throttle;
    private final AuditTrailService audit;
    private final int defaultTtlDays;

    @SuppressWarnings("java:S107")
    public ClanInviteService(ClanInviteCodeRepository codes,
                             ClanInviteRedemptionRepository redemptions,
                             BranchLookupPort branches, InviteeLookupPort persons,
                             MemberScopeService scopes,
                             BranchScopeGuard guard, ClanInviteRedeemer redeemer,
                             IdentityEnroller enroller,
                             IdentityProviderPort identityProvider,
                             IdentityReclaimPolicy reclaim, InviteThrottle throttle,
                             AuditTrailService audit,
                             @Value("${giapha.membership.clan-invite.ttl-days:30}") int defaultTtlDays) {
        this.codes = codes;
        this.redemptions = redemptions;
        this.branches = branches;
        this.persons = persons;
        this.scopes = scopes;
        this.guard = guard;
        this.redeemer = redeemer;
        this.enroller = enroller;
        this.identityProvider = identityProvider;
        this.reclaim = reclaim;
        this.throttle = throttle;
        this.audit = audit;
        this.defaultTtlDays = defaultTtlDays;
    }

    // -------------------------------------------------------------------------------------
    // Hội đồng: phát · xem · thu hồi
    // -------------------------------------------------------------------------------------

    /**
     * Phát một mã mời dòng họ. Trả về mã thô <b>đúng một lần</b>.
     *
     * <p><b>Không</b> thu hồi mã cũ, khác hẳn {@code InvitationService.issue}. Mã cá nhân phải thu
     * hồi vì hai mã cùng mở được <i>một hồ sơ</i> thì "thu hồi" mất nghĩa. Mã dòng họ không mở hồ sơ
     * nào, và nhiều mã song song chính là cách Hội đồng biết mã nào đã rò: một mã cho nhóm Zalo, một
     * mã phát tại lễ giỗ tổ, và bộ đếm của từng mã nói cho họ biết kênh nào đang chảy.</p>
     */
    @Transactional
    public IssuedClanInvite issue(IssueClanInviteCommand command) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);
        // PHAT MA DONG HO LA VIEC CUA HOI DONG. Xem javadoc lop truoc khi noi dong nay.
        guard.requireClanWide(caller, "phat ma moi cho ca dong ho");

        Integer maxUses = command == null ? null : command.maxUses();
        if (maxUses != null && maxUses < 1) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Tran luot dung phai tu 1 tro len; de trong neu khong muon dat tran");
        }

        Instant now = Instant.now();
        String code = InvitationCode.generate();
        ClanInviteCode invite = ClanInviteCode.issue(UUID.randomUUID(), InvitationCode.hash(code),
                command == null ? null : command.label(), caller.appUserId(),
                now.plus(ttlOf(command)), maxUses, command == null ? null : command.note());
        ClanInviteCode saved = codes.save(invite);

        // KHONG ghi ma tho vao audit, va ca bam cung khong: audit_log la bang chi ghi them, mot bi
        // mat lot vao do la lot vinh vien.
        audit.record(ENTITY, saved.id().toString(), AuditAction.CREATE, null,
                Map.of("label", String.valueOf(saved.label()),
                        "expiresAt", String.valueOf(saved.expiresAt()),
                        "maxUses", String.valueOf(saved.maxUses())),
                List.of("label", "expiresAt", "maxUses"), "Phat ma moi cho ca dong ho");
        log.info("Phat ma moi dong ho {} boi app_user {}, han {}, tran luot {}",
                saved.id(), caller.appUserId(), saved.expiresAt(), saved.maxUses());

        return new IssuedClanInvite(code, ClanInviteView.from(saved, now));
    }

    /**
     * Danh sách mã — <b>kèm bộ đếm</b>. Đây là màn hình làm chốt 3 có tác dụng.
     *
     * <p>Chỉ vai toàn dòng họ: mã dòng họ không có chi để lọc theo, nên không có "phạm vi" nào để
     * cắt danh sách này cho Trưởng chi. Cho Trưởng chi xem cũng không sai về nguyên tắc, nhưng nó
     * gợi ý rằng họ phát được — và họ thì không.</p>
     */
    @Transactional(readOnly = true)
    public ClanInviteList list(int page, int size) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);
        guard.requireClanWide(caller, "xem danh sach ma moi dong ho");
        int limit = Math.min(Math.max(size, 1), 200);
        Instant now = Instant.now();
        List<ClanInviteView> views = codes.all(limit, Math.max(page, 0) * limit).stream()
                .map(code -> ClanInviteView.from(code, now))
                .toList();
        // MAU SO cua bo dem. Xem javadoc ClanInviteList: 400 luot tren mot dong ho 600 nguoi la
        // mot cau hoi; 400 luot tran trui thi khong noi len dieu gi.
        return new ClanInviteList(views, persons.countLivingPersons());
    }

    /**
     * <b>Ai đã dùng mã này</b> — thứ truy được người đưa mã ra ngoài.
     *
     * <p>{@code use_count} nói bao nhiêu lượt; danh sách này nói ai. Với 1.500 người thì đó là câu
     * hỏi Hội đồng <i>sẽ</i> hỏi khi bộ đếm nhảy bất thường, không phải có thể hỏi.</p>
     */
    @Transactional(readOnly = true)
    public List<ClanInviteRedemption> redemptionsOf(UUID codeId, int page, int size) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);
        guard.requireClanWide(caller, "xem nhat ky dung ma moi dong ho");
        int limit = Math.min(Math.max(size, 1), 200);
        return redemptions.byCode(codeId, limit, Math.max(page, 0) * limit);
    }

    /**
     * <b>Chốt 2</b> — thu hồi một mã.
     *
     * <p>Người đã vào bằng mã này <b>không bị ảnh hưởng</b>: tài khoản của họ đã tồn tại và không
     * treo vào mã. Đúng yêu cầu "đóng lại ngay mà không ảnh hưởng người đã vào".</p>
     *
     * <p>Thu hồi một mã <i>đã hết hạn</i> là hợp lệ và không ném: đó là điều Hội đồng làm khi dọn
     * danh sách. Và không có phép mở lại — cần mã mới thì phát mã mới; mã cũ ở lại với bộ đếm của
     * nó, vì chính con số ấy là thứ đáng giữ.</p>
     */
    @Transactional
    public ClanInviteView revoke(UUID codeId, String reason) {
        MemberScope caller = scopes.currentMemberScope();
        guard.requireProvisionedAccount(caller);
        guard.requireClanWide(caller, "thu hoi ma moi dong ho");

        ClanInviteCode code = codes.byId(codeId)
                .orElseThrow(() -> new NotFoundException(MembershipProblemCodes.NOT_FOUND,
                        "Khong tim thay ma moi dong ho " + codeId));
        Instant now = Instant.now();
        code.revoke(reason, now);
        ClanInviteCode saved = codes.save(code);

        audit.record(ENTITY, saved.id().toString(), AuditAction.UPDATE, null,
                Map.of("status", saved.status().name(),
                        "useCount", String.valueOf(saved.useCount())),
                List.of("status"), reason == null ? "Thu hoi ma moi dong ho" : reason);
        log.info("Thu hoi ma moi dong ho {} sau {} luot dung, boi app_user {}",
                saved.id(), saved.useCount(), caller.appUserId());
        return ClanInviteView.from(saved, now);
    }

    // -------------------------------------------------------------------------------------
    // Người cầm mã: xem · đăng ký
    // -------------------------------------------------------------------------------------

    /**
     * "Mã này của dòng họ nào" — <b>không đòi đăng nhập</b>, và mã phải được kiểm ở đây
     * <i>trước khi</i> màn đăng ký tạo bất cứ thứ gì.
     *
     * <p>Trả về đúng hai thứ: tên dòng họ và hạn dùng. Xem {@link ClanInvitePreview} về việc vì sao
     * bộ đếm và nhãn mã <b>không</b> đi ra ngoài.</p>
     */
    @Transactional(readOnly = true)
    public ClanInvitePreview preview(String rawCode, String clientId) {
        throttle.guard(clientId);
        ClanInviteCode code = resolveUsable(rawCode, clientId);
        throttle.recordClanSuccess(clientId, false);
        return new ClanInvitePreview(branches.clanName().orElse(null), code.expiresAt());
    }

    /**
     * Đăng ký tài khoản bằng mã dòng họ — <b>không đòi token</b>.
     *
     * <h2>Tài khoản ra khỏi đây CHƯA gắn nhân khẩu nào</h2>
     * Khác hẳn {@code InvitationService.accept}. Mã dòng họ không trỏ vào ai, nên không có gì để
     * ghép, và <b>không được suy đoán</b>: gắn theo trùng tên là cách nhanh nhất để trao cho một
     * người quyền xem dữ liệu Tầng 3 của người khác. Người đăng ký xem được phả đồ ngay (đã chốt),
     * rồi gửi đơn tự nhận qua {@link PersonClaimService}.
     *
     * <h2>THỨ TỰ THAO TÁC — cùng khuôn với {@code InvitationService.accept}</h2>
     * Keycloak và Postgres là hai hệ thống, không có transaction chung. Thứ tự:
     * <ol>
     *   <li>tra mã và kiểm còn dùng được (chỉ đọc — <b>chưa</b> tiêu lượt nào);</li>
     *   <li>tìm-hoặc-tạo tài khoản Keycloak — <b>lặp lại được</b>, gọi hai lần ra cùng tài khoản;</li>
     *   <li>đúc liên kết đặt mật khẩu — thuần tính toán, không để lại vết ở đâu;</li>
     *   <li><b>rồi mới</b> ghi CSDL: tiêu lượt, ghi vết ai dùng, dựng {@code app_user} — cùng một
     *       transaction ({@link ClanInviteRedeemer}).</li>
     * </ol>
     *
     * <p><b>Vì sao không ngược lại.</b> Ghi CSDL trước rồi mới tạo tài khoản Keycloak nghe "an toàn
     * hơn" vì CSDL rollback được, nhưng nó tạo ra đúng cái kẹt không ai gỡ: một lượt đã bị tiêu và
     * một dòng {@code app_user} trỏ tới một {@code sub} không tồn tại. Thứ tự ở trên thì mọi hỏng
     * hóc rơi về cùng một trạng thái lành: <b>bộ đếm chưa nhúc nhích</b>, người dùng bấm lại được,
     * và lần bấm lại tìm thấy tài khoản Keycloak của lần trước thay vì tạo bản sao.</p>
     *
     * <h2>Định danh đăng nhập là EMAIL HOẶC SỐ ĐIỆN THOẠI</h2>
     * Ô đăng nhập của realm nhận cả hai — quyết định ấy có để phục vụ các cụ không có email. Nếu ô
     * đăng ký chỉ nhận email thì một người 70 tuổi gõ đúng thứ mà màn đăng nhập sẽ nhận lại bị từ
     * chối bằng một câu lỗi về khuôn dữ liệu, và đó là mâu thuẫn của sản phẩm chứ không phải lỗi
     * của họ. Xem {@link vn.giapha.membership.domain.LoginIdentifier}.
     *
     * <p><b>Giới hạn có thật, cần nói với người dùng:</b> tài khoản lập bằng số điện thoại
     * <i>không</i> nhận được thư đặt lại mật khẩu khi hệ thống có SMTP. Đó là giới hạn của việc
     * <b>khôi phục</b>, không phải của việc đăng ký — và hôm nay nó chưa khác gì, vì chưa có SMTP
     * và liên kết đặt mật khẩu được trả thẳng trong phản hồi HTTP.</p>
     *
     * <p><b>Tài khoản Keycloak mồ côi thì KHÔNG xoá đi</b> — nó không ghép với nhân khẩu nào nên
     * không thấy được gì ngoài dữ liệu công khai, đúng trạng thái của mọi tài khoản vừa đăng nhập
     * Google lần đầu. Xoá lại tự tạo một lỗi mới khi hai người bấm gần nhau.</p>
     *
     * <h2 id="dinhDanhDaCoChu">ĐỊNH DANH ĐÃ CÓ CHỦ THÌ DỪNG LẠI — không phát liên kết, không
     * ghi gì</h2>
     * Endpoint này <b>không đòi đăng nhập</b>. Thứ duy nhất người gọi trình ra là một mã mà cả họ
     * đang cầm (thiết kế nói thẳng: dán vào nhóm Zalo) cộng một chuỗi họ tự gõ vào ô "email hoặc số
     * điện thoại". Chuỗi ấy <b>không chứng minh</b> họ sở hữu định danh đó.
     *
     * <p>Nếu {@code findOrCreate} <i>tìm thấy</i> thay vì tạo, thì tài khoản trả về là tài sản của
     * người khác, và hai bước tiếp theo trở thành hai thao tác trên tài sản ấy:</p>
     * <ul>
     *   <li>{@code issueSetPasswordLink} chỉ từ chối khi tài khoản <b>đã có mật khẩu</b>. Mà "đã
     *       tồn tại nhưng chưa có mật khẩu" chính là trạng thái {@code InvitationService.accept}
     *       để lại cho <b>mọi</b> người được mời chưa bấm vào liên kết — và những tài khoản ấy đã
     *       gắn một nhân khẩu trong phả. Phát liên kết ở đó là trao trọn hồ sơ một thành viên, kể
     *       cả quyền tự đọc dữ liệu Tầng 3 của chính hồ sơ ấy.</li>
     *   <li>{@code redeem} → {@code AppUser.refreshProfile} ghi đè {@code display_name} và
     *       {@code email} của họ bằng giá trị người gọi tự khai. {@code display_name} ấy chính là
     *       thứ Trưởng chi đọc trong hàng chờ duyệt đơn tự nhận.</li>
     * </ul>
     *
     * <p><b>Vì sao từ chối chứ không "im lặng bỏ qua liên kết".</b> Bỏ liên kết mà vẫn đi tiếp thì
     * lượt ghi ở bước (4) vẫn chạm vào dòng {@code app_user} của người khác, và trường
     * {@code status} trong phản hồi vẫn nói ra tài khoản ấy đang {@code ACTIVE} hay chưa — tức vẫn
     * còn nguyên một máy dò. Từ chối sớm đóng cả ba lỗ bằng một chốt, và để lối đi tiếp của người
     * dùng thật rất rõ: <b>đăng nhập rồi nhập lại mã</b>, đúng nhánh
     * {@link #dangKyBangTokenSanCo}.</p>
     *
     * <p><b>Lần bấm lại sau một lần hỏng vẫn đi tiếp được.</b> Nếu bước (4) từng hỏng giữa chừng
     * (CSDL chết), cái còn lại là một tài khoản Keycloak <i>không ai sở hữu</i> — và
     * {@link IdentityReclaimPolicy} nhận ra đúng trạng thái ấy qua hai dấu hiệu đồng thời: realm
     * còn treo {@code UPDATE_PASSWORD} (dấu vết riêng của luồng onboarding này, tài khoản Google
     * không bao giờ có) và <b>chưa có dòng {@code app_user}</b> nào cho {@code sub} ấy. Cửa sổ đòi
     * lại có hạn, bằng đúng hạn của liên kết đặt mật khẩu.</p>
     *
     * <p>Mỗi lần từ chối được <b>tính vào giới hạn tần suất</b> như một lần thất bại: tín hiệu
     * "địa chỉ này đã đăng ký" là thứ không xoá hẳn được, nhưng dò cả danh bạ dòng họ thì phải
     * không làm được trong một cửa sổ.</p>
     */
    public ClanRegistration register(RegisterWithClanInviteCommand command) {
        if (command == null || command.code() == null) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED, "Phai nhap ma moi");
        }
        String clientId = command.clientId();
        throttle.guard(clientId);

        // (1) Tra ma. CHI DOC — chua luot nao bi tieu o buoc nay.
        ClanInviteCode code = resolveUsable(command.code(), clientId);

        Optional<CurrentUser> caller = CurrentUserProvider.current();
        if (caller.isPresent()) {
            return dangKyBangTokenSanCo(code, caller.get(), clientId);
        }
        // EMAIL HOAC SO DIEN THOAI. O dang nhap cua realm nhan ca hai — quyet dinh ay co de
        // phuc vu cac cu khong co email — nen o dang ky chi nhan email la mot mau thuan trong
        // chinh san pham, va no roi dung vao nhom nguoi ma ca luong moi sinh ra de phuc vu.
        LoginIdentifier login = enroller.readIdentifier(command.email());

        // (2) TIM TRUOC, TAO SAU. Nguoi ay co the da co tai khoan tu truoc, hoac vua bam hai lan,
        // hoac dang thu lai sau mot lan dang ky hong.
        IdentityAccount account = enroller.findOrCreate(login, command.displayName());

        // (2b) DINH DANH DA CO CHU THI DUNG LAI O DAY. Xem javadoc #dinhDanhDaCoChu.
        if (!reclaim.mayClaim(account)) {
            throttle.recordFailure(clientId);
            // KHONG phai mot su co. Dong nay gan nhu luc nao cung dung: dinh danh ay DA CO CHU.
            // Chi dang ngo khi co nguoi quyet rang ho vua duoc moi va chua tung dang ky — luc do
            // tim dong INFO cua IdentityReclaimPolicy ngay trên: "ngoai cua so" nghia la tai khoan
            // mo coi cua ho da qua han doi lai va Hoi dong can xoa no o realm.
            log.warn("Tu choi dang ky bang ma dong ho {}: dinh danh kieu {} thuoc ve tai khoan"
                    + " Keycloak {} da co chu, ma nguoi goi khong trinh token", code.id(),
                    login.kind(), account.subject());
            throw new DomainException(MembershipProblemCodes.IDENTITY_ALREADY_REGISTERED,
                    "Dinh danh nay da co tai khoan trong he thong. Hay dang nhap roi nhap lai ma"
                            + " dong ho; neu ban khong dang nhap duoc, hay bao Truong chi hoac Hoi"
                            + " dong Toc bieu.");
        }

        // (3) Duc lien ket — chi cho tai khoan VUA DUOC LAP o buoc tren.
        Optional<SetPasswordLink> link = identityProvider.issueSetPasswordLink(account);

        // (4) Buoc duy nhat khong dao nguoc duoc: tieu luot + ghi vet + dung app_user.
        // app_user.email chi nhan email that: nhet mot so may vao cot ay se lam moi loi gui thu ve
        // sau gui vao hu khong, va lam bao cao "bao nhieu nguoi co email" noi doi.
        //
        // verifiedCaller = FALSE: danh tinh o nhanh nay do nguoi goi TU KHAI. Redeemer vi vay
        // khong duoc lam tuoi ho so cua mot dong app_user da co — xem ClanInviteRedeemer#redeem.
        AppUser user = redeemer.redeem(code.id(), account.subject(), login.emailOrNull(),
                command.displayName() == null ? login.value() : command.displayName(),
                clientKeyOf(clientId), false);

        throttle.recordClanSuccess(clientId, true);
        log.info("Dang ky bang ma dong ho {}: app_user {} (Keycloak vua tao: {}), phat lien ket dat"
                + " mat khau: {}", code.id(), user.id(), account.justCreated(), link.isPresent());
        return new ClanRegistration(user, link, code.id());
    }

    /**
     * Người gọi <b>đã có token</b>: danh tính lấy từ token, không hỏi email, không tạo gì ở Keycloak.
     *
     * <h2>Đây không phải một ca hiếm — nó là lối của mọi người đăng nhập Google/Zalo</h2>
     * Realm cho phép social login, nên một người con cháu hoàn toàn có thể đăng nhập bằng Google
     * trước khi có mã mời: lúc ấy họ đã có tài khoản Keycloak, đã có token, nhưng <b>chưa có dòng
     * {@code app_user}</b> và chưa thấy được gì ngoài dữ liệu công khai. Mã dòng họ là thứ mở cửa
     * cho họ, và ở nhánh này nó chỉ phải làm đúng một việc: tiêu một lượt rồi dựng
     * {@code app_user}.
     *
     * <p><b>Không phát liên kết đặt mật khẩu</b> — họ vừa đăng nhập được thì hiển nhiên đã có cách
     * đăng nhập, và phát liên kết cho một tài khoản đã dùng được là mở một lối đổi mật khẩu cho bất
     * kỳ ai cầm mã dòng họ. Mà mã ấy thì cả họ đang cầm.</p>
     *
     * <p>Cùng khuôn với {@code InvitationService#acceptWithToken}, và cố ý giống: hai luồng vào hệ
     * thống nên xử ca "đã có token" theo cùng một cách, nếu không sẽ có một luồng quên mất nó.</p>
     */
    private ClanRegistration dangKyBangTokenSanCo(ClanInviteCode code, CurrentUser caller,
                                                  String clientId) {
        // verifiedCaller = TRUE: danh tinh nay do Keycloak chung nhan trong token, khong phai do
        // nguoi goi tu khai — nen lam tuoi ho so tu token la dung, va la ca duy nhat dung.
        AppUser user = redeemer.redeem(code.id(), caller.keycloakSub(), caller.email(),
                caller.username(), clientKeyOf(clientId), true);
        throttle.recordClanSuccess(clientId, true);
        log.info("Dang ky bang ma dong ho {} voi token san co: app_user {}", code.id(), user.id());
        return new ClanRegistration(user, Optional.empty(), code.id());
    }

    // -------------------------------------------------------------------------------------
    // Nội bộ
    // -------------------------------------------------------------------------------------

    /**
     * Băm định danh người gọi cho nhật ký "ai đã dùng mã nào".
     *
     * <p>Cùng phép băm với {@link InviteThrottle}, nên hai bảng nói về cùng một người gọi bằng cùng
     * một khoá — điều kiện để nối được "IP này đã dò 40 lần" với "IP này đã dùng mã X".</p>
     */
    private static String clientKeyOf(String clientId) {
        return InvitationCode.sha256Hex(clientId == null || clientId.isBlank()
                ? "unknown-client" : clientId.trim());
    }

    /**
     * Tra mã và khẳng định nó còn dùng được, đồng thời đếm lần thất bại.
     *
     * <p><b>Mã không tồn tại trả {@code 404 NOT_FOUND}</b>, cùng một câu trả lời với mã sai định
     * dạng. Ba ca còn lại (hết hạn · thu hồi · hết lượt) được nói thẳng vì người cầm mã có một mã
     * thật và cần biết lối đi tiếp. Phân biệt "mã sai" với "mã có thật" bằng một mã lỗi riêng sẽ
     * biến endpoint này thành máy xác nhận mã tồn tại — và với một mã <b>dùng nhiều lần, sống suốt
     * hạn</b> thì điều đó đắt hơn nhiều so với mã cá nhân.</p>
     */
    private ClanInviteCode resolveUsable(String rawCode, String clientId) {
        String hash;
        try {
            hash = InvitationCode.hash(rawCode);
        } catch (IllegalArgumentException ex) {
            // Ma sai dinh dang van la mot lan thu: khong dem thi bo dem tro thanh tu chon.
            throttle.recordFailure(clientId);
            throw khongKhopMaNao();
        }
        Optional<ClanInviteCode> found = codes.byCodeHash(hash);
        if (found.isEmpty()) {
            throttle.recordFailure(clientId);
            throw khongKhopMaNao();
        }
        ClanInviteCode code = found.get();
        ClanInviteUsability usability = code.usabilityAt(Instant.now());
        if (!usability.isUsable()) {
            throttle.recordFailure(clientId);
            throw new ClanInviteNotUsableException(usability);
        }
        return code;
    }

    /** Hạn hiệu lực: mặc định 30 ngày, rút ngắn được, nhưng không kéo dài quá {@value #MAX_TTL_DAYS}. */
    private Duration ttlOf(IssueClanInviteCommand command) {
        int days = command == null || command.ttlDays() == null ? defaultTtlDays : command.ttlDays();
        if (days < 1 || days > MAX_TTL_DAYS) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Han ma moi dong ho phai tu 1 den " + MAX_TTL_DAYS + " ngay");
        }
        return Duration.ofDays(days);
    }

    private static NotFoundException khongKhopMaNao() {
        return new NotFoundException(MembershipProblemCodes.NOT_FOUND,
                "Khong tim thay ma moi khop ma nay");
    }
}
