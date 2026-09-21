package vn.giapha.membership.application;

import java.util.List;
import java.util.UUID;
import vn.giapha.shared.vo.Gender;

/**
 * Dò trùng cho một đơn "tôi chưa có trong phả" — <b>ràng buộc 3 của design 07 §1.5</b>.
 *
 * <h2>Vì sao cổng này nằm ở {@code application} chứ không ở {@code domain.port}</h2>
 * Đây <b>không</b> phải một sơ suất về phân tầng mà là hệ quả trực tiếp của hình dạng đồ thị module.
 * {@code genealogy} <i>đã</i> phụ thuộc vào {@code membership}
 * ({@code CallerIdentityJdbcAdapter} → {@code MemberScopeService}, và
 * {@code ChangeRequestApplier} nghe {@code membership.domain.event}). Vì vậy <b>mọi</b> phụ thuộc
 * theo chiều {@code membership → genealogy} đều tạo ra một chu trình và làm
 * {@code ModularityTests} đỏ — đã kiểm chứng, không phải suy đoán.
 *
 * <p>Nên lối duy nhất còn lại là <b>đảo phụ thuộc</b>: {@code membership} khai báo cổng, một bên
 * <i>khác</i> hiện thực nó. Bên hiện thực phải nhìn thấy cổng, mà {@code membership.domain.port}
 * <b>không</b> mang {@code @NamedInterface} còn {@code membership.application} thì có — nên cổng
 * đứng ở đây. Đặt nó ở {@code domain.port} rồi gắn nhãn cho nó là đưa siêu dữ liệu framework vào
 * tầng domain, đúng thứ mà kỷ luật "domain là POJO thuần" của dự án cấm.</p>
 *
 * <p>Hiện thực ở {@code genealogy.infrastructure.membership} — cạnh {@code genealogy → membership}
 * vốn đã có nên đặt ở đó không thêm một phụ thuộc nào. Đọc package-info của gói ấy để biết vì sao
 * hai lối tắt thông thường (SQL thẳng, chèn nhân khẩu thủ công) đều không dùng được.</p>
 *
 * <h2>Cổng này KHÔNG chấm điểm, và tuyệt đối không được chấm điểm</h2>
 * Hiện thực chỉ dựng tham số, gọi facade {@code PersonScreeningService} của {@code genealogy}
 * (named interface {@code "do-trung"}), rồi dịch kết quả.
 *
 * <p><b>Đừng viết bộ chấm điểm thứ hai</b>, kể cả một câu SQL "cho nhanh". Bộ dò đã có mang một bộ
 * trọng số nhiều tín hiệu với ngưỡng được chọn để chịu được hai tập quán đặt tên của người Việt: cả
 * một đời mang chung chữ đệm (trùng tên <i>và</i> trùng đời là <b>bình thường</b>, không đủ để
 * kêu), và tục đặt tên con theo tên người anh đã mất (cùng cha cùng tên thì <b>có</b> kêu). Một bản
 * thứ hai sẽ lệch, và triệu chứng là <b>màn duyệt đơn bảo trùng còn màn thêm người bảo không</b> —
 * không ai truy ra được vì sao. Thà không có cảnh báo còn hơn có hai cảnh báo mâu thuẫn nhau.
 */
public interface ClaimScreeningPort {

    /**
     * Quét nghi trùng cho một người <b>chưa có trong phả</b>.
     *
     * @param ref       mã tham chiếu do bên gọi đặt (id của đơn), để ghép kết quả về đúng đơn
     * @param fullName  họ tên tự khai
     * @param birthYear năm sinh tự khai; {@code null} khi không khai
     * @param gender    giới tính tự khai; {@code null} khi không khai
     * @param branchId  chi của <b>người thân được chỉ ra</b> — người mới chưa thuộc chi nào, nhưng
     *                  bộ dò cộng điểm cho "cùng chi" và đó là phỏng đoán đúng nhất ta có
     * @return đã sắp giảm dần theo điểm; <b>rỗng là kết quả mong đợi</b> với tuyệt đại đa số đơn,
     *         kể cả đơn của người trùng tên với ai đó trong họ
     */
    List<Suspect> scanDuplicates(String ref, String fullName, Integer birthYear, Gender gender,
                                 UUID branchId);

    /**
     * Một nhân khẩu đã có trong phả bị nghi là chính người đang khai.
     *
     * <h2>Bốn trường của bộ dò bị bỏ đi có chủ ý</h2>
     * Bộ dò bên {@code genealogy} trả về cả {@code displayName}, {@code generation},
     * {@code branchId} và {@code matchedName} của bên bị nghi. Kiểu này <b>không</b> chở chúng, vì
     * javadoc của {@code DuplicateMatch} đã chốt ranh giới cho mọi kênh đi ra khỏi tiến trình:
     * <i>được phép nói trường nào của chính người gọi vừa nhập đã khớp, không bao giờ nói một giá
     * trị đọc từ phả</i>. Bên bị nghi hoàn toàn có thể là một người <b>còn sống ở một chi khác</b>
     * mà người đọc không được xem tên huý hay năm sinh.
     *
     * <p>Điều đó đặc biệt quan trọng ở đây vì kết quả được <b>lưu</b> vào
     * {@code person_claim.screening}: một giá trị đọc từ phả lọt vào JSONB ấy là lọt vĩnh viễn, và
     * nó sẽ đi qua mọi lần đọc đơn về sau mà không còn bộ lọc nào chạy trên nó. Giao diện cầm
     * {@code personId} rồi gọi {@code GET /api/v1/persons/&#123;id&#125;}, nơi bộ lọc phân tầng
     * riêng tư thật sự chạy.</p>
     *
     * <p><b>Vì sao có kiểu này bên cạnh {@code membership.domain.ClaimDuplicateSuspect}.</b> Bản ở
     * {@code domain} là thứ {@code PersonClaim} giữ; bản ở đây là thứ đi qua ranh giới module. Gộp
     * làm một nghĩa là bên hiện thực phải nhìn thấy {@code membership.domain}, mà gói ấy không mang
     * {@code @NamedInterface} — và mở nó ra là mở cả {@code AppUser}, {@code Invitation},
     * {@code MemberScope}. Phép dịch giữa hai kiểu nằm ở đúng một chỗ:
     * {@code PersonClaimService#submitNew}.</p>
     *
     * @param signals tên các tín hiệu đã đóng góp, dạng chuỗi — <b>chỉ để hiển thị</b>
     * @param hint    câu giải thích ngắn — <b>chỉ để hiển thị</b>, đừng phân tích chuỗi này
     */
    record Suspect(UUID personId, int score, List<String> signals, String hint) {

        public Suspect {
            signals = signals == null ? List.of() : List.copyOf(signals);
        }
    }
}
