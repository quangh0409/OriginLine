package vn.giapha.genealogy.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.port.DuplicateCandidatePort;
import vn.giapha.genealogy.domain.port.DuplicateCandidatePort.NameKey;
import vn.giapha.genealogy.domain.port.DuplicateCandidatePort.PersonSignature;

/**
 * <b>Phép dò trùng nhân khẩu</b> — cảnh báo khi hồ sơ đang định nhập có vẻ là một người đã có
 * trong gia phả (mã lỗi {@code DUPLICATE_PERSON_SUSPECTED}).
 *
 * <h2>Thiết kế theo lô ngay từ đầu</h2>
 * Đường ống nhập liệu hàng loạt (400 người một lượt, quét từ gia phả giấy) là nơi phép dò này có
 * giá trị nhất, nên {@link #check(List)} mới là API chính còn lối gọi một người chỉ là ca đặc biệt
 * của nó. Cả lô đi qua <b>đúng hai</b> vòng gọi CSDL: một để bỏ dấu tên, một để lấy ứng viên. Ghép
 * cặp và chấm điểm làm trong bộ nhớ.
 *
 * <p>Lô còn được <b>tự soi chính mình</b>: hai dòng trùng nhau nằm trong cùng một tệp nhập là
 * chuyện rất hay xảy ra khi hai người trong họ cùng chép một cuốn gia phả giấy. Dòng sau bị đối
 * chiếu với các dòng trước trong cùng lô, kết quả mang {@code personId = null} và mã tham chiếu của
 * dòng kia.</p>
 *
 * <h2>Máy nghi ngờ, người quyết định</h2>
 * Lớp này <b>không bao giờ</b> tự gộp hai hồ sơ và cũng không từ chối vĩnh viễn. Nó chỉ trả về danh
 * sách nghi ngờ, hoặc — ở lối gọi {@link #check(DuplicateProbe, boolean)} — ném một cảnh báo ghi đè
 * được. Gộp nhầm hai người trong gia phả là loại lỗi rất khó gỡ.
 *
 * @see DuplicateScorer về tiêu chí và ngưỡng
 */
@Component
public class DuplicatePersonChecker {

    private static final Logger log = LoggerFactory.getLogger(DuplicatePersonChecker.class);

    /** Trần số nhân khẩu mà cửa lọc theo tên được kéo về cho <b>mỗi</b> dòng đầu vào. */
    private static final int UNG_VIEN_MOI_DONG = 40;

    /** Trần tuyệt đối cho cả lô — một cái tên phổ biến có thể trúng cả trăm người trong họ lớn. */
    private static final int UNG_VIEN_TOI_DA = 2000;

    /** Trần số nghi ngờ báo về cho một dòng: hộp thoại không ai đọc nổi quá vài dòng. */
    private static final int NGHI_NGO_TOI_DA = 10;

    private final DuplicateCandidatePort candidates;

    public DuplicatePersonChecker(DuplicateCandidatePort candidates) {
        this.candidates = candidates;
    }

    // -----------------------------------------------------------------------------------------
    // Lối gọi theo lô — dùng cho nhập liệu hàng loạt
    // -----------------------------------------------------------------------------------------

    /**
     * Dò trùng cho cả một lô, đối chiếu với gia phả hiện có <b>và</b> với chính các dòng khác trong
     * lô.
     *
     * @return đúng một báo cáo cho mỗi dòng đầu vào, cùng thứ tự; báo cáo rỗng nghĩa là không có gì
     *         phải hỏi — đó là kết quả mong đợi cho tuyệt đại đa số hồ sơ
     */
    public List<DuplicateReport> check(List<DuplicateProbe> probes) {
        if (probes == null || probes.isEmpty()) {
            return List.of();
        }
        List<PersonSignature> chuKy = buildSignatures(probes);
        List<PersonSignature> daCo = loadCandidates(chuKy);

        List<DuplicateReport> reports = new ArrayList<>(probes.size());
        List<PersonSignature> daXet = new ArrayList<>(probes.size());
        for (int i = 0; i < probes.size(); i++) {
            DuplicateProbe probe = probes.get(i);
            PersonSignature signature = chuKy.get(i);
            List<DuplicateMatch> matches = new ArrayList<>();

            for (PersonSignature candidate : daCo) {
                if (probe.excludePersonId() != null
                        && probe.excludePersonId().equals(candidate.personId())) {
                    continue;
                }
                DuplicateScorer.score(signature, candidate).ifPresent(matches::add);
            }
            for (int j = 0; j < daXet.size(); j++) {
                final String refKia = probes.get(j).ref();
                DuplicateScorer.score(signature, daXet.get(j))
                        .map(match -> match.withRef(refKia))
                        .ifPresent(matches::add);
            }
            matches.sort(Comparator.comparingInt(DuplicateMatch::score).reversed());
            if (matches.size() > NGHI_NGO_TOI_DA) {
                matches = new ArrayList<>(matches.subList(0, NGHI_NGO_TOI_DA));
            }
            reports.add(new DuplicateReport(probe.ref(), matches));
            daXet.add(signature);
        }
        long coNghiNgo = reports.stream().filter(DuplicateReport::coNghiNgo).count();
        if (coNghiNgo > 0) {
            // Khong log ten: day la du lieu pha he binh thuong. So luong thi du de theo doi ty le
            // canh bao - canh bao qua tay con te hon khong canh bao.
            log.info("Do trung: {}/{} dong co nghi ngo, {} ung vien duoc quet",
                    coNghiNgo, reports.size(), daCo.size());
        }
        return reports;
    }

    // -----------------------------------------------------------------------------------------
    // Lối gọi một người — dùng trong luồng thêm nhân khẩu
    // -----------------------------------------------------------------------------------------

    /** Dò trùng cho một hồ sơ, không ném ngoại lệ. */
    public List<DuplicateMatch> check(DuplicateProbe probe) {
        List<DuplicateReport> reports = check(List.of(probe));
        return reports.isEmpty() ? List.of() : reports.get(0).matches();
    }

    /**
     * Dò trùng rồi <b>chặn hoặc cho qua</b>, cùng hình dạng với {@code TabooNameChecker.check}.
     *
     * @param confirmed người dùng đã xem danh sách nghi ngờ ở lần gọi trước và khẳng định đây là
     *        người khác
     * @return chuỗi mô tả để ghi vào {@code audit_log} khi có ghi đè; {@code null} khi không có gì
     * @throws DuplicatePersonSuspectedException khi có nghi ngờ mà chưa được xác nhận —
     *         <b>không bản ghi nào được tạo</b>, vì phép dò chạy trước mọi lệnh ghi
     */
    public String check(DuplicateProbe probe, boolean confirmed) {
        List<DuplicateMatch> matches = check(probe);
        if (matches.isEmpty()) {
            return null;
        }
        if (!confirmed) {
            throw new DuplicatePersonSuspectedException(matches);
        }
        // Ghi bang KHOA, khong bang gia tri doc tu pha.
        //
        // `audit_log` la mot bang ma bo loc phan tang KHONG canh: da vao day thi no nam lai mai,
        // va moi truy van thang vao bang deu doc duoc. Bo do trung quet TOAN dong ho, nen ung vien
        // co the la nguoi con song o mot chi khac — `displayName` cua ho khong duoc phep dong lai
        // o day chi vi co nguoi bam "ghi de".
        //
        // `/api/v1/audit-logs/**` hien chi mo cho ADMIN/COUNCIL, hai vai von xem tron, nen day
        // khong phai lo ro ra nguoi khong co quyen. Nhung pham vi cua mot endpoint doi duoc, con
        // chuoi da ghi thi khong — nen dung khoa ngay tu dau.
        String summary = matches.stream()
                .map(m -> (m.personId() != null ? m.personId().toString() : "dong " + m.ref())
                        + " (" + m.score() + " diem)")
                .collect(Collectors.joining("; "));
        log.info("Ghi de canh bao nghi trung cho {} ung vien, da co xac nhan cua nguoi dung",
                matches.size());
        return "Ghi de canh bao nghi trung: " + summary;
    }

    // -----------------------------------------------------------------------------------------
    // Dựng chữ ký
    // -----------------------------------------------------------------------------------------

    /**
     * Dựng chữ ký cho cả lô. Toàn bộ tên của lô được bỏ dấu trong <b>một</b> vòng gọi CSDL — phép
     * bỏ dấu phải là {@code vn_unaccent} của Postgres chứ không phải một bản cài lại trong Java,
     * nếu không hai bản sẽ lệch nhau ở đúng những ký tự hiếm và cảnh báo sẽ lúc có lúc không.
     */
    private List<PersonSignature> buildSignatures(List<DuplicateProbe> probes) {
        // Bo null ngay tu day: cong bo dau nhan mot lo chuoi va tra ve cung so phan tu, khong phai
        // noi de xu ly "khong co gia tri".
        List<String> raw = new ArrayList<>();
        for (DuplicateProbe probe : probes) {
            for (PersonName name : probe.names()) {
                raw.add(name.fullName());
            }
            if (probe.nativePlace() != null && !probe.nativePlace().isBlank()) {
                raw.add(probe.nativePlace());
            }
        }
        if (raw.isEmpty()) {
            return probes.stream().map(probe -> toSignature(probe, Map.of())).toList();
        }
        List<String> unaccented = candidates.unaccent(raw);
        if (unaccented.size() != raw.size()) {
            throw new IllegalStateException("DuplicateCandidatePort.unaccent tra ve sai so phan tu: "
                    + unaccented.size() + " thay vi " + raw.size());
        }
        Map<String, String> tra = new LinkedHashMap<>();
        for (int i = 0; i < raw.size(); i++) {
            tra.put(raw.get(i), unaccented.get(i));
        }
        List<PersonSignature> result = new ArrayList<>(probes.size());
        for (DuplicateProbe probe : probes) {
            result.add(toSignature(probe, tra));
        }
        return result;
    }

    private PersonSignature toSignature(DuplicateProbe probe, Map<String, String> unaccented) {
        List<NameKey> names = probe.names().stream()
                .map(n -> new NameKey(n.type(), n.fullName(), unaccented.get(n.fullName())))
                .toList();
        String display = probe.names().stream().filter(PersonName::primary).findFirst()
                .or(() -> probe.names().stream().findFirst())
                .map(PersonName::fullName).orElse(null);
        return new PersonSignature(null, display, names, probe.gender(), probe.generation(),
                probe.branchId(), year(probe.birth()), year(probe.death()), gio(probe.death()),
                probe.nativePlace() == null ? null : unaccented.get(probe.nativePlace()));
    }

    /** Ngày giỗ = phần âm lịch của ngày mất — nguồn chân lý, {@code death_solar} chỉ là tham chiếu. */
    private static vn.giapha.shared.vo.LunarDate gio(LifeDate death) {
        return death == null ? null : death.lunar();
    }

    private static Integer year(LifeDate date) {
        return date == null ? null : date.year().orElse(null);
    }

    private List<PersonSignature> loadCandidates(List<PersonSignature> probes) {
        Set<String> names = new LinkedHashSet<>();
        for (PersonSignature probe : probes) {
            for (NameKey name : probe.names()) {
                if (name.unaccented() != null && !name.unaccented().isBlank()) {
                    names.add(name.unaccented());
                }
            }
        }
        if (names.isEmpty()) {
            return List.of();
        }
        int limit = Math.min(UNG_VIEN_TOI_DA, UNG_VIEN_MOI_DONG * Math.max(1, probes.size()));
        return candidates.findByAnyName(names, limit);
    }
}
