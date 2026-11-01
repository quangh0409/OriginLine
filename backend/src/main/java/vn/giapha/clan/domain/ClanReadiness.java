package vn.giapha.clan.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Dòng họ này còn thiếu bước nào" — luật thuần, tính từ một bó sự kiện đã đếm sẵn.
 *
 * <h2>Vì sao luật này ở domain, tách khỏi phép đếm</h2>
 * Phép đếm đi qua ba context khác ({@code genealogy}, {@code kinship}, {@code membership}); luật
 * "bước nào xong, câu nhắc nói gì" thì không phụ thuộc context nào. Tách ra để test được từng tổ
 * hợp trạng thái mà không cần CSDL — và để câu nhắc tiếng Việt chỉ có <b>một</b> bản.
 *
 * <h2>Năm bước, theo đúng thứ tự một dòng họ đi qua</h2>
 * <ol>
 *   <li>{@code profile} — hồ sơ đã lưu <b>và</b> chi gốc đã có <b>và</b> bộ luật danh xưng tầng
 *       {@code CLAN} đã dựng. Ba điều một lượt lưu hồ sơ làm ra cùng lúc; thiếu một nghĩa là hồ sơ
 *       có từ trước V23 (tự điền) mà chưa ai bấm Lưu.</li>
 *   <li>{@code council} — có ít nhất một tài khoản Hội đồng Tộc biểu. Không có ai thì không ai
 *       duyệt được đơn tự nhận, bài viết, luật danh xưng.</li>
 *   <li>{@code import} — phả đã có nhân khẩu.</li>
 *   <li>{@code invite} — có mã mời dòng họ còn hiệu lực để con cháu tự vào.</li>
 *   <li>{@code done} — cả bốn bước trên.</li>
 * </ol>
 * Bước sau <b>không</b> bị khoá bởi bước trước: quản trị có thể tạo tài khoản Hội đồng trước khi
 * lưu hồ sơ. Thứ tự chỉ là thứ tự gợi ý.
 */
public final class ClanReadiness {

    /** Khoá bước — khớp enum {@code ClanReadinessStepKey} của hợp đồng (chữ thường). */
    public enum StepKey {
        PROFILE, COUNCIL, IMPORT, INVITE, DONE;

        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Một bước: đã xong chưa, và câu tiếng Việt có dấu nói việc cần làm tiếp. */
    public record Step(StepKey key, boolean done, String hint) {
    }

    /**
     * Bó sự kiện đã đếm.
     *
     * @param profileSaved      có dòng {@code clan_profile}
     * @param rootBranchPresent chi gốc {@code DONG_HO} ứng với nhãn gốc đang tồn tại
     * @param ruleSetPresent    bộ luật danh xưng tầng {@code CLAN} của chi gốc đang tồn tại
     * @param councilCount      số tài khoản đang giữ vai {@code COUNCIL}
     * @param personCount       số nhân khẩu chưa xoá mềm
     * @param activeInvites     số mã mời dòng họ còn dùng được
     */
    public record Facts(boolean profileSaved, boolean rootBranchPresent, boolean ruleSetPresent,
                        int councilCount, long personCount, int activeInvites) {
    }

    private final Facts facts;

    private ClanReadiness(Facts facts) {
        this.facts = facts;
    }

    public static ClanReadiness of(Facts facts) {
        return new ClanReadiness(facts);
    }

    public boolean profileDone() {
        return facts.profileSaved() && facts.rootBranchPresent() && facts.ruleSetPresent();
    }

    public boolean councilDone() {
        return facts.councilCount() > 0;
    }

    public boolean importDone() {
        return facts.personCount() > 0;
    }

    public boolean inviteDone() {
        return facts.activeInvites() > 0;
    }

    public boolean allDone() {
        return profileDone() && councilDone() && importDone() && inviteDone();
    }

    /** Năm bước theo thứ tự cố định — giao diện vẽ theo đúng thứ tự này. */
    public List<Step> steps() {
        List<Step> steps = new ArrayList<>(5);
        steps.add(new Step(StepKey.PROFILE, profileDone(), profileHint()));
        steps.add(new Step(StepKey.COUNCIL, councilDone(), councilDone()
                ? "Đã có " + facts.councilCount() + " tài khoản Hội đồng Tộc biểu."
                : "Tạo ít nhất một tài khoản Hội đồng Tộc biểu để có người duyệt đơn tự nhận,"
                        + " bài viết và luật danh xưng của dòng họ."));
        steps.add(new Step(StepKey.IMPORT, importDone(), importDone()
                ? "Phả đã có " + facts.personCount() + " nhân khẩu."
                : "Nhập danh sách nhân khẩu từ tệp Excel theo mẫu của từng chi (mục Nhập liệu)."));
        steps.add(new Step(StepKey.INVITE, inviteDone(), inviteDone()
                ? "Đang có " + facts.activeInvites() + " mã mời dòng họ còn hiệu lực."
                : "Phát mã mời dòng họ để con cháu tự đăng ký tài khoản và tìm mình trong phả."));
        steps.add(new Step(StepKey.DONE, allDone(), allDone()
                ? "Dòng họ đã sẵn sàng sử dụng."
                : "Còn " + remaining() + " bước chưa xong — làm lần lượt các bước phía trên."));
        return List.copyOf(steps);
    }

    private int remaining() {
        int left = 0;
        left += profileDone() ? 0 : 1;
        left += councilDone() ? 0 : 1;
        left += importDone() ? 0 : 1;
        left += inviteDone() ? 0 : 1;
        return left;
    }

    private String profileHint() {
        if (!facts.profileSaved()) {
            return "Nhập tên dòng họ, vùng miền và nhãn gốc rồi bấm Lưu để khởi tạo dòng họ.";
        }
        if (!facts.rootBranchPresent() || !facts.ruleSetPresent()) {
            // Ca của hồ sơ tự điền ở V23: hàng đã có nhưng chưa ai bấm Lưu nên chưa có bộ luật
            // tầng CLAN. Nói đúng việc cần làm, không nói "chưa khởi tạo" — nói thế là nói dối.
            return "Mở hồ sơ dòng họ, kiểm lại vùng miền rồi bấm Lưu một lần để hệ thống dựng"
                    + " chi gốc và bộ luật danh xưng của dòng họ.";
        }
        return "Hồ sơ dòng họ đã khởi tạo xong; có thể sửa bất cứ lúc nào.";
    }
}
