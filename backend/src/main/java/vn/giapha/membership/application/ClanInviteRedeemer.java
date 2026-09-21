package vn.giapha.membership.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.ClanInviteCode;
import vn.giapha.membership.domain.ClanInviteUsability;
import vn.giapha.membership.domain.port.ClanInviteCodeRepository;
import vn.giapha.membership.domain.port.ClanInviteRedemptionRepository;

/**
 * <b>Bước duy nhất không đảo ngược được của luồng đăng ký bằng mã dòng họ</b>, gói gọn trong một
 * transaction Postgres: tiêu một lượt của mã · ghi vết "ai đã dùng mã nào" · dựng dòng
 * {@code app_user}. Cùng lúc, hoặc không làm gì cả.
 *
 * <h2>Vì sao tách khỏi {@link ClanInviteService}</h2>
 * Y hệt lý do của {@link InvitationLinker}: việc đăng ký chạm vào <b>hai hệ thống</b> — Keycloak và
 * Postgres — mà không có transaction chung. Giữ một connection Postgres mở trong lúc chờ mạng là
 * cách chắc chắn để cạn pool vào ngày giỗ họ, khi cả họ cùng bấm đăng ký. Mà một phương thức
 * {@code @Transactional} thì không có chỗ nào "ở ngoài": ranh giới là cả phương thức. Vì vậy phần
 * CSDL tách sang một bean riêng, và ranh giới transaction trở thành đúng cái người đọc nhìn thấy.
 *
 * <h2>Thứ tự bên trong: TIÊU LƯỢT TRƯỚC, dựng tài khoản SAU</h2>
 * Ngược với {@link InvitationLinker}, nơi mã được đốt <i>sau cùng</i>. Lý do: ở đó "đốt mã" là một
 * bất biến một-lần và phải là dòng cuối để không ai vô tình chèn lời gọi vào giữa. Ở đây phép tiêu
 * lượt vừa là <b>bộ đếm</b> vừa là <b>phép kiểm cuối cùng</b> — nó chạy trong SQL với đủ điều kiện
 * (còn hạn · chưa thu hồi · chưa chạm trần) nên nó phải chạy <i>trước khi</i> ta dựng bất cứ thứ gì
 * dựa trên giả định "mã còn dùng được". Đọc-kiểm-rồi-mới-tiêu thì giữa hai thời điểm ấy Hội đồng có
 * thể vừa thu hồi mã, và trần {@code max_uses} vượt được bởi đúng số người bấm đồng thời.
 *
 * <p>Cả ba thao tác nằm trong một transaction nên "tiêu trước" không đánh đổi gì: hỏng ở bước sau
 * thì lượt vừa tiêu cũng cuộn lại. Bất biến thật sự cần giữ là <b>bộ đếm không bao giờ đếm thiếu
 * so với số tài khoản đã vào</b> — và nó được giữ bởi việc cả ba cùng một transaction, không bởi
 * thứ tự dòng lệnh.</p>
 *
 * <h2>Bấm hai lần không đếm thành hai lượt</h2>
 * {@code ux_clan_redemption_once} chặn cặp (mã, tài khoản) trùng. Nếu người ấy đã dùng mã này rồi —
 * bấm lại, mạng chập chờn, tải lại trang — thì lượt <b>không</b> được tiêu lần nữa. Không có phép
 * kiểm này thì bộ đếm phồng lên vì những lần bấm lại vô hại, và Hội đồng sẽ thu hồi một mã lành vì
 * tưởng nó đã rò.
 */
@Service
public class ClanInviteRedeemer {

    private static final Logger log = LoggerFactory.getLogger(ClanInviteRedeemer.class);

    private static final String ENTITY = "ClanInviteCode";
    private static final String ENTITY_APP_USER = "AppUser";

    private final ClanInviteCodeRepository codes;
    private final ClanInviteRedemptionRepository redemptions;
    private final AppUserProvisioningService provisioning;
    private final AuditTrailService audit;

    public ClanInviteRedeemer(ClanInviteCodeRepository codes,
                              ClanInviteRedemptionRepository redemptions,
                              AppUserProvisioningService provisioning,
                              AuditTrailService audit) {
        this.codes = codes;
        this.redemptions = redemptions;
        this.provisioning = provisioning;
        this.audit = audit;
    }

    /**
     * Tiêu một lượt của mã và dựng tài khoản ứng với {@code keycloakSub}.
     *
     * <h2>{@code verifiedCaller} — ai nói rằng {@code keycloakSub} là của người đang gọi</h2>
     * {@code true} khi {@code sub} đến từ một <b>token</b> Keycloak đã xác thực. {@code false} khi
     * nó đến từ một định danh người gọi <b>tự khai</b> ở ô đăng ký (lối không cần đăng nhập). Ở
     * nhánh tự khai, lớp này <b>không được làm tươi hồ sơ</b> của một dòng {@code app_user} đã có:
     * làm tươi nghĩa là ghi {@code display_name}/{@code email} người lạ gõ vào lên hàng của một
     * thành viên thật — và {@code display_name} ấy chính là thứ Trưởng chi đọc khi duyệt đơn tự
     * nhận. Xem {@link AppUserProvisioningService#ensureForUnverified}.
     *
     * <p>Đây là <b>lớp chặn thứ hai</b>. Lớp thứ nhất ở {@code ClanInviteService#register}: định
     * danh đã có chủ thì lượt đăng ký bị từ chối trước khi tới đây. Giữ cả hai vì lớp thứ nhất là
     * một câu {@code if} trong một phương thức dài, còn cái nó bảo vệ là quyền đọc dữ liệu Tầng 3
     * của một người khác.</p>
     *
     * @param codeId        mã đã được xác nhận còn dùng được ở ngoài transaction này
     * @param keycloakSub   {@code sub} của tài khoản vừa lập hoặc vừa tìm thấy ở Keycloak
     * @param email         địa chỉ thư người dùng tự khai
     * @param displayName   tên hiển thị tự khai
     * @param clientKeyHash băm định danh người gọi, để truy nguồn khi mã rò
     * @param verifiedCaller {@code sub} đến từ token đã xác thực, không phải từ lời tự khai
     * @return tài khoản ở trạng thái {@code PENDING}, <b>chưa</b> gắn nhân khẩu nào
     */
    @Transactional
    public AppUser redeem(UUID codeId, String keycloakSub, String email, String displayName,
                          String clientKeyHash, boolean verifiedCaller) {
        Instant now = Instant.now();

        // Doc lai TRONG transaction: giua luc ClanInviteService tra ma va luc nay co vai luot HTTP
        // toi Keycloak, va trong khoang do Hoi dong co the vua thu hoi ma.
        ClanInviteCode code = codes.byId(codeId)
                .orElseThrow(() -> new vn.giapha.shared.exception.NotFoundException(
                        MembershipProblemCodes.NOT_FOUND, "Khong tim thay ma moi khop ma nay"));
        ClanInviteUsability usability = code.usabilityAt(now);
        if (!usability.isUsable()) {
            throw new ClanInviteNotUsableException(usability);
        }

        AppUser account = verifiedCaller
                ? provisioning.ensureFor(keycloakSub, email, displayName)
                : provisioning.ensureForUnverified(keycloakSub, email, displayName);

        // MOT TAI KHOAN DEM MOT LUOT TREN MOT MA. Ghi vet truoc, roi moi tang bo dem: neu nguoi nay
        // da dung ma nay roi thi khong tang nua.
        boolean moi = redemptions.record(code.id(), account.id(), clientKeyHash, now);
        if (moi) {
            // CHOT 3 — tang bo dem nguyen tu, co dieu kien. Xem ClanInviteCodeRepository#tryConsume.
            if (!codes.tryConsume(code.id(), now)) {
                // Ma vua het han / vua bi thu hoi / vua cham tran giua hai cau lenh. Nem de ca
                // transaction cuon lai: dong redemption vua ghi cung bien mat, nen bo dem va nhat
                // ky khong bao gio lech nhau.
                throw new ClanInviteNotUsableException(
                        codes.byId(code.id()).map(c -> c.usabilityAt(now))
                                .orElse(ClanInviteUsability.REVOKED));
            }
            audit.record(ENTITY, code.id().toString(), AuditAction.UPDATE, null,
                    Map.of("useCount", String.valueOf(code.useCount() + 1),
                            "redeemedBy", String.valueOf(account.id())),
                    List.of("useCount"), "Dung ma moi dong ho de dang ky tai khoan");
        } else {
            log.info("App_user {} da dung ma dong ho {} truoc do — khong dem them mot luot",
                    account.id(), code.id());
        }

        audit.record(ENTITY_APP_USER, account.id().toString(), AuditAction.CREATE, null,
                Map.of("status", account.status().name(),
                        "clanInviteId", code.id().toString()),
                List.of("status"), "Tu dang ky bang ma moi dong ho, CHUA gan nhan khau");

        log.info("App_user {} dang ky bang ma dong ho {} (luot moi: {})",
                account.id(), code.id(), moi);
        return account;
    }
}
