package vn.giapha.membership.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.giapha.membership.domain.BranchSummary;
import vn.giapha.membership.domain.ClaimDuplicateSuspect;
import vn.giapha.membership.domain.PersonClaim;
import vn.giapha.membership.domain.PersonClaimKind;
import vn.giapha.shared.vo.Gender;

/**
 * Một đơn tự nhận nhìn từ màn của người gửi và từ hàng chờ của Trưởng chi.
 *
 * <h2>{@link #phone()} và {@link #introduction()} chỉ đi tới hai loại người</h2>
 * Người gửi (xem đơn của chính mình) và người duyệt <b>đúng phạm vi chi</b>. Không có lối thứ ba:
 * {@code PersonClaimService} kiểm điều đó ở mọi phương thức đọc, và phép lọc phạm vi chạy ngay
 * trong SQL nên hàng đợi của chi khác không bao giờ đi vào bộ nhớ tiến trình.
 *
 * <h2>HAI trường chỉ dành cho người duyệt: {@link #duplicateSuspects()} và
 * {@link #competingClaimIds()}</h2>
 * Bản đọc của <b>người gửi</b> luôn có {@code duplicateSuspects = null} và
 * {@code competingClaimIds} rỗng, bất kể đơn đã được quét hay chưa — nên trên màn của người gửi,
 * ba trạng thái nói ở dưới <b>không áp dụng</b>: sự vắng mặt ở đó nghĩa là "không phải việc của
 * bạn", không phải "hệ thống chưa kiểm".
 *
 * <p>Lý do: {@code ClaimDuplicateSuspect} không mang tên hay năm sinh, nhưng {@code signals} thì
 * mang ({@code NAM_SINH_KHOP}, {@code CUNG_CHI}, {@code CUNG_NGUYEN_QUAN}, {@code DOI_*}). Một tài
 * khoản tự đăng ký chưa được duyệt đọc được chúng là đọc được dữ liệu Tầng 2 của một người đang
 * sống qua một kênh phụ. Xem {@code PersonClaimService#toRequesterView}.</p>
 *
 * <h2>Không mang tên hay năm sinh của nhân khẩu được nhận</h2>
 * Chỉ {@link #personId()}. Nhân khẩu ấy có thể là một người <b>còn sống</b>, và ai được xem gì về
 * họ là câu hỏi của bộ lọc phân tầng riêng tư — thứ chạy ở
 * {@code GET /api/v1/persons/&#123;id&#125;}, không phải ở đây. Giao diện cầm khoá rồi gọi sang;
 * chép sẵn một cái tên vào đây là dựng một lối đọc thứ hai không có bộ lọc nào, và nó sẽ lọc theo
 * quyền của <i>người gửi đơn</i> chứ không phải người đang đọc.
 *
 * @param targetBranch         chi đích, kèm <b>chức danh</b> ("Trưởng Chi Giáp") — câu trả lời cho
 *                             "đang chờ ai". Là một <i>vai</i>, không phải một con người: tên và số
 *                             điện thoại của Trưởng chi <b>không</b> đi ra màn này
 * @param requesterDisplayName tên hiển thị của <b>tài khoản</b> gửi đơn (tự khai lúc đăng ký), để
 *                             Trưởng chi đối chiếu với phần tự giới thiệu. Không phải tên trong phả
 * @param attemptNo            đơn thứ mấy của người này. Lần thứ nhất là chuyện thường; lần thứ tư
 *                             trên bốn nhân khẩu khác nhau là một tín hiệu hoàn toàn khác
 * @param duplicateSuspects    ràng buộc 3 của §1.5 — ảnh chụp lúc gửi, <b>chỉ có trên bản đọc của
 *                             người duyệt</b>. Ở đó: {@code null} = chưa quét bao giờ (đơn
 *                             {@code EXISTING} không cần quét); danh sách rỗng = <b>đã quét, không
 *                             nghi ai</b>. Hai câu khác nhau trên màn hình, và gộp chúng lại sẽ nói
 *                             với Trưởng chi rằng hệ thống đã kiểm trong khi nó chưa kiểm
 * @param competingClaimIds    các đơn <b>khác đang chờ</b> cùng trỏ vào nhân khẩu này — cũng chỉ có
 *                             trên bản đọc của người duyệt. Xem {@code PersonClaimService} về việc
 *                             vì sao trường này là một chốt nghiệp vụ chứ không phải một tiện ích
 *                             hiển thị
 * @param createdPersonId      nhân khẩu được tạo <b>lúc duyệt</b> một đơn {@code NEW_PERSON}; rỗng
 *                             ở mọi đơn bị từ chối, và đó là bằng chứng không có node ma nào
 */
@SuppressWarnings("java:S107")
public record PersonClaimView(UUID id, String kind, String status,
                              UUID requestedBy, String requesterDisplayName, int attemptNo,
                              UUID personId,
                              UUID relativePersonId, String relativeKind,
                              String declaredName, Integer declaredBirthYear, Gender declaredGender,
                              UUID targetBranchId, BranchSummary targetBranch,
                              String phone, String introduction,
                              List<ClaimDuplicateSuspect> duplicateSuspects,
                              List<UUID> competingClaimIds,
                              UUID reviewerId, String reviewNote, Instant reviewedAt,
                              UUID createdPersonId, Instant createdAt, long version) {

    public PersonClaimView {
        competingClaimIds = competingClaimIds == null ? List.of() : List.copyOf(competingClaimIds);
    }

    /**
     * Dựng từ một đơn, với các khối tra cứu đã được {@code PersonClaimService} chuẩn bị sẵn.
     *
     * <p>{@code duplicateSuspects} <b>vắng mặt</b> với đơn {@code EXISTING}: bộ dò trùng không chạy
     * cho loại đơn ấy (người được nhận vốn đã ở trong phả, không có gì để so trùng), nên một danh
     * sách rỗng ở đó sẽ là một lời nói dối — "đã quét, không nghi ai".</p>
     *
     * @param forReviewer bản đọc này đi tới <b>người duyệt</b> hay tới người gửi. Sai ở đây là làm
     *                    rò dữ liệu Tầng 2, nên tham số <b>không có giá trị mặc định</b>: nơi gọi
     *                    buộc phải nói ra mình đang dựng bản nào
     */
    public static PersonClaimView from(PersonClaim claim, String requesterDisplayName,
                                       int attemptNo, BranchSummary targetBranch,
                                       List<UUID> competingClaimIds, boolean forReviewer) {
        List<ClaimDuplicateSuspect> suspects =
                forReviewer && claim.kind() == PersonClaimKind.NEW_PERSON
                        ? claim.screening() : null;
        return new PersonClaimView(claim.id(), claim.kind().name(), claim.status().name(),
                claim.requestedBy(), requesterDisplayName, attemptNo, claim.personId(),
                claim.relativePersonId(),
                claim.relativeKind() == null ? null : claim.relativeKind().name(),
                claim.declaredName(), claim.declaredBirthYear(), claim.declaredGender(),
                claim.targetBranchId(), targetBranch, claim.phone(), claim.introduction(),
                suspects, competingClaimIds, claim.reviewerId(), claim.reviewNote(),
                claim.reviewedAt(), claim.createdPersonId(), claim.createdAt(), claim.version());
    }
}
