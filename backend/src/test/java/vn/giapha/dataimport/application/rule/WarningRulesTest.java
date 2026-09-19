package vn.giapha.dataimport.application.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.giapha.dataimport.application.rule.RuleFixtures.codes;
import static vn.giapha.dataimport.application.rule.RuleFixtures.ctx;
import static vn.giapha.dataimport.application.rule.RuleFixtures.of;
import static vn.giapha.dataimport.application.rule.RuleFixtures.row;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.IssueSeverity;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.port.DuplicateScanPort;
import vn.giapha.dataimport.domain.port.PlaceCodePort;
import vn.giapha.dataimport.domain.port.TabooScanPort;

/**
 * Nhóm <b>cảnh báo</b>: nghi trùng người · trùng tên huý bậc trên · thiếu ngày giỗ.
 *
 * <p>Điểm chung phải giữ bằng mọi giá: <b>không cái nào chặn</b>. Gộp chúng vào nhóm lỗi chặn thì
 * "4 lỗi phải sửa" biến thành "15 vấn đề", và người nhập chuyển từ "làm được" sang "hỏng cả tệp
 * rồi".</p>
 */
class WarningRulesTest {

    @Nested
    @DisplayName("Thiếu ngày giỗ")
    class ThieuGio {

        private final LifeStatusRule rule = new LifeStatusRule();

        @Test
        @DisplayName("Đã mất mà trống ngày giỗ: CẢNH BÁO, và nói rõ hậu quả")
        void daMatMaTrongNgayGio() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-01-001", "Nguyễn Văn Cẩn").conSong(false).build()));
            rule.apply(ctx);

            List<ImportIssue> issues = of(ctx, IssueCode.IMP_MISSING_GIO);
            assertThat(issues).hasSize(1);
            assertThat(issues.get(0).severity()).isEqualTo(IssueSeverity.WARNING);
            assertThat(issues.get(0).message()).contains("không bao giờ được nhắc giỗ");
        }

        @Test
        @DisplayName("Người còn sống không bị đòi ngày giỗ")
        void nguoiConSong() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-05-001", "Nguyễn Văn Trẻ").conSong(true).build()));
            rule.apply(ctx);
            assertThat(codes(ctx)).doesNotContain(IssueCode.IMP_MISSING_GIO);
        }

        @Test
        @DisplayName("Còn sống = Có nhưng có ngày giỗ là LỖI CHẶN, khớp ck_person_alive_vs_death")
        void conSongMaCoNgayGio() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-05-001", "X").conSong(true).ngayMatAm("15/8").build()));
            rule.apply(ctx);

            List<ImportIssue> issues = of(ctx, IssueCode.IMP_ALIVE_WITH_DEATH);
            assertThat(issues).hasSize(1);
            assertThat(issues.get(0).severity()).isEqualTo(IssueSeverity.BLOCKING);
        }

        @Test
        @DisplayName("Giỗ ngày 30 không rõ năm: cảnh báo để dòng họ chốt tục lệ")
        void gioNgay30KhongRoNam() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-01-001", "Cụ").conSong(false).ngayMatAm("30/8").build()));
            rule.apply(ctx);
            assertThat(of(ctx, IssueCode.IMP_GIO_DAY_30)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Nghi trùng người")
    class NghiTrung {

        /**
         * Ca then chốt về riêng tư, ở <b>tầng sinh ra dữ liệu</b>.
         *
         * <p>Bộ dò quét toàn dòng họ, nên hồ sơ bị nghi có thể là người <b>còn sống ở một chi
         * khác</b> mà người nhập không có quyền biết là tồn tại. Cảnh báo này được ghi thẳng vào
         * {@code import_issue.message}, nên nhét tên vào đó là ghi vĩnh viễn dữ liệu người khác
         * vào một bảng không có bộ lọc riêng tư nào canh.</p>
         *
         * <p><b>Tên trong tệp và tên trong phả cố ý KHÁC NHAU.</b> Nếu để chúng trùng nhau — như
         * bản test cũ — thì phép kiểm "không lộ tên" xanh vì lý do sai: cái tên vẫn hiện, chỉ là
         * nó đến từ {@code row.nhan()}.</p>
         */
        @Test
        @DisplayName("Bên bị nghi đã có trong phả: chỉ khoá, không tên/đời — nhưng vẫn đủ để hành động")
        void benTrongPha_chiKhoa() {
            UUID nguoiTrongPha = UUID.randomUUID();
            DuplicateScanPort port = rows -> List.of(new DuplicateScanPort.KetQua("AT-05-001",
                    List.of(new DuplicateScanPort.NghiNgo(nguoiTrongPha, null,
                            "Nguyễn Thị Lựu", 5, 78, "trùng ngày giỗ, cùng chi"))));

            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-05-001", "Nguyễn Văn Cẩn").doi(5).build()));
            new SuspectDuplicateRule(port).apply(ctx);

            List<ImportIssue> issues = of(ctx, IssueCode.IMP_SUSPECT_DUPLICATE);
            assertThat(issues).hasSize(1);
            ImportIssue issue = issues.get(0);
            assertThat(issue.severity()).isEqualTo(IssueSeverity.WARNING);

            // --- Khong mot manh du lieu nhan khau nao cua nguoi trong pha ---
            assertThat(issue.message()).doesNotContain("Nguyễn Thị Lựu");
            assertThat(issue.context().toString()).doesNotContain("Nguyễn Thị Lựu");

            // --- ...nhung van dung duoc: dong nao, may diem, khop o dau, lam gi tiep ---
            assertThat(issue.message())
                    .contains("AT-05-001", "Nguyễn Văn Cẩn")   // du lieu cua CHINH nguoi nhap
                    .contains("1 hồ sơ đã có trong phả")
                    .contains("78")
                    .contains("trùng ngày giỗ, cùng chi")
                    .contains("Hội đồng Tộc biểu");

            Map<String, Object> ungVien = dauTien(issue);
            assertThat(ungVien).containsOnlyKeys("personId", "diem", "nguon", "tinHieu");
            assertThat(ungVien.get("personId")).isEqualTo(nguoiTrongPha.toString());
            assertThat(ungVien.get("nguon")).isEqualTo("TREE");
        }

        /**
         * Đối chứng — bài kiểm trên vô nghĩa nếu thiếu ca này: khi bên bị nghi cũng là một dòng
         * <b>trong chính tệp ấy</b> thì tên <b>vẫn phải hiện</b>. Đó là dữ liệu của chính người
         * nhập, và giấu đi chỉ làm họ không đối chiếu được.
         */
        @Test
        @DisplayName("Đối chứng: bên bị nghi là dòng khác TRONG CÙNG TỆP thì nêu tên thoải mái")
        void benTrongTep_venNguyenTen() {
            DuplicateScanPort port = rows -> List.of(new DuplicateScanPort.KetQua("AT-05-001",
                    List.of(new DuplicateScanPort.NghiNgo(null, "AT-04-003",
                            "Nguyễn Văn Phả", 4, 91, "trùng ngày giỗ"))));

            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-05-001", "Nguyễn Văn Cẩn").doi(5).build()));
            new SuspectDuplicateRule(port).apply(ctx);

            ImportIssue issue = of(ctx, IssueCode.IMP_SUSPECT_DUPLICATE).get(0);
            assertThat(issue.message())
                    .contains("AT-04-003", "Nguyễn Văn Phả", "91 điểm", "trong chính tệp này");
            // Khong co ve "trong pha" thi khong dan them cau ve rieng tu.
            assertThat(issue.message()).doesNotContain("hồ sơ đã có trong phả");

            Map<String, Object> ungVien = dauTien(issue);
            assertThat(ungVien).containsKeys("ref", "ten", "doi", "diem", "giaiThich");
            assertThat(ungVien.get("nguon")).isEqualTo("FILE");
        }

        @Test
        @DisplayName("Trộn hai loại trong một dòng: chỉ vế trong tệp được nêu tên")
        void tronHaiLoai() {
            DuplicateScanPort port = rows -> List.of(new DuplicateScanPort.KetQua("AT-05-001",
                    List.of(new DuplicateScanPort.NghiNgo(null, "AT-04-003", "Nguyễn Văn Phả", 4,
                                    91, "trùng ngày giỗ"),
                            new DuplicateScanPort.NghiNgo(UUID.randomUUID(), null,
                                    "Nguyễn Thị Lựu", 5, 78, "trùng năm sinh"))));

            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-05-001", "Nguyễn Văn Cẩn").doi(5).build()));
            new SuspectDuplicateRule(port).apply(ctx);

            ImportIssue issue = of(ctx, IssueCode.IMP_SUSPECT_DUPLICATE).get(0);
            assertThat(issue.message())
                    .contains("Nguyễn Văn Phả")
                    .doesNotContain("Nguyễn Thị Lựu");
            assertThat(issue.context().toString()).doesNotContain("Nguyễn Thị Lựu");
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> dauTien(ImportIssue issue) {
            List<Map<String, Object>> ds =
                    (List<Map<String, Object>>) issue.context().get("nghiNgo");
            assertThat(ds).isNotEmpty();
            return ds.get(0);
        }

        @Test
        @DisplayName("Không nghi ngờ gì thì im lặng — đó là kết quả mong đợi của đại đa số hồ sơ")
        void khongNghiNgo() {
            DuplicateScanPort port = rows -> rows.stream()
                    .map(r -> new DuplicateScanPort.KetQua(r.ref(), List.of()))
                    .toList();
            ValidationContext ctx = ctx(List.of(row(2, "AT-05-001", "Nguyễn Văn A").build()));
            new SuspectDuplicateRule(port).apply(ctx);
            assertThat(codes(ctx)).isEmpty();
        }

        @Test
        @DisplayName("Dòng đang cập nhật người đã có thì loại chính người ấy khỏi phép dò")
        void loaiChinhMinh() {
            // Khong lam viec nay thi moi lan tai lai se bao 400 dong "trung voi chinh minh".
            UUID daCo = UUID.randomUUID();
            List<UUID> daLoaiTru = new ArrayList<>();
            DuplicateScanPort port = rows -> {
                rows.forEach(r -> daLoaiTru.add(r.excludePersonId()));
                return rows.stream()
                        .map(r -> new DuplicateScanPort.KetQua(r.ref(), List.of()))
                        .toList();
            };

            List<PersonRow> rows = List.of(row(2, "AT-05-001", "Nguyễn Văn A").build());
            ValidationContext ctx = RuleFixtures.ctxTaiLai(rows, Map.of("AT-05-001", daCo));
            new SuspectDuplicateRule(port).apply(ctx);

            assertThat(daLoaiTru).containsExactly(daCo);
        }
    }

    @Nested
    @DisplayName("Kỵ húy")
    class KyHuy {

        /**
         * Phép dò chọn bậc trên theo <b>đời thứ</b>, không theo {@code is_alive}: "bậc trên" hoàn
         * toàn có thể là một ông bác còn sống ở chi khác. Cổng không mang cờ sống/mất, nên luật là
         * <b>an toàn khi không biết</b> — chỉ trả khoá.
         *
         * <p>Bẫy được ghim ở đây: {@code KetQua.tabooName()} là {@code person_name.full_name}
         * <b>đọc từ phả</b> (phép dò khớp cả ở mức tên chính, nên "Lựu" khớp "Nguyễn Phúc Lựu").
         * Câu thông báo phải dùng ô Tên huý của <b>chính dòng đang nhập</b>.</p>
         */
        @Test
        @DisplayName("Trùng tên huý bậc trên: chỉ khoá, không tên và không đời của bậc trên")
        void trungTenHuyBacTren_chiKhoa() {
            UUID bacTren = UUID.randomUUID();
            TabooScanPort port = rows -> List.of(new TabooScanPort.KetQua("AT-07-001",
                    "Nguyễn Phúc Lựu", bacTren, "Nguyễn Phúc Thuỷ Tổ", 3));

            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-07-001", "Nguyễn Văn Cẩn").tenHuy("Lựu").doi(7).build()));
            new TabooCollisionRule(port).apply(ctx);

            List<ImportIssue> issues = of(ctx, IssueCode.IMP_TABOO_COLLISION);
            assertThat(issues).hasSize(1);
            ImportIssue issue = issues.get(0);
            assertThat(issue.severity()).isEqualTo(IssueSeverity.WARNING);

            String tatCa = issue.message() + " " + issue.context();
            assertThat(tatCa)
                    .doesNotContain("Nguyễn Phúc Thuỷ Tổ")   // ten bac tren
                    .doesNotContain("Nguyễn Phúc Lựu")       // ten huy DOC TU PHA
                    .doesNotContain("đời 3");                // doi thu bac tren

            // ...nhung van hanh dong duoc: biet ten huy NAO va dong NAO va cham.
            assertThat(issue.message()).contains("Tên huý Lựu", "AT-07-001", "Nguyễn Văn Cẩn");
            assertThat(issue.context()).containsOnlyKeys("tenHuy", "bacTrenId");
            assertThat(issue.context().get("tenHuy")).isEqualTo("Lựu");
            assertThat(issue.context().get("bacTrenId")).isEqualTo(bacTren.toString());
        }

        @Test
        @DisplayName("Dòng không có tên huý thì không gọi tới bộ dò")
        void khongCoTenHuy() {
            List<TabooScanPort.UngVien> daGoi = new ArrayList<>();
            TabooScanPort port = rows -> {
                daGoi.addAll(rows);
                return List.of();
            };
            ValidationContext ctx = ctx(List.of(row(2, "AT-07-001", "Nguyễn Văn A").build()));
            new TabooCollisionRule(port).apply(ctx);
            assertThat(daGoi).isEmpty();
        }
    }

    @Nested
    @DisplayName("Độ đầy đủ")
    class DoDayDu {

        @Test
        @DisplayName("Người đứng một mình bị cảnh báo, nhưng thuỷ tổ có con thì không")
        void nguoiDungMotMinh() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-01-001", "Thuỷ tổ").doi(1).build(),
                    row(3, "AT-02-001", "Con").doi(2).cha("AT-01-001").me("AT-01-002").build(),
                    row(4, "AT-09-999", "Người lạc").doi(9).build()));
            new CompletenessRule().apply(ctx);

            List<ImportIssue> issues = of(ctx, IssueCode.IMP_LONE_NODE);
            assertThat(issues).hasSize(1);
            assertThat(issues.get(0).rowNo()).isEqualTo(4);
        }

        @Test
        @DisplayName("Có cha mà không có mẹ: đếm được để đo độ đầy đủ của bên ngoại")
        void thieuMe() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-02-001", "Con").cha("AT-01-001").build()));
            new CompletenessRule().apply(ctx);
            assertThat(of(ctx, IssueCode.IMP_MISSING_MOTHER)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Mã tỉnh")
    class MaTinh {

        @Test
        @DisplayName("Danh mục còn rỗng thì luật TỰ TẮT, không bắn 400 cảnh báo")
        void danhMucRongThiTuTat() {
            PlaceCodePort rong = Set::of;
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-01-001", "A").nguyenQuan("Hà Nội", "99").build()));
            new PlaceCodeRule(rong).apply(ctx);
            assertThat(codes(ctx)).isEmpty();
        }

        @Test
        @DisplayName("Mã không có trong danh mục: cảnh báo, không chặn")
        void maKhongCoTrongDanhMuc() {
            PlaceCodePort port = () -> Set.of("01", "VN");
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-01-001", "A").nguyenQuan("Hà Nội", "99").build()));
            new PlaceCodeRule(port).apply(ctx);

            List<ImportIssue> issues = of(ctx, IssueCode.IMP_UNKNOWN_PLACE_CODE);
            assertThat(issues).hasSize(1);
            assertThat(issues.get(0).severity()).isEqualTo(IssueSeverity.WARNING);
        }

        @Test
        @DisplayName("Có nguyên quán bằng chữ mà thiếu mã: cảnh báo, vì dòng này không vào được báo cáo")
        void coChuMaThieuMa() {
            PlaceCodePort port = () -> Set.of("01", "VN");
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-01-001", "A").nguyenQuan("Làng Đông Ngạc", null).build()));
            new PlaceCodeRule(port).apply(ctx);
            assertThat(of(ctx, IssueCode.IMP_MISSING_PLACE_CODE)).hasSize(1);
        }
    }
}
