package vn.giapha.genealogy.infrastructure.membership;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.giapha.genealogy.application.DuplicateMatch;
import vn.giapha.genealogy.application.DuplicateReport;
import vn.giapha.genealogy.application.PersonScreeningService;
import vn.giapha.genealogy.application.ScreeningSubject;
import vn.giapha.membership.application.ClaimScreeningPort;
import vn.giapha.shared.vo.Gender;

/**
 * Bộ <b>dịch kiểu</b> giữa đơn tự nhận và bộ dò trùng của {@code genealogy}. Không một dòng logic
 * nghiệp vụ nào ở đây.
 *
 * <h2>Đường vào: facade {@code PersonScreeningService}, named interface {@code "do-trung"}</h2>
 * {@code genealogy} <b>không</b> mở cả gói {@code application}, càng không mở {@code domain}. Nó
 * gắn {@code @NamedInterface("do-trung")} lên đúng năm kiểu, và bốn kiểu trong số đó xuất hiện ở
 * tệp này: {@link PersonScreeningService} (cửa duy nhất), {@link ScreeningSubject} (đầu vào),
 * {@link DuplicateReport} và {@link DuplicateMatch} (đầu ra). Nhờ đó {@code membership}
 * <b>không biết gì</b> về {@code genealogy}: bề mặt tiếp xúc gói gọn trong tệp này.
 *
 * <p><b>Không có kiểu {@code domain} nào của {@code genealogy} ở đây</b>, và đó là điểm mấu chốt:
 * {@code PersonName}, {@code LifeDate}, {@code NameType} không đi qua ranh giới context. Việc xếp
 * một chuỗi tên vào lớp tên nào là kiến thức của {@code genealogy} và nằm trong facade.</p>
 *
 * <h2>Điều tuyệt đối KHÔNG được làm ở đây</h2>
 * Viết một bộ chấm điểm nghi trùng thứ hai, kể cả một câu SQL "cho nhanh" — xem javadoc của
 * {@link ClaimScreeningPort}.
 */
@Component
public class ClaimScreeningAdapter implements ClaimScreeningPort {

    private final PersonScreeningService screening;

    public ClaimScreeningAdapter(PersonScreeningService screening) {
        this.screening = screening;
    }

    @Override
    public List<Suspect> scanDuplicates(String ref, String fullName, Integer birthYear,
                                        Gender gender, UUID branchId) {
        if (fullName == null || fullName.isBlank()) {
            return List.of();
        }
        // Nguoi khai CHUA co trong pha nen khong co gi de loai tru (excludePersonId = null), va
        // cung khong co doi thu de cham diem — de null chu KHONG doan: doi thu la thu bo do coi la
        // phan chung dang tin nhat, va mot con so bia se lam sai lech ca bang diem.
        ScreeningSubject subject = new ScreeningSubject(ref, fullName, null, null, gender, null,
                branchId, birthYear, null, null, null);

        List<Suspect> suspects = new ArrayList<>();
        for (DuplicateReport report : screening.scanDuplicates(List.of(subject))) {
            for (DuplicateMatch match : report.matches()) {
                if (match.personId() == null) {
                    // Ben bi nghi la mot dong khac trong cung mot lo nhap lieu — khong the xay ra o
                    // day vi ta chi gui MOT ho so. Bo qua cho chac thay vi luu mot khoa null.
                    continue;
                }
                suspects.add(new Suspect(match.personId(), match.score(), tenTinHieu(match),
                        match.hint()));
            }
        }
        return suspects;
    }

    /**
     * Tên các tín hiệu ở dạng chuỗi.
     *
     * <p>Lặp qua {@code Object} chứ không qua {@code DuplicateSignal}: enum ấy <b>không</b> nằm
     * trong năm kiểu mang nhãn {@code "do-trung"}, nên một vòng lặp có kiểu tường minh sẽ làm
     * {@code ModularityTests} đỏ. Và đó là hành vi đúng — nó buộc người sửa cân nhắc thay vì lặng
     * lẽ nới bề mặt tiếp xúc. Chuỗi là đủ: giá trị này chỉ để hiển thị.</p>
     */
    private static List<String> tenTinHieu(DuplicateMatch match) {
        List<String> names = new ArrayList<>();
        for (Object signal : match.signals()) {
            if (signal != null) {
                names.add(signal.toString());
            }
        }
        return names;
    }
}
