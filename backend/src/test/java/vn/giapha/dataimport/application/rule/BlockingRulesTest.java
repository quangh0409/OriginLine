package vn.giapha.dataimport.application.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.giapha.dataimport.application.rule.RuleFixtures.codes;
import static vn.giapha.dataimport.application.rule.RuleFixtures.ctx;
import static vn.giapha.dataimport.application.rule.RuleFixtures.of;
import static vn.giapha.dataimport.application.rule.RuleFixtures.row;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.IssueSeverity;
import vn.giapha.dataimport.domain.MarriageRow;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.port.AnchorAncestorPort;
import vn.giapha.dataimport.domain.port.LunarDatePort;

/**
 * Năm nhóm <b>lỗi chặn</b> mà đợt này phải bắt được: mã cha/mẹ không tồn tại · vòng lặp tổ tiên ·
 * con sinh trước cha · ngày âm không tồn tại · mã trùng trong tệp.
 */
class BlockingRulesTest {

    @Nested
    @DisplayName("Mã cha/mẹ không tồn tại")
    class MaChaKhongTonTai {

        private final ParentReferenceRule rule = new ParentReferenceRule();

        @Test
        @DisplayName("Báo lỗi chặn kèm GỢI Ý mã gần giống trong tệp")
        void baoLoiKemGoiY() {
            List<PersonRow> rows = List.of(
                    row(2, "AT-04-003", "Nguyễn Văn Cẩn").doi(4).build(),
                    // Go nham chu O thanh so 0 — mat nguoi khong phan biet noi.
                    row(3, "AT-05-001", "Nguyễn Văn Đức").doi(5).cha("AT-04-O03").build());

            ValidationContext ctx = ctx(rows);
            rule.apply(ctx);

            List<ImportIssue> issues = of(ctx, IssueCode.IMP_PARENT_NOT_FOUND);
            assertThat(issues).hasSize(1);
            assertThat(issues.get(0).severity()).isEqualTo(IssueSeverity.BLOCKING);
            assertThat(issues.get(0).rowNo()).isEqualTo(3);
            assertThat(issues.get(0).context().get("goiY")).isEqualTo(List.of("AT-04-003"));
            assertThat(issues.get(0).message()).contains("AT-04-003", "Nguyễn Văn Cẩn");
        }

        @Test
        @DisplayName("Mã cha đã có trong phả (không có trong tệp) thì KHÔNG báo lỗi")
        void maChaDaCoTrongPha() {
            List<PersonRow> rows = List.of(
                    row(2, "AT-05-001", "Nguyễn Văn Đức").doi(5).cha("AT-04-003").build());
            ValidationContext ctx = RuleFixtures.ctxTaiLai(rows,
                    Map.of("AT-04-003", UUID.randomUUID()));
            rule.apply(ctx);
            assertThat(codes(ctx)).doesNotContain(IssueCode.IMP_PARENT_NOT_FOUND);
        }

        @Test
        @DisplayName("Khai chính mình là cha của mình")
        void tuLamChaMinh() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-01-001", "Nguyễn Văn Cẩn").cha("AT-01-001").build()));
            rule.apply(ctx);
            assertThat(codes(ctx)).contains(IssueCode.IMP_SELF_PARENT);
        }

        @Test
        @DisplayName("Thuỷ tổ không khai cha mẹ thì không báo gì")
        void thuyToKhongKhaiChaMe() {
            ValidationContext ctx = ctx(List.of(row(2, "AT-01-001", "Thuỷ tổ").doi(1).build()));
            rule.apply(ctx);
            assertThat(codes(ctx)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Vòng lặp tổ tiên")
    class VongLap {

        /** Đồ thị đã có sẵn rỗng — mọi vòng lặp trong các ca này nằm trọn trong tệp. */
        private final AnchorAncestorPort khongCoMoNeo = (id, depth) -> List.of();

        @Test
        @DisplayName("Vòng lặp trong tệp: đúng MỘT lỗi, thông điệp chứa đủ chuỗi mã")
        void vongLapTrongTep() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-05-012", "A").cha("AT-04-003").build(),
                    row(3, "AT-04-003", "B").cha("AT-05-012").build()));

            new CycleRule(khongCoMoNeo).apply(ctx);

            List<ImportIssue> issues = of(ctx, IssueCode.IMP_CYCLE);
            assertThat(issues).hasSize(1);
            assertThat(issues.get(0).message()).contains("AT-05-012", "AT-04-003");
            assertThat(issues.get(0).severity()).isEqualTo(IssueSeverity.BLOCKING);
        }

        @Test
        @DisplayName("Cây bình thường ba đời: không lỗi nào")
        void cayBinhThuong() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-01-001", "Cụ").build(),
                    row(3, "AT-02-001", "Ông").cha("AT-01-001").build(),
                    row(4, "AT-03-001", "Cháu").cha("AT-02-001").build()));
            new CycleRule(khongCoMoNeo).apply(ctx);
            assertThat(codes(ctx)).doesNotContain(IssueCode.IMP_CYCLE);
        }

        @Test
        @DisplayName("Vòng lặp XUYÊN BIÊN qua một người đã có trong phả")
        void vongLapXuyenBien() {
            UUID cuTo = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
            UUID trungGian = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");

            // Trong tep: AT-09-001 khai cha la cu to da co trong pha.
            List<PersonRow> rows = List.of(
                    row(2, "AT-09-001", "Nguyễn Văn X").cha("AT-08-001").build());
            ValidationContext ctx = RuleFixtures.ctxTaiLai(rows, Map.of(
                    "AT-08-001", cuTo, "AT-09-001", trungGian));

            // Do thi that: cu to lai la hau due cua chinh AT-09-001.
            AnchorAncestorPort moNeo = (id, depth) -> {
                if (cuTo.equals(id)) {
                    return List.of(new AnchorAncestorPort.Canh(cuTo, trungGian));
                }
                return List.of();
            };

            new CycleRule(moNeo).apply(ctx);
            assertThat(codes(ctx)).contains(IssueCode.IMP_CYCLE);
        }
    }

    @Nested
    @DisplayName("Con sinh trước cha, và đời khai lệch")
    class ThuTuThoiGian {

        private final ChronologyRule rule = new ChronologyRule();

        @Test
        @DisplayName("Con sinh trước cha là lỗi chặn")
        void conSinhTruocCha() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-01-001", "Cha").doi(1).namSinh(1920).build(),
                    row(3, "AT-02-001", "Con").doi(2).cha("AT-01-001").namSinh(1900).build()));
            rule.apply(ctx);

            List<ImportIssue> issues = of(ctx, IssueCode.IMP_CHILD_BEFORE_PARENT);
            assertThat(issues).hasSize(1);
            assertThat(issues.get(0).rowNo()).isEqualTo(3);
            assertThat(issues.get(0).context()).containsEntry("namSinhCon", 1900);
        }

        @Test
        @DisplayName("Cha sinh con năm 15 tuổi KHÔNG bị bắt — mục tiêu là chép nhầm, không phải phán xét")
        void chaTre() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-01-001", "Cha").namSinh(1900).build(),
                    row(3, "AT-02-001", "Con").cha("AT-01-001").namSinh(1915).build()));
            rule.apply(ctx);
            assertThat(codes(ctx)).doesNotContain(IssueCode.IMP_CHILD_BEFORE_PARENT);
        }

        @Test
        @DisplayName("Thiếu năm sinh một bên thì im lặng — sổ cũ thiếu năm là chuyện thường")
        void thieuNamSinh() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-01-001", "Cha").build(),
                    row(3, "AT-02-001", "Con").cha("AT-01-001").namSinh(1915).build()));
            rule.apply(ctx);
            assertThat(codes(ctx)).isEmpty();
        }

        @Test
        @DisplayName("Đời khai khác đời suy ra thì bắt người nhập hoà giải, không lặng lẽ sửa")
        void doiLech() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-04-001", "Cha").doi(4).build(),
                    row(3, "AT-06-001", "Con").doi(6).cha("AT-04-001").build()));
            rule.apply(ctx);

            List<ImportIssue> issues = of(ctx, IssueCode.IMP_GENERATION_MISMATCH);
            assertThat(issues).hasSize(1);
            assertThat(issues.get(0).context()).containsEntry("doiKhai", 6)
                    .containsEntry("doiSuyRa", 5);
        }
    }

    @Nested
    @DisplayName("Ngày âm không tồn tại")
    class NgayAm {

        /** Giả lập lịch âm: tháng 2 năm 1945 là tháng thiếu, và năm ấy không nhuận tháng 8. */
        private final LunarDatePort lich = new LunarDatePort() {
            @Override
            public boolean kiemDuoc(int lunarYear) {
                return lunarYear >= 1813 && lunarYear <= 2199;
            }

            @Override
            public boolean tonTai(int lunarYear, int month, int day, boolean leap) {
                if (leap) {
                    return false;
                }
                return !(month == 2 && day == 30);
            }
        };

        @Test
        @DisplayName("Mùng 30 của tháng thiếu bị bắt")
        void mung30ThangThieu() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-01-001", "Cụ").ngayMatAm("30/2/1945").build()));
            new LunarDateRule(lich).apply(ctx);

            List<ImportIssue> issues = of(ctx, IssueCode.IMP_LUNAR_DATE_NOT_EXIST);
            assertThat(issues).hasSize(1);
            assertThat(issues.get(0).severity()).isEqualTo(IssueSeverity.BLOCKING);
        }

        @Test
        @DisplayName("Tháng nhuận của một năm không nhuận tháng ấy bị bắt")
        void thangNhuanKhongCo() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-01-001", "Cụ").ngayMatAm("15/8 nhuận 1945").build()));
            new LunarDateRule(lich).apply(ctx);
            assertThat(codes(ctx)).contains(IssueCode.IMP_LUNAR_DATE_NOT_EXIST);
        }

        @Test
        @DisplayName("Ngày âm có thật thì không báo gì")
        void ngayAmCoThat() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-01-001", "Cụ").ngayMatAm("15/8/1945").build()));
            new LunarDateRule(lich).apply(ctx);
            assertThat(codes(ctx)).isEmpty();
        }

        @Test
        @DisplayName("Không rõ năm thì KHÔNG kết luận được, và luật im lặng")
        void khongRoNam() {
            // Cau hoi "ngay nay co ton tai khong" phu thuoc tung nam. Khong co nam thi khong co
            // dap an, va bia ra mot dap an la cach nhanh nhat de bao sai cho mot du lieu dung.
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-01-001", "Cụ").ngayMatAm("30/2").build()));
            new LunarDateRule(lich).apply(ctx);
            assertThat(codes(ctx)).doesNotContain(IssueCode.IMP_LUNAR_DATE_NOT_EXIST);
        }

        @Test
        @DisplayName("Năm trước 1813 thì bỏ qua, không báo sai cho các cụ tổ đời đầu")
        void namQuaCo() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-01-001", "Thuỷ tổ").ngayMatAm("30/2/1700").build()));
            new LunarDateRule(lich).apply(ctx);
            assertThat(codes(ctx)).isEmpty();
        }

        @Test
        @DisplayName("Ô là số sê-ri của Excel: báo lỗi riêng, không đoán ngược")
        void oLaSoSeRi() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-01-001", "Cụ").ngayMatAm("45889").build()));
            new LunarDateRule(lich).apply(ctx);

            List<ImportIssue> issues = of(ctx, IssueCode.IMP_LUNAR_DATE_IS_SERIAL);
            assertThat(issues).hasSize(1);
            assertThat(issues.get(0).message()).contains("Văn bản");
        }
    }

    @Nested
    @DisplayName("Mã trùng trong tệp")
    class MaTrung {

        @Test
        @DisplayName("Cả HAI dòng đều được báo, để người nhập so được")
        void baoCaHaiDong() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-02-001", "Nguyễn Văn A").build(),
                    row(7, "AT-02-001", "Nguyễn Văn B").build()));
            new DuplicateCodeRule().apply(ctx);

            List<ImportIssue> issues = of(ctx, IssueCode.IMP_DUP_CODE);
            assertThat(issues).hasSize(2);
            assertThat(issues).allSatisfy(i ->
                    assertThat(i.context().get("cacDong")).isEqualTo(List.of(2, 7)));
        }

        @Test
        @DisplayName("Mã khác nhau thì không báo gì")
        void maKhacNhau() {
            ValidationContext ctx = ctx(List.of(
                    row(2, "AT-02-001", "A").build(),
                    row(3, "AT-02-002", "B").build()));
            new DuplicateCodeRule().apply(ctx);
            assertThat(codes(ctx)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Bậc hôn phối trùng nhau")
    class BacHonPhoi {

        @Test
        @DisplayName("Hai bà cùng bậc trên một ông là lỗi chặn")
        void haiBaCungBac() {
            ValidationContext ctx = ctx(List.of(), List.of(
                    new MarriageRow(2, "AT-01-001", "AT-01-002", 1, null, null, null, Map.of()),
                    new MarriageRow(3, "AT-01-001", "AT-01-003", 1, null, null, null, Map.of())));
            new SpouseOrderRule().apply(ctx);
            assertThat(of(ctx, IssueCode.IMP_SPOUSE_ORDER_CONFLICT)).hasSize(2);
        }

        @Test
        @DisplayName("Vợ cả và vợ hai thì không sao")
        void bacKhacNhau() {
            ValidationContext ctx = ctx(List.of(), List.of(
                    new MarriageRow(2, "AT-01-001", "AT-01-002", 1, null, null, null, Map.of()),
                    new MarriageRow(3, "AT-01-001", "AT-01-003", 2, null, null, null, Map.of())));
            new SpouseOrderRule().apply(ctx);
            assertThat(codes(ctx)).isEmpty();
        }
    }
}
