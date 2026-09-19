package vn.giapha.genealogy.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import vn.giapha.genealogy.domain.port.DuplicateCandidatePort.NameKey;
import vn.giapha.genealogy.domain.port.DuplicateCandidatePort.PersonSignature;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.LunarDate;

/**
 * Chấm điểm nghi trùng giữa <b>hai hồ sơ</b>. Hàm thuần: không Spring, không CSDL, không trạng
 * thái — nhờ đó ngưỡng và trọng số kiểm chứng được bằng test chạy trong vài micro-giây.
 *
 * <h2>Ba tầng quyết định</h2>
 * <ol>
 *   <li><b>Cửa lọc tên.</b> Không khớp tên ở bất kỳ lớp nào (so không dấu) thì dừng ngay. Đây là
 *       điều kiện cần, không phải bằng chứng: trùng tên trong một dòng họ là chuyện bình thường.</li>
 *   <li><b>Bắt buộc có bằng chứng ngày tháng.</b> Chỉ trùng tên — dù cùng đời, cùng chi, cùng
 *       nguyên quán — vẫn <b>không</b> đủ để kêu. Tục đặt tên theo chữ đệm của đời khiến hai người
 *       anh em họ cùng đời cùng chi trùng tên nhau là chuyện có thật; kêu ở đây là kêu oan hàng
 *       loạt. Phải có ít nhất một trong: giỗ trùng khít, năm sinh khớp, hoặc năm sinh lệch 1–2 năm.
 *       Riêng năm mất khớp thì chỉ cộng điểm chứ <b>không</b> tự nó mở cửa.</li>
 *   <li><b>Tổng điểm ≥ {@value #NGUONG_NGHI_TRUNG}.</b></li>
 * </ol>
 *
 * <h2>Đối chiếu với hợp đồng</h2>
 * {@code contracts/openapi.yaml} mô tả tiêu chí là "cùng tên + năm sinh + chi". Bộ điểm dưới đây
 * cho tổ hợp đó đúng {@code 30 + 22 + 18 = 70}, vừa đúng ngưỡng, tức <b>vẫn kêu</b> như hợp đồng
 * hứa — và các trọng số được hiệu chỉnh để giữ nguyên tính chất đó. Hai chỗ cố ý
 * khác hợp đồng, đã nêu ra chứ không đổi lặng lẽ:
 * <ul>
 *   <li><b>Rộng hơn:</b> giỗ trùng khít cũng đủ kêu dù không có năm sinh. Phần lớn nhân khẩu đời
 *       trên chỉ còn ngày giỗ, không còn năm sinh — theo đúng chữ của hợp đồng thì đúng nhóm hồ sơ
 *       hay bị nhập trùng nhất lại không bao giờ được soi.</li>
 *   <li><b>Hẹp hơn:</b> khác đời thứ thì hầu như không kêu. Đời thứ là đơn trị và suy ra từ cạnh
 *       cha–con; hai hồ sơ cùng tên, cùng năm sinh mà khác đời thì <b>không thể</b> là một người
 *       (cha và con không sinh cùng năm) — một trong hai đơn giản là người khác.</li>
 * </ul>
 */
public final class DuplicateScorer {

    /** Điểm tối thiểu để hỏi người dùng. Xem javadoc lớp về cách hiệu chỉnh con số này. */
    public static final int NGUONG_NGHI_TRUNG = 70;

    /** Lệch tối đa vẫn coi là "năm sinh gần khớp" — chép theo tuổi mụ thường lệch đúng 1–2 năm. */
    private static final int LECH_NAM_SINH_TOI_DA = 2;

    private DuplicateScorer() {
    }

    /**
     * @param probe hồ sơ đang định nhập
     * @param candidate hồ sơ đã có (hoặc một dòng khác trong cùng lô nhập liệu)
     * @return kết quả chấm điểm, rỗng khi không đáng hỏi
     */
    public static Optional<DuplicateMatch> score(PersonSignature probe, PersonSignature candidate) {
        Objects.requireNonNull(probe, "probe khong duoc null");
        Objects.requireNonNull(candidate, "candidate khong duoc null");

        NameHit nameHit = bestNameHit(probe.names(), candidate.names());
        if (nameHit == null) {
            return Optional.empty();
        }

        List<DuplicateSignal> signals = new ArrayList<>();
        signals.add(nameHit.signal());
        boolean coBangChungNgayThang = false;

        DuplicateSignal gio = gioSignal(probe.gio(), candidate.gio());
        if (gio != null) {
            signals.add(gio);
            coBangChungNgayThang = true;
        }
        DuplicateSignal namSinh = namSinhSignal(probe.birthYear(), candidate.birthYear());
        if (namSinh != null) {
            signals.add(namSinh);
            coBangChungNgayThang = true;
        }
        if (gio == null && bothPresentAndEqual(probe.deathYear(), candidate.deathYear())) {
            // Chi cong diem, KHONG tu no mo cua: hai nguoi cung ten mat cung nam la trung hop
            // hoan toan co the xay ra trong mot dong ho dong nguoi.
            signals.add(DuplicateSignal.NAM_MAT_KHOP);
        }
        if (probe.branchId() != null && probe.branchId().equals(candidate.branchId())) {
            signals.add(DuplicateSignal.CUNG_CHI);
        }
        DuplicateSignal doi = doiSignal(probe.generation(), candidate.generation());
        if (doi != null) {
            signals.add(doi);
        }
        if (isNotBlank(probe.nativePlaceUnaccented())
                && probe.nativePlaceUnaccented().equals(candidate.nativePlaceUnaccented())) {
            signals.add(DuplicateSignal.CUNG_NGUYEN_QUAN);
        }
        if (khacGioiRoRang(probe.gender(), candidate.gender())) {
            signals.add(DuplicateSignal.KHAC_GIOI);
        }

        if (!coBangChungNgayThang) {
            return Optional.empty();
        }
        int score = signals.stream().mapToInt(DuplicateSignal::points).sum();
        if (score < NGUONG_NGHI_TRUNG) {
            return Optional.empty();
        }
        return Optional.of(new DuplicateMatch(candidate.personId(), null, candidate.displayName(),
                candidate.generation(), candidate.branchId(), score, signals, nameHit.matchedName(),
                hint(signals)));
    }

    // -----------------------------------------------------------------------------------------
    // Từng tín hiệu
    // -----------------------------------------------------------------------------------------

    /**
     * Khớp tên chéo mọi lớp: tên húy của hồ sơ mới có thể trùng tên thường gọi của hồ sơ cũ, vì
     * người nhập lần hai rất hay ghi cùng một cái tên vào lớp khác. Ưu tiên khớp có dấu.
     */
    private static NameHit bestNameHit(List<NameKey> probeNames, List<NameKey> candidateNames) {
        NameHit best = null;
        for (NameKey a : probeNames) {
            for (NameKey b : candidateNames) {
                if (a.unaccented() == null || !a.unaccented().equals(b.unaccented())) {
                    continue;
                }
                if (normalize(a.raw()).equals(normalize(b.raw()))) {
                    return new NameHit(DuplicateSignal.TEN_TRUNG_CO_DAU, b.raw());
                }
                if (best == null) {
                    best = new NameHit(DuplicateSignal.TEN_TRUNG_KHONG_DAU, b.raw());
                }
            }
        }
        return best;
    }

    /**
     * Ngày giỗ: so ngày + tháng + cờ tháng nhuận, <b>bỏ qua năm</b>. Năm mất có thể chép sai hoặc
     * chỉ nhớ áng chừng, nhưng ngày giỗ thì cả họ cúng hằng năm nên rất khó sai. Bỏ cờ tháng nhuận
     * ra khỏi phép so là lệch cả tháng mà không có gì báo.
     */
    private static DuplicateSignal gioSignal(LunarDate probe, LunarDate candidate) {
        if (probe == null || candidate == null) {
            return null;
        }
        return probe.sameDayAndMonth(candidate) ? DuplicateSignal.GIO_TRUNG_KHIT : null;
    }

    private static DuplicateSignal namSinhSignal(Integer probe, Integer candidate) {
        if (probe == null || candidate == null) {
            return null;
        }
        int lech = Math.abs(probe - candidate);
        if (lech == 0) {
            return DuplicateSignal.NAM_SINH_KHOP;
        }
        return lech <= LECH_NAM_SINH_TOI_DA ? DuplicateSignal.NAM_SINH_LECH_IT : null;
    }

    private static DuplicateSignal doiSignal(Integer probe, Integer candidate) {
        if (probe == null || candidate == null) {
            return null;
        }
        return probe.equals(candidate) ? DuplicateSignal.CUNG_DOI : DuplicateSignal.KHAC_DOI;
    }

    private static boolean khacGioiRoRang(Gender a, Gender b) {
        return a != null && b != null && a != Gender.UNKNOWN && b != Gender.UNKNOWN && a != b;
    }

    private static boolean bothPresentAndEqual(Integer a, Integer b) {
        return a != null && a.equals(b);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** Gộp khoảng trắng và hạ chữ thường; <b>giữ nguyên dấu</b> — đây là phép so "có dấu". */
    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * Câu giải thích cho hộp thoại — <b>một danh sách nhãn tín hiệu, không một giá trị trường
     * nào của hồ sơ bên kia</b>.
     *
     * <h2>Vì sao không được nêu giá trị, kể cả khi giá trị ấy bằng đúng giá trị người dùng vừa gõ</h2>
     * Câu này chảy thẳng vào {@code import_issue.message} / {@code context.giaiThich} của đường
     * nhập liệu và vào thân lỗi 409 của đường thêm người, rồi <b>nằm lại trong cơ sở dữ liệu</b>.
     * Bộ dò quét toàn dòng họ, nên {@code candidate} hoàn toàn có thể là một người <b>còn sống ở
     * một chi khác</b> mà người đọc câu này không có quyền biết là tồn tại.
     *
     * <p>Cám dỗ ở đây là lập luận "năm sinh in ra bằng đúng năm sinh người dùng vừa gõ thì có rò
     * gì đâu". Lập luận ấy sai ở chỗ: <b>thứ bị tiết lộ không phải con số, mà là mối liên kết</b>
     * — "có một hồ sơ trong phả mang năm sinh 1975". Riêng {@link DuplicateSignal#NAM_SINH_LECH_IT}
     * thì còn sai cả về con số: nó in ra năm sinh <i>thật</i> của hồ sơ kia, lệch 1–2 năm so với
     * thứ người dùng gõ.</p>
     *
     * <h2>Phần rò rỉ còn lại, đã cân nhắc và chấp nhận</h2>
     * Nhãn tín hiệu vẫn nói ra <b>một bit</b> mỗi loại: "trùng ngày giỗ" nghĩa là có một hồ sơ
     * trong phả cùng ngày giỗ với dòng đang nhập. Đó là <b>phần rò rỉ không cắt được</b> — nó
     * chính là nội dung của cảnh báo. Một cảnh báo nghi trùng mà không nói được vì sao nghi thì
     * người đối chiếu không có gì để đối chiếu, và họ sẽ bấm bỏ qua theo phản xạ. Ranh giới được
     * chọn là: <b>nói trường nào của chính mình đã khớp, không bao giờ nói giá trị đọc từ phả</b>.
     *
     * <p>Nêu bằng chứng mạnh nhất trước, phản chứng sau cùng — sắp theo điểm giảm dần thay vì
     * theo thứ tự đã thu thập.</p>
     */
    private static String hint(List<DuplicateSignal> signals) {
        String ket = signals.stream()
                .sorted(Comparator.comparingInt(DuplicateSignal::points).reversed())
                .map(DuplicateScorer::nhan)
                .collect(Collectors.joining(", "));
        return ket.isEmpty() ? "trùng tên" : ket;
    }

    /**
     * Nhãn tiếng Việt của một tín hiệu. Cố ý là {@code switch} vét cạn: thêm một tín hiệu mới mà
     * quên đặt nhãn thì <b>không biên dịch được</b>, thay vì lặng lẽ rơi vào một nhánh mặc định
     * nào đó.
     */
    private static String nhan(DuplicateSignal signal) {
        return switch (signal) {
            case TEN_TRUNG_CO_DAU -> "trùng họ tên đủ dấu";
            case TEN_TRUNG_KHONG_DAU -> "trùng họ tên khi bỏ dấu";
            case GIO_TRUNG_KHIT -> "trùng ngày giỗ";
            case NAM_SINH_KHOP -> "trùng năm sinh";
            case NAM_SINH_LECH_IT -> "năm sinh lệch 1–2 năm";
            case NAM_MAT_KHOP -> "trùng năm mất";
            case CUNG_CHI -> "cùng chi";
            case CUNG_DOI -> "cùng đời";
            case CUNG_NGUYEN_QUAN -> "cùng nguyên quán";
            case KHAC_GIOI -> "nhưng khác giới tính";
            case KHAC_DOI -> "nhưng khác đời thứ";
        };
    }

    private record NameHit(DuplicateSignal signal, String matchedName) {
    }
}
