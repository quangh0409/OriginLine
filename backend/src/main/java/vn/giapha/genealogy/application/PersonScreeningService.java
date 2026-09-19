package vn.giapha.genealogy.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import vn.giapha.genealogy.domain.DatePrecision;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.TabooConflict;

/**
 * <b>Cửa duy nhất</b> để context khác dùng lại hai phép quét trước-khi-ghi của {@code genealogy}:
 * nghi trùng người ({@link DuplicatePersonChecker}) và kỵ húy ({@link TabooNameChecker}).
 *
 * <h2>Vì sao phải có lớp này</h2>
 * Hai phép quét ấy nói bằng value object của <b>domain</b> — {@code PersonName}, {@code LifeDate},
 * {@code NameType}, {@code DatePrecision}, {@code TabooConflict}. Value object của domain
 * <b>không được đi qua ranh giới bounded context</b>: làm thế thì mọi context khác phải học mô
 * hình tên nhiều lớp và mô hình song lịch của phả hệ, và để Spring Modulith cho phép điều đó thì
 * phải gắn siêu dữ liệu framework ngay trên các lớp domain — đúng thứ mà {@code DomainPurityTest}
 * cấm, và là điều kiện BA v2 §12 đặt ra khi giữ Neo4j làm phương án dự phòng (adapter đồ thị phải
 * thay được mà domain không biết).
 *
 * <p>Vì vậy facade này nhận {@link ScreeningSubject} và trả {@link DuplicateReport} /
 * {@link TabooHit} — toàn kiểu của tầng application, chỉ dựng trên kiểu nguyên thuỷ và VO của
 * shared kernel. Đây là <b>chỗ duy nhất</b> trong toàn hệ thống dịch từ tham số quét sang kiểu
 * domain; context khác không nhìn thấy kiểu domain nào cả.</p>
 *
 * <h2>Lớp này KHÔNG chấm điểm</h2>
 * Không một dòng logic nghiệp vụ nào ở đây: không ngưỡng, không trọng số, không lọc "cho chắc".
 * Toàn bộ việc sinh ứng viên, chấm điểm và chọn ngưỡng vẫn nằm ở {@code DuplicatePersonChecker} /
 * {@code TabooNameChecker}. Nếu facade tự thêm một phép lọc thì đường nhập liệu và đường thêm
 * người sẽ nghi ngờ khác nhau, và triệu chứng là <b>màn nhập liệu bảo trùng còn màn thêm người
 * bảo không</b> — không ai truy ra được vì sao.
 *
 * @see ScreeningSubject về cách chống nhân đôi khái niệm
 */
@Service
@org.springframework.modulith.NamedInterface("do-trung")
public class PersonScreeningService {

    private final DuplicatePersonChecker duplicates;
    private final TabooNameChecker taboos;

    public PersonScreeningService(DuplicatePersonChecker duplicates, TabooNameChecker taboos) {
        this.duplicates = duplicates;
        this.taboos = taboos;
    }

    /**
     * Quét <b>nghi trùng người</b> cho cả một lô, đối chiếu với gia phả hiện có <b>và</b> với chính
     * các hồ sơ khác trong lô.
     *
     * <p>Bộ dò tự soi trong nội bộ lô, nên không phải gọi hai lần: cùng một người được chép hai lần
     * với hai mã khác nhau là chuyện rất hay xảy ra khi một người đàn ông xuất hiện ở cả trang đời
     * cha lẫn trang đời con trong sổ. Kết quả ấy mang {@code personId = null} kèm mã tham chiếu của
     * dòng kia.</p>
     *
     * @return đúng một báo cáo cho mỗi hồ sơ đầu vào, cùng thứ tự; báo cáo rỗng nghĩa là "không có
     *         gì phải hỏi" — kết quả mong đợi cho tuyệt đại đa số hồ sơ
     */
    public List<DuplicateReport> scanDuplicates(List<ScreeningSubject> subjects) {
        if (subjects == null || subjects.isEmpty()) {
            return List.of();
        }
        List<DuplicateProbe> probes = new ArrayList<>(subjects.size());
        for (ScreeningSubject subject : subjects) {
            probes.add(toProbe(subject));
        }
        return duplicates.check(probes);
    }

    /**
     * Quét <b>kỵ húy</b> (FR-1.6) cho cả một lô.
     *
     * <p>Chỉ những hồ sơ <b>có tên huý</b> mới được hỏi — trong một tệp thật đó là một phần nhỏ, vì
     * cột Tên huý thường trống với các đời gần.</p>
     *
     * <p><b>Vì sao gọi rồi bắt ngoại lệ.</b> {@link TabooNameChecker} được thiết kế cho luồng thêm
     * một người: lần gọi đầu ném {@link TabooNameConflictException} kèm danh sách va chạm và
     * <b>không ghi gì cả</b>, lần gọi sau kèm cờ xác nhận thì cho qua. Ở đây ta chỉ cần vế thứ
     * nhất, nên gọi với {@code confirmed = false} rồi đọc ngoại lệ. Trông hơi lạ, và cám dỗ là viết
     * một câu SQL cho gọn — đừng: bộ dò ấy mang một quyết định nghiệp vụ mà SQL viết lại sẽ đánh
     * mất, là <b>chỉ soi lớp tên HUY</b>. Trùng tên tự, hiệu, thụy, thường gọi hay pháp danh không
     * phạm huý, và quét cả chúng chỉ tạo ra một biển cảnh báo giả khiến người dùng bấm bỏ qua theo
     * phản xạ — lúc đó cảnh báo mất sạch giá trị, kể cả những cảnh báo đúng.</p>
     *
     * @return <b>chỉ</b> các va chạm; hồ sơ không va chạm không xuất hiện trong kết quả. Một hồ sơ
     *         có thể sinh nhiều va chạm khi trùng huý nhiều bậc trên.
     */
    public List<TabooHit> scanTabooNames(List<ScreeningSubject> subjects) {
        if (subjects == null || subjects.isEmpty()) {
            return List.of();
        }
        List<TabooHit> hits = new ArrayList<>();
        for (ScreeningSubject subject : subjects) {
            if (isBlank(subject.tabooName())) {
                continue;
            }
            List<PersonName> names = List.of(
                    PersonName.of(NameType.HUY, subject.tabooName(), true));
            try {
                taboos.check(names, subject.generation(), subject.excludePersonId(), false);
            } catch (TabooNameConflictException ex) {
                for (TabooConflict conflict : ex.conflicts()) {
                    hits.add(new TabooHit(subject.ref(), conflict.tabooName(),
                            conflict.ancestorPersonId(), conflict.ancestorDisplayName(),
                            conflict.ancestorGeneration()));
                }
            }
        }
        return hits;
    }

    // -----------------------------------------------------------------------------------------
    // Chỗ DUY NHẤT dịch tham số quét sang value object của domain
    // -----------------------------------------------------------------------------------------

    /**
     * Dịch {@link ScreeningSubject} sang {@link DuplicateProbe}.
     *
     * <p>Package-private có chủ đích: {@code PersonScreeningServiceTest} ghim phép dịch này, còn
     * ngoài context thì không ai nhìn thấy {@code DuplicateProbe}. Thêm một thành phần vào
     * {@code DuplicateProbe} mà quên nuôi nó từ đây sẽ làm test ấy đỏ.</p>
     */
    static DuplicateProbe toProbe(ScreeningSubject subject) {
        return new DuplicateProbe(subject.ref(), names(subject), subject.gender(),
                subject.generation(), subject.branchId(), birth(subject), death(subject),
                subject.nativePlace(), subject.excludePersonId());
    }

    /**
     * Tên thường gọi là tên <b>chính</b>, tên huý và thuỵ hiệu đi kèm.
     *
     * <p>Tên huý mạnh hơn tên thường gọi rất nhiều khi so trùng vì nó ít trùng ngẫu nhiên; bỏ nó ra
     * khỏi đây là tự tay vứt đi tín hiệu tốt nhất mà một cuốn gia phả cung cấp. Bộ dò tra chéo mọi
     * lớp tên, nên một người được nhập lại lần hai với tên ghi vào lớp khác vẫn khớp được.</p>
     */
    private static List<PersonName> names(ScreeningSubject subject) {
        List<PersonName> names = new ArrayList<>(3);
        if (!isBlank(subject.fullName())) {
            names.add(PersonName.of(NameType.THUONG_GOI, subject.fullName(), true));
        }
        if (!isBlank(subject.tabooName())) {
            names.add(PersonName.of(NameType.HUY, subject.tabooName(), names.isEmpty()));
        }
        if (!isBlank(subject.posthumousName())) {
            names.add(PersonName.of(NameType.THUY, subject.posthumousName(), names.isEmpty()));
        }
        return names;
    }

    /** Chỉ có năm sinh, không có ngày — đúng mức chính xác mà gia phả giấy thường còn giữ được. */
    private static LifeDate birth(ScreeningSubject subject) {
        if (subject.birthYear() == null) {
            return null;
        }
        return LifeDate.of(LocalDate.of(subject.birthYear(), 1, 1), null, DatePrecision.YEAR);
    }

    /**
     * Ngày mất dựng từ <b>ngày giỗ âm lịch</b> — nguồn chân lý, và là tín hiệu nặng điểm nhất của
     * bộ dò. Ngày dương cố ý không nhận: nó đổi theo từng năm nên chỉ là dữ liệu dẫn xuất.
     *
     * <p><b>Mất mát đã biết:</b> {@code LunarDate} của shared kernel bắt buộc có năm, nên ca "mất
     * ngày 15 tháng 8, không rõ năm" đi vào bộ dò mà <b>không mang ngày giỗ</b> — tức là mất đúng
     * tín hiệu mạnh nhất, và đó chính là nhóm hồ sơ cổ nhất, hay trùng nhau nhất. Đây là hạn chế
     * của mô hình dữ liệu chứ không phải của lớp này; ghi ra đây để lần sau ai nới
     * {@code LunarDate} thì nhớ quay lại.</p>
     */
    private static LifeDate death(ScreeningSubject subject) {
        return subject.gio() == null ? null : LifeDate.ofLunar(subject.gio());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
