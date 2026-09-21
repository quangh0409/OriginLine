package vn.giapha.membership.application;

import vn.giapha.membership.domain.ClanInviteUsability;
import vn.giapha.shared.exception.DomainException;

/**
 * Mã khớp một mã mời dòng họ <b>có thật</b>, nhưng mã ấy đã hết hạn / bị thu hồi / hết lượt.
 *
 * <h2>Hai mã lỗi dùng lại, một mã lỗi mới</h2>
 * "Hết hạn" và "bị thu hồi" trả về đúng {@link MembershipProblemCodes#INVITATION_EXPIRED} và
 * {@link MembershipProblemCodes#INVITATION_REVOKED} của luồng mã cá nhân. Người dùng không phân
 * biệt được — và không cần phân biệt — mã cá nhân với mã dòng họ: họ chỉ cầm một dãy mười ký tự và
 * gõ nó vào một ô. Hai tập mã lỗi song song cho cùng một câu trả lời chỉ bắt giao diện viết hai
 * nhánh giống hệt nhau.
 *
 * <p>{@link ClanInviteUsability#EXHAUSTED} thì khác thật và có mã riêng
 * ({@link MembershipProblemCodes#CLAN_INVITE_EXHAUSTED}): mã vẫn còn hạn, vẫn chưa bị thu hồi, chỉ
 * là Hội đồng đã đặt trần lượt dùng và trần ấy đầy. Lối đi tiếp là xin Hội đồng nâng trần hoặc
 * phát mã mới — khác hẳn lối của một mã quá hạn.</p>
 *
 * <p>Ca thứ tư — mã <b>không khớp</b> mã nào — không đi qua lớp này: nó là
 * {@code NotFoundException} với {@code NOT_FOUND} sẵn có. Phân biệt "mã sai" với "mã hết hạn" bằng
 * một mã lỗi riêng sẽ biến endpoint tra mã thành máy xác nhận mã tồn tại, và với một mã <b>dùng
 * nhiều lần</b> thì điều đó đắt hơn nhiều so với mã cá nhân.</p>
 */
public class ClanInviteNotUsableException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final transient ClanInviteUsability usability;

    public ClanInviteNotUsableException(ClanInviteUsability usability) {
        super(codeOf(usability), messageOf(usability));
        this.usability = usability;
    }

    public ClanInviteUsability usability() {
        return usability;
    }

    private static String codeOf(ClanInviteUsability usability) {
        return switch (usability) {
            case EXPIRED -> MembershipProblemCodes.INVITATION_EXPIRED;
            case REVOKED -> MembershipProblemCodes.INVITATION_REVOKED;
            case EXHAUSTED -> MembershipProblemCodes.CLAN_INVITE_EXHAUSTED;
            case USABLE -> MembershipProblemCodes.VALIDATION_FAILED;
        };
    }

    private static String messageOf(ClanInviteUsability usability) {
        return switch (usability) {
            case EXPIRED -> "Ma moi cua dong ho da het han";
            case REVOKED -> "Ma moi cua dong ho da bi thu hoi";
            case EXHAUSTED -> "Ma moi cua dong ho da dung het so luot cho phep";
            case USABLE -> "Ma moi khong dung duoc";
        };
    }
}
