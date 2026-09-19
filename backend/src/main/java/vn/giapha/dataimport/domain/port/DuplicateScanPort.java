package vn.giapha.dataimport.domain.port;

import java.util.List;
import java.util.UUID;
import vn.giapha.dataimport.domain.LunarDeathDate;
import vn.giapha.shared.vo.Gender;

/**
 * Cổng dò <b>nghi trùng người</b>.
 *
 * <h2>Cổng này KHÔNG chứa thuật toán, và đó là toàn bộ mục đích của nó</h2>
 * Phép dò trùng đã tồn tại ở context {@code genealogy}, đã được hiệu chỉnh trên một dòng họ mô
 * phỏng, và đã có bộ trọng số nhiều tín hiệu với ngưỡng được chọn để chịu được hai tập quán đặt
 * tên của người Việt: tên đệm theo đời, và đặt tên con theo tên người anh đã mất. Viết một bộ dò
 * thứ hai ở đây thì hai bộ sẽ lệch nhau, và triệu chứng là màn nhập liệu bảo trùng còn màn thêm
 * người bảo không.
 *
 * <p>Vì vậy cổng khai báo <b>đúng thứ context này cần</b>, bằng kiểu của chính nó, còn adapter
 * dịch sang lời gọi bộ dò đã có. Nhờ thế cả {@code domain} lẫn {@code application} của
 * {@code dataimport} không biết gì về {@code genealogy}, và chỗ phải sửa khi ranh giới module đổi
 * là <b>một tệp adapter</b>.</p>
 *
 * <h2>Theo lô, không N+1</h2>
 * 400 dòng nhân 20 ứng viên là 8.000 lượt chấm điểm, mỗi lượt cần dữ liệu của người ứng viên. Lối
 * gọi duy nhất là theo lô, để không ai viết được vòng lặp truy vấn.
 */
public interface DuplicateScanPort {

    /** @return đúng một kết quả cho mỗi dòng đầu vào, cùng thứ tự */
    List<KetQua> scan(List<UngVien> rows);

    /**
     * Một dòng đang định nhập.
     *
     * @param ref mã ngoài của dòng, để ghép kết quả về đúng dòng
     * @param generation đời thứ; càng đáng tin càng ít cảnh báo giả, vì khác đời là phản chứng nặng
     */
    record UngVien(String ref,
                   String fullName,
                   String tabooName,
                   String posthumousName,
                   Gender gender,
                   Integer generation,
                   UUID branchId,
                   Integer birthYear,
                   LunarDeathDate death,
                   String nativePlace,
                   UUID excludePersonId) {
    }

    /** @param nghiNgo các nhân khẩu bị nghi, đã sắp giảm dần theo điểm */
    record KetQua(String ref, List<NghiNgo> nghiNgo) {

        public KetQua {
            nghiNgo = nghiNgo == null ? List.of() : List.copyOf(nghiNgo);
        }

        public boolean co() {
            return !nghiNgo.isEmpty();
        }
    }

    /**
     * @param personId nhân khẩu đã có trong phả; null khi bên bị nghi là một dòng khác <b>trong
     *        cùng tệp</b> — chuyện rất hay xảy ra khi một người đàn ông xuất hiện ở cả trang đời
     *        cha lẫn trang đời con trong sổ
     * @param ref mã ngoài của dòng kia, khi bên bị nghi nằm trong cùng tệp
     * @param hint câu giải thích ngắn cho người dùng, <b>chỉ để hiển thị</b>
     */
    record NghiNgo(UUID personId,
                   String ref,
                   String displayName,
                   Integer generation,
                   int score,
                   String hint) {
    }
}
