package vn.giapha.dataimport.infrastructure.genealogy;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.port.TabooScanPort;
import vn.giapha.genealogy.application.PersonScreeningService;
import vn.giapha.genealogy.application.ScreeningSubject;
import vn.giapha.genealogy.application.TabooHit;

/**
 * Hiện thực {@link TabooScanPort} bằng cách gọi lại bộ dò <b>kỵ húy</b> đã có của
 * {@code genealogy}, qua facade {@link PersonScreeningService} (named interface {@code "do-trung"}).
 *
 * <h2>Lớp này là bộ dịch kiểu, không phải bộ dò</h2>
 * Nó dựng {@link ScreeningSubject#kyHuy} từ mỗi dòng rồi ánh xạ {@link TabooHit} về {@code KetQua}.
 * Không một quyết định nghiệp vụ nào ở đây — kể cả việc bỏ qua dòng không có tên huý, vốn nằm
 * trong facade cùng với phép dò.
 *
 * <p>Cám dỗ là viết một câu SQL cho gọn. Đừng: bộ dò ấy mang theo một quyết định nghiệp vụ mà một
 * câu SQL viết lại sẽ đánh mất — <b>chỉ soi lớp tên HUY</b>. Trùng tên tự, hiệu, thụy, thường gọi
 * hay pháp danh <b>không phạm huý</b>, và quét cả chúng chỉ tạo ra một biển cảnh báo giả khiến
 * người dùng bấm bỏ qua theo phản xạ. Lúc đó cảnh báo mất sạch giá trị, kể cả những cảnh báo
 * đúng.</p>
 *
 * <h2>Cảnh báo, không phải lệnh cấm</h2>
 * Trong một cuốn gia phả đang được <b>chép lại</b>, va chạm kỵ húy thường là sự thật lịch sử chứ
 * không phải lỗi nhập liệu: các cụ đã đặt tên như thế rồi. Vì vậy kết quả ở đây luôn đi vào nhóm
 * cảnh báo, và việc ghi đè phải để lại dấu trong {@code audit_log} ở bước ghi.
 *
 * <h2>Facade gọi theo từng dòng, và vì sao chấp nhận được</h2>
 * Khác bộ dò trùng, phép này chỉ chạy trên những dòng <b>có tên huý</b> — trong một tệp thật đó là
 * một phần nhỏ, vì cột Tên huý thường trống với các đời gần. Mỗi lượt là một truy vấn có chỉ mục
 * ({@code ix_person_name_huy}). Nếu về sau cột ấy được điền đủ cho cả 400 dòng thì chỗ cần sửa là
 * thêm một lối gọi theo lô vào {@code TabooNamePort} rồi cho facade dùng nó, không phải sửa lớp
 * này.
 */
@Component
public class TabooScanAdapter implements TabooScanPort {

    private final PersonScreeningService screening;

    public TabooScanAdapter(PersonScreeningService screening) {
        this.screening = screening;
    }

    @Override
    public List<KetQua> scan(List<UngVien> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ScreeningSubject> subjects = new ArrayList<>(rows.size());
        for (UngVien row : rows) {
            subjects.add(ScreeningSubject.kyHuy(row.ref(), row.tabooName(), row.generation(),
                    row.excludePersonId()));
        }
        List<KetQua> ketQua = new ArrayList<>();
        for (TabooHit hit : screening.scanTabooNames(subjects)) {
            ketQua.add(new KetQua(hit.ref(), hit.tabooName(), hit.ancestorPersonId(),
                    hit.ancestorDisplayName(), hit.ancestorGeneration()));
        }
        return ketQua;
    }
}
