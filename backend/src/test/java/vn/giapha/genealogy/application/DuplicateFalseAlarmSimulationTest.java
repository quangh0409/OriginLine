package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.port.DuplicateCandidatePort.NameKey;
import vn.giapha.genealogy.domain.port.DuplicateCandidatePort.PersonSignature;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.LunarDate;

/**
 * <b>Đo tỉ lệ cảnh báo giả</b> trên một dòng họ mô phỏng đúng quy mô bộ dữ liệu demo của Giai đoạn
 * 1 (§10 của kế hoạch): 1.506 nhân khẩu · 7 đời · 4 chi · 12 nhánh · khoảng 60% đã khuất.
 *
 * <h2>Vì sao phải đo chứ không ước</h2>
 * "Cảnh báo quá tay còn tệ hơn không cảnh báo" là một khẳng định định lượng: nếu cứ vài chục lần
 * thêm người lại có một lần bị hỏi oan, người nhập liệu sẽ học được rằng hộp thoại đó vô nghĩa và
 * bấm "vẫn ghi" theo phản xạ — kể cả đúng lần nó nói thật. Vậy nên ngưỡng phải được kiểm bằng một
 * con số nằm trong CI, để lần sau ai nới trọng số thì test đỏ ngay.
 *
 * <h2>Mô hình dựng cái gì, và giả định nào là quan trọng nhất</h2>
 * <ul>
 *   <li>Một họ duy nhất, <b>chữ đệm theo đời</b> (mô phỏng bài thơ đặt tên) — cả một đời chỉ khác
 *       nhau đúng ở tên chính.</li>
 *   <li><b>Không đặt trùng tên người cùng đời trong cùng một chi.</b> Đây là giả định quan trọng
 *       nhất của mô hình và nó phản ánh tập quán thật: cha mẹ biết rõ tên con của anh em ruột và
 *       anh em con chú con bác, và tránh đặt trùng. Ngược lại, dùng lại một cái tên đẹp <b>sau vài
 *       đời</b> hoặc ở <b>chi khác</b> thì rất phổ biến — mô hình cho phép thoải mái.</li>
 *   <li>Vốn tên chính hữu hạn, đúng tập quán dùng lại tên đẹp.</li>
 *   <li>Các đời trên có thêm tên húy / tên tự — càng nhiều lớp tên thì cửa lọc theo tên càng hay
 *       trúng, tức càng bất lợi cho tỉ lệ cảnh báo giả.</li>
 *   <li>Người đã khuất có ngày giỗ âm lịch, nên có cả những trùng hợp giỗ ngẫu nhiên (xác suất
 *       khoảng 1/354 mỗi cặp).</li>
 * </ul>
 *
 * <p>Mọi nhân khẩu sinh ra ở đây là <b>người khác nhau</b> theo đúng cách dựng, nên mỗi cảnh báo
 * đếm được đều là cảnh báo giả. Seed cố định để con số lặp lại được.</p>
 *
 * <p><b>Mô hình cố ý bi quan</b> ở hai chỗ, nên con số đo được là cận trên chứ không phải kỳ vọng:
 * đời thứ 7 dồn hơn một nghìn người vào một khoảng sinh 35 năm, và vốn tên chính chỉ khoảng một
 * trăm lựa chọn (đời sau thực tế còn hay dùng tên chính hai âm tiết, tức phong phú hơn nhiều).
 * Cả hai đều làm số cặp trùng tên trong cùng một đời cao hơn thực tế.</p>
 */
class DuplicateFalseAlarmSimulationTest {

    private static final Logger log = LoggerFactory.getLogger(DuplicateFalseAlarmSimulationTest.class);

    private static final int SO_NHAN_KHAU = 1506;
    private static final int SO_DOI = 7;
    private static final int SO_NHANH = 12;
    private static final long SEED = 19_450_902L;

    /**
     * Trần tỉ lệ cảnh báo giả, đặt sát ngay trên giá trị đo được để mọi lần nới trọng số đều làm
     * test đỏ.
     *
     * <p>Đo được với bộ trọng số hiện tại: <b>16/1506 = 1,06%</b>, tức khoảng một lần bị hỏi oan
     * trên 94 lần thêm người. Ngưỡng đau của người dùng nằm ở khoảng một trên hai mươi — quá mức đó
     * thì hộp thoại bị bấm qua theo phản xạ và cảnh báo mất sạch giá trị.</p>
     */
    private static final double TRAN_CANH_BAO_GIA = 0.015;

    private static final String HO = "Nguyễn";

    /** Chữ đệm theo đời — mô phỏng bài thơ đặt tên của dòng họ. */
    private static final String[] DEM_NAM = {"Phúc", "Hữu", "Văn", "Đình", "Công", "Bá", "Xuân"};
    private static final String[] DEM_NU = {"Thị", "Thị", "Thị", "Thanh", "Thị", "Ngọc", "Thị"};

    private static final String[] TEN_NAM = {"Tuấn", "Hùng", "Sơn", "Cường", "Minh", "Đức", "Long",
            "Thắng", "Dũng", "Quang", "Nam", "Hải", "Bảo", "An", "Khánh", "Trung", "Kiên", "Phong",
            "Tùng", "Việt", "Chiến", "Thành", "Tâm", "Hiếu", "Nghĩa", "Trí", "Lâm", "Đạt", "Hoà",
            "Vinh", "Lộc", "Thọ", "Phúc", "Toàn", "Nhân", "Tài", "Tiến", "Bằng", "Cẩn", "Doanh",
            "Giang", "Huy", "Khôi", "Lợi", "Mạnh", "Ngọc", "Oanh", "Phú", "Quý", "Sáng", "Tuyên",
            "Ước", "Vũ", "Xuyên", "Yên", "Bích", "Chương", "Diệu", "Định", "Điền"};
    private static final String[] TEN_NU = {"Lan", "Hoa", "Mai", "Hương", "Thuý", "Nga", "Hằng",
            "Yến", "Trang", "Loan", "Phượng", "Nhung", "Xuân", "Thảo", "Vân", "Hạnh", "Nhàn",
            "Duyên", "Tuyết", "Bích", "Cúc", "Đào", "Gấm", "Huệ", "Kim", "Liên", "Minh", "Nguyệt",
            "Oanh", "Phúc", "Quyên", "Sen", "Tâm", "Uyên", "Vinh", "Xoan", "Ý", "Bình", "Chi",
            "Dung", "Giang", "Hà", "Khuê", "Lệ", "My"};

    private static final String[] NGUYEN_QUAN = {"Bắc Ninh", "Hà Nội", "Hưng Yên", "Nam Định"};

    @Test
    @DisplayName("Tỉ lệ cảnh báo giả trên dòng họ 1.506 người phải dưới 1,5%")
    void tiLeCanhBaoGiaDuNho() {
        List<PersonSignature> dongHo = sinhDongHo();
        assertThat(dongHo).hasSize(SO_NHAN_KHAU);

        int soLanBiHoi = 0;
        int tongCanhBao = 0;
        java.util.Map<String, Integer> tanSuat = new java.util.TreeMap<>();
        for (int i = 0; i < dongHo.size(); i++) {
            int canhBao = demCanhBao(dongHo, i, tanSuat);
            tongCanhBao += canhBao;
            if (canhBao > 0) {
                soLanBiHoi++;
            }
        }
        tanSuat.forEach((combo, dem) -> log.info("  canh bao gia: {} x {}", dem, combo));
        double tiLe = (double) soLanBiHoi / dongHo.size();
        log.info("Mo phong do trung: {}/{} lan them nguoi bi hoi oan ({}%), tong {} canh bao gia",
                soLanBiHoi, dongHo.size(), String.format("%.2f", tiLe * 100), tongCanhBao);

        assertThat(tiLe)
                .as("canh bao qua tay con te hon khong canh bao: %d/%d lan them nguoi bi hoi oan",
                        soLanBiHoi, dongHo.size())
                .isLessThan(TRAN_CANH_BAO_GIA);
    }

    @Test
    @DisplayName("Vẫn bắt được bản trùng thật cài vào giữa 1.506 người")
    void vanBatDuocBanTrungThat() {
        List<PersonSignature> dongHo = sinhDongHo();

        int batDuoc = 0;
        int soCaThu = 0;
        for (int i = 0; i < dongHo.size(); i += 17) {
            PersonSignature goc = dongHo.get(i);
            if (goc.birthYear() == null && goc.gio() == null) {
                continue;
            }
            soCaThu++;
            PersonSignature nhapLai = nhapLaiLechMotChut(goc);
            if (dongHo.stream().anyMatch(other -> DuplicateScorer.score(nhapLai, other).isPresent())) {
                batDuoc++;
            }
        }
        log.info("Mo phong do trung: bat duoc {}/{} ban trung that", batDuoc, soCaThu);
        assertThat(soCaThu).isGreaterThan(50);
        assertThat((double) batDuoc / soCaThu)
                .as("ban trung that phai bi bat gan nhu chac chan")
                .isGreaterThan(0.95);
    }

    // -------------------------------------------------------------------------------------
    // Mô phỏng
    // -------------------------------------------------------------------------------------

    private int demCanhBao(List<PersonSignature> dongHo, int viTri,
                           java.util.Map<String, Integer> tanSuat) {
        PersonSignature moi = dongHo.get(viTri);
        int canhBao = 0;
        for (int j = 0; j < dongHo.size(); j++) {
            if (j == viTri) {
                continue;
            }
            var match = DuplicateScorer.score(moi, dongHo.get(j));
            if (match.isPresent()) {
                canhBao++;
                tanSuat.merge(match.get().signals().toString(), 1, Integer::sum);
            }
        }
        return canhBao;
    }

    /**
     * Cùng một người được nhập lần thứ hai từ một cuốn gia phả giấy khác: tên gõ không dấu, năm
     * sinh lệch một năm (chép theo tuổi mụ) — nhưng ngày giỗ thì vẫn đúng, vì cả họ cúng ngày đó.
     */
    private PersonSignature nhapLaiLechMotChut(PersonSignature goc) {
        List<NameKey> names = goc.names().stream()
                .map(n -> new NameKey(n.type(), FakeDuplicateCandidatePort.boDau(n.raw()),
                        n.unaccented()))
                .toList();
        Integer namSinh = goc.birthYear() == null ? null : goc.birthYear() + 1;
        return new PersonSignature(null, goc.displayName(), names, goc.gender(), goc.generation(),
                goc.branchId(), namSinh, goc.deathYear(), goc.gio(), goc.nativePlaceUnaccented());
    }

    private List<PersonSignature> sinhDongHo() {
        Random rnd = new Random(SEED);
        List<UUID> nhanh = new ArrayList<>();
        for (int i = 0; i < SO_NHANH; i++) {
            nhanh.add(new UUID(SEED, i));
        }
        // Ten da dung trong cung mot (nhanh, doi) — khong dat trung, dung tap quan that.
        Set<String> daDung = new HashSet<>();
        List<PersonSignature> ket = new ArrayList<>(SO_NHAN_KHAU);
        for (int i = 0; i < SO_NHAN_KHAU; i++) {
            ket.add(sinhMotNguoi(rnd, nhanh, i, daDung));
        }
        return ket;
    }

    private PersonSignature sinhMotNguoi(Random rnd, List<UUID> nhanh, int thuTu, Set<String> daDung) {
        int doi = doiThu(thuTu);
        UUID chi = nhanh.get(rnd.nextInt(nhanh.size()));
        boolean nam = rnd.nextDouble() < 0.52;
        String dem = nam ? DEM_NAM[doi - 1] : DEM_NU[doi - 1];
        String[] von = nam ? TEN_NAM : TEN_NU;

        String ten = null;
        for (int thu = 0; thu < 60 && ten == null; thu++) {
            String ungVien = von[rnd.nextInt(von.length)];
            if (daDung.add(chi + "|" + doi + "|" + dem + "|" + ungVien)) {
                ten = ungVien;
            }
        }
        if (ten == null) {
            ten = von[rnd.nextInt(von.length)];
        }
        String hoTen = HO + " " + dem + " " + ten;

        List<NameKey> names = new ArrayList<>();
        names.add(nameKey(NameType.THUONG_GOI, hoTen));
        if (doi <= 4) {
            // Doi tren co them ten huy va ten tu.
            names.add(nameKey(NameType.HUY, HO + " " + dem + " " + von[rnd.nextInt(von.length)]));
            if (rnd.nextDouble() < 0.4) {
                names.add(nameKey(NameType.TU, HO + " " + von[rnd.nextInt(von.length)]));
            }
        }

        // Doi 1 khoang 1700, moi doi cach nhau ~28 nam, nhung mot doi trai dai gan 35 nam vi anh ca
        // va em ut cua mot nha da cach nhau ca chuc nam, cong them lech giua cac nhanh.
        int namSinhGoc = 1700 + (doi - 1) * 28 + rnd.nextInt(35);
        // Gia pha that luon thieu du lieu: doi cang xa thi cang it con nam sinh.
        boolean coNamSinh = rnd.nextDouble() < (doi <= 2 ? 0.45 : doi <= 4 ? 0.7 : 0.95);
        Integer namSinh = coNamSinh ? namSinhGoc : null;

        boolean daKhuat = doi <= 5 || rnd.nextDouble() < 0.35;
        Integer namMat = daKhuat ? namSinhGoc + 45 + rnd.nextInt(45) : null;
        LunarDate gio = daKhuat && rnd.nextDouble() < 0.85
                ? new LunarDate(namMat, 1 + rnd.nextInt(12), 1 + rnd.nextInt(30), false) : null;

        return new PersonSignature(new UUID(0xC0FFEEL, thuTu), hoTen, names,
                nam ? Gender.MALE : Gender.FEMALE, doi, chi, namSinh, namMat, gio,
                FakeDuplicateCandidatePort.boDau(NGUYEN_QUAN[rnd.nextInt(NGUYEN_QUAN.length)]));
    }

    /** Hình tháp: mỗi đời đông gấp ba đời trước, thuỷ tổ đúng một người, đời cuối lấy phần còn lại. */
    private int doiThu(int thuTu) {
        int con = 1;
        int daQua = 0;
        for (int doi = 1; doi <= SO_DOI; doi++) {
            int soNguoiDoiNay = doi == SO_DOI ? SO_NHAN_KHAU : con;
            if (thuTu < daQua + soNguoiDoiNay) {
                return doi;
            }
            daQua += soNguoiDoiNay;
            con = Math.max(2, (int) Math.round(con * 3.1));
        }
        return SO_DOI;
    }

    private static NameKey nameKey(NameType type, String raw) {
        return new NameKey(type, raw, FakeDuplicateCandidatePort.boDau(raw));
    }
}
