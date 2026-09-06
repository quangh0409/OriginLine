package vn.giapha.kinship.application;

import java.util.List;
import vn.giapha.kinship.domain.KinshipRuleSet;
import vn.giapha.shared.vo.PersonId;

/**
 * Sinh khoá cache Redis cho kết quả tra danh xưng (vùng cache {@code kinship}, xem
 * {@code RedisConfig}).
 *
 * <p>Hình dạng khoá: {@code v1|<vân tay bộ luật>|<id nhỏ>|<id lớn>|<F|R>}</p>
 *
 * <ul>
 *   <li><b>Chuẩn hoá thứ tự cặp</b> — hai id luôn sắp tăng dần, chiều hỏi nằm ở hậu tố
 *       {@code F}/{@code R}. Nhờ vậy cặp (A,B) và (B,A) dùng chung tiền tố, xoá theo cặp chỉ cần
 *       một mẫu. Không gộp hai chiều vào một mục vì chiều nào cũng có {@code egoSelfTerm},
 *       {@code facts} và <b>bộ luật của riêng ego</b> — hai người khác chi có thể ra hai bộ luật
 *       khác nhau, gộp lại là trả sai danh xưng.</li>
 *   <li><b>Vân tay bộ luật</b> — băm của toàn bộ chuỗi kế thừa {@code (id:version)} từ DEFAULT tới
 *       bộ lá. Sửa luật ở <i>bất kỳ</i> cấp nào cũng đổi vân tay, nên mọi mục cache cũ lập tức
 *       thành không-thể-với-tới. Chỉ lấy {@code version} của bộ lá là chưa đủ: sửa bộ DEFAULT sẽ
 *       không đụng tới version của bộ BRANCH và cache cũ sẽ sống sót với danh xưng sai.</li>
 * </ul>
 *
 * <p>Vân tay là lớp phòng thủ <b>bị động</b>. {@link RuleAdminService} vẫn xoá cache một cách chủ
 * động sau mỗi lần ghi — hai lớp bổ trợ nhau, vì vân tay không cứu được trường hợp bộ luật bị sửa
 * thẳng dưới CSDL.</p>
 */
public final class KinshipCacheKeys {

    /** Đổi khi cấu trúc {@link KinshipQueryResult} thay đổi, để bản cache cũ không giải mã nhầm. */
    private static final String SCHEMA_VERSION = "v1";

    private KinshipCacheKeys() {
    }

    public static String pairKey(PersonId ego, PersonId alter, KinshipRuleSet ruleSet) {
        String a = ego.value().toString();
        String b = alter.value().toString();
        boolean forward = a.compareTo(b) <= 0;
        String low = forward ? a : b;
        String high = forward ? b : a;
        return SCHEMA_VERSION + '|' + fingerprint(ruleSet) + '|' + low + '|' + high + '|'
                + (forward ? 'F' : 'R');
    }

    /**
     * Vân tay của bộ luật đang hiệu lực: {@code id:version} của từng bộ trong chuỗi kế thừa, băm
     * lại cho khoá ngắn.
     */
    public static String fingerprint(KinshipRuleSet ruleSet) {
        if (ruleSet == null) {
            return "norules";
        }
        StringBuilder raw = new StringBuilder();
        List<KinshipRuleSet> chain = ruleSet.chain();
        for (KinshipRuleSet set : chain) {
            raw.append(set.id()).append(':').append(set.version()).append(';');
        }
        return Integer.toUnsignedString(raw.toString().hashCode(), 36);
    }
}
