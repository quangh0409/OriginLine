package vn.giapha.dataimport.infrastructure.genealogy;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.LunarDeathDate;
import vn.giapha.dataimport.domain.port.DuplicateScanPort;
import vn.giapha.genealogy.application.DuplicateMatch;
import vn.giapha.genealogy.application.DuplicateReport;
import vn.giapha.genealogy.application.PersonScreeningService;
import vn.giapha.genealogy.application.ScreeningSubject;
import vn.giapha.shared.vo.LunarDate;

/**
 * Hiện thực {@link DuplicateScanPort} bằng cách <b>gọi lại bộ dò trùng đã có</b> của
 * {@code genealogy}, qua facade {@link PersonScreeningService} (named interface {@code "do-trung"}).
 *
 * <h2>Lớp này cố ý không chứa một dòng logic nghiệp vụ nào</h2>
 * Nó dịch kiểu, và chỉ dịch kiểu: {@code UngVien} của {@code dataimport} thành
 * {@link ScreeningSubject}, rồi {@link DuplicateReport} thành {@code KetQua}. Toàn bộ việc sinh ứng
 * viên, chấm điểm và chọn ngưỡng nằm ở {@code DuplicatePersonChecker} — nơi bộ trọng số đã được
 * hiệu chỉnh trên một dòng họ mô phỏng và được ghim bằng test mô phỏng cảnh báo giả. Nếu ở đây có
 * thêm một phép lọc "cho chắc" thì đường nhập liệu và đường thêm người sẽ nghi ngờ khác nhau, và
 * không ai giải thích được vì sao.
 *
 * <p>Cả việc xếp tên vào lớp tên nào (thường gọi / huý / thụy) lẫn việc dựng mốc sinh–mất cũng
 * <b>không</b> ở đây: đó là kiến thức của {@code genealogy} và nằm trong facade. Lớp này không
 * nhìn thấy {@code PersonName} hay {@code LifeDate}, và đó là điều mong muốn — value object của
 * domain không đi qua ranh giới context.</p>
 *
 * <h2>Bộ dò tự soi cả trong nội bộ lô — không phải gọi hai lần</h2>
 * Cùng một người được ghi hai lần với hai mã khác nhau trong một tệp là chuyện rất hay xảy ra: một
 * người đàn ông xuất hiện ở cả trang đời cha lẫn trang đời con trong sổ. Facade đã đối chiếu từng
 * hồ sơ với các hồ sơ trước nó trong cùng lô, và kết quả ấy mang {@code personId = null} kèm mã
 * tham chiếu của dòng kia.
 *
 * <h2>Bề mặt tiếp xúc với genealogy — giữ cho nó nhỏ</h2>
 * Lớp này chạm đúng bốn kiểu của {@code genealogy}, tất cả đều ở tầng {@code application} và đều
 * mang nhãn {@code "do-trung"}: {@link PersonScreeningService}, {@link ScreeningSubject},
 * {@link DuplicateReport}, {@link DuplicateMatch}. Nếu chữ ký của facade sau này nhận thêm một kiểu
 * mới thì kiểu ấy phải được gắn nhãn ở phía {@code genealogy}, nếu không {@code ModularityTests} sẽ
 * đỏ — và đó là hành vi mong muốn. Đừng "chữa" bằng cách chép kiểu đó sang đây.
 */
@Component
public class DuplicateScanAdapter implements DuplicateScanPort {

    private final PersonScreeningService screening;

    public DuplicateScanAdapter(PersonScreeningService screening) {
        this.screening = screening;
    }

    @Override
    public List<KetQua> scan(List<UngVien> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ScreeningSubject> subjects = new ArrayList<>(rows.size());
        for (UngVien row : rows) {
            subjects.add(toSubject(row));
        }
        List<KetQua> ketQua = new ArrayList<>(rows.size());
        for (DuplicateReport report : screening.scanDuplicates(subjects)) {
            List<NghiNgo> nghiNgo = new ArrayList<>(report.matches().size());
            for (DuplicateMatch m : report.matches()) {
                nghiNgo.add(new NghiNgo(m.personId(), m.ref(), m.displayName(), m.generation(),
                        m.score(), m.hint()));
            }
            ketQua.add(new KetQua(report.ref(), nghiNgo));
        }
        return ketQua;
    }

    private static ScreeningSubject toSubject(UngVien row) {
        return new ScreeningSubject(row.ref(), row.fullName(), row.tabooName(),
                row.posthumousName(), row.gender(), row.generation(), row.branchId(),
                row.birthYear(), gio(row.death()), row.nativePlace(),
                // Dong dang cap nhat mot nguoi da co thi phai loai chinh nguoi ay ra, neu khong
                // moi lan tai lai se bao "trung voi chinh minh" cho ca 400 dong.
                row.excludePersonId());
    }

    /**
     * Ngày giỗ — nguồn chân lý, và là tín hiệu nặng điểm nhất của bộ dò.
     *
     * <p><b>Mất mát đã biết:</b> {@link LunarDate} của shared kernel bắt buộc có năm, còn
     * {@link LunarDeathDate} thì không — vì "mất ngày 15 tháng 8, không rõ năm" là chuyện rất
     * thường trong sổ cũ. Ca ấy đi vào bộ dò mà <b>không mang ngày giỗ</b>, tức là mất đúng tín
     * hiệu mạnh nhất, và đó chính là nhóm hồ sơ cổ nhất, hay trùng nhau nhất. Đây là hạn chế của
     * mô hình dữ liệu chứ không phải của lớp này; ghi ra đây để lần sau ai nới {@code LunarDate}
     * thì nhớ quay lại.</p>
     */
    private static LunarDate gio(LunarDeathDate d) {
        if (d == null || !d.coNam()) {
            return null;
        }
        return new LunarDate(d.year(), d.month(), d.day(), d.leap());
    }
}
