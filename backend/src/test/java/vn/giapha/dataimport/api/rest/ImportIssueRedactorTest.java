package vn.giapha.dataimport.api.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;

/**
 * Chốt chặn cấu trúc trên {@code import_issue.context}.
 *
 * <h2>Lớp này đã đổi vai, và bộ test đổi theo</h2>
 * Nó <b>không còn soạn lại câu thông báo</b>: gốc rễ đã được vá ở {@code SuspectDuplicateRule} /
 * {@code TabooCollisionRule}, nơi câu thông báo được soạn bằng khoá chứ không bằng trường. Giữ
 * thêm một bản soạn lại ở tầng api nghĩa là có hai bản văn bản cảnh báo, và hai bản thì lệch nhau.
 *
 * <p>Việc còn lại là <b>danh sách khoá được phép</b>, và nó tồn tại vì một lý do nêu được: bên ghi
 * và bên đọc {@code context} bị ngăn cách bởi một cơ sở dữ liệu, tức bởi thời gian. Một dòng do
 * bản nhị phân cũ ghi ra vẫn có thể được đọc lên sau khi nâng cấp. Vì vậy bộ test dưới đây gieo
 * đúng <b>hình dạng dữ liệu cũ</b> — có {@code ten}, {@code doi}, {@code giaiThich},
 * {@code bacTrenTen} — và khẳng định chúng không đi qua được.</p>
 */
@DisplayName("Danh sách khoá được phép của import_issue.context")
class ImportIssueRedactorTest {

    @Nested
    @DisplayName("IMP_SUSPECT_DUPLICATE")
    class NghiTrung {

        private final UUID nguoiTrongPha = UUID.randomUUID();

        @Test
        @DisplayName("Dòng cũ do bản nhị phân trước bản vá ghi ra: ten/doi/giaiThich bị cắt")
        void dongCu_catTruongDuLieu() {
            ImportIssue issue = nghiTrung(Map.of(
                    "personId", nguoiTrongPha.toString(),
                    "ten", "Nguyễn Thị Lựu",
                    "doi", 5,
                    "diem", 78,
                    "giaiThich", "trung nam sinh 1975, cung doi thu 5"));

            ImportIssueRedactor.NoiDung noiDung = ImportIssueRedactor.apply(issue, null);

            Map<String, Object> ungVien = dauTien(noiDung);
            assertThat(ungVien).containsOnlyKeys("personId", "diem");
            assertThat(ungVien.get("personId")).isEqualTo(nguoiTrongPha.toString());
            assertThat(noiDung.context().toString())
                    .doesNotContain("Nguyễn Thị Lựu")
                    .doesNotContain("1975");
        }

        @Test
        @DisplayName("Dòng mới do bộ kiểm đã vá ghi ra: đi qua nguyên vẹn, kể cả nhãn tín hiệu")
        void dongMoi_diQuaNguyenVen() {
            ImportIssue issue = nghiTrung(Map.of(
                    "personId", nguoiTrongPha.toString(),
                    "diem", 78,
                    "nguon", "TREE",
                    "tinHieu", "trùng ngày giỗ, cùng chi"));

            ImportIssueRedactor.NoiDung noiDung = ImportIssueRedactor.apply(issue, null);

            Map<String, Object> ungVien = dauTien(noiDung);
            assertThat(ungVien).containsOnlyKeys("personId", "diem", "nguon", "tinHieu");
            assertThat(ungVien.get("tinHieu")).isEqualTo("trùng ngày giỗ, cùng chi");
        }

        @Test
        @DisplayName("Vế trong CHÍNH TỆP đi qua nguyên vẹn — đó là thứ người nhập vừa gõ")
        void veTrongTep_giuDu() {
            ImportIssue issue = nghiTrung(Map.of(
                    "ref", "AT-04-003",
                    "ten", "Nguyễn Văn F",
                    "doi", 4,
                    "diem", 91,
                    "giaiThich", "trùng ngày giỗ",
                    "nguon", "FILE"));

            ImportIssueRedactor.NoiDung noiDung = ImportIssueRedactor.apply(issue, null);

            Map<String, Object> ungVien = dauTien(noiDung);
            assertThat(ungVien).containsKeys("ref", "ten", "doi", "diem", "giaiThich");
            assertThat(ungVien.get("nguon")).isEqualTo("FILE");
        }

        @Test
        @DisplayName("Trộn hai loại: cắt đúng vế trong phả, không đụng vế trong tệp")
        void tronHaiLoai() {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("nghiNgo", List.of(
                    Map.of("ref", "AT-04-003", "ten", "Nguyễn Văn F", "diem", 91),
                    Map.of("personId", nguoiTrongPha.toString(), "ten", "Nguyễn Thị Lựu",
                            "diem", 78)));
            ImportIssue issue = ImportIssue.nhanKhau(IssueCode.IMP_SUSPECT_DUPLICATE, 12, "Họ tên",
                    "cau thong bao do bo kiem soan", context);

            ImportIssueRedactor.NoiDung noiDung = ImportIssueRedactor.apply(issue, null);

            assertThat(noiDung.context().toString())
                    .contains("Nguyễn Văn F")
                    .doesNotContain("Nguyễn Thị Lựu");
        }

        /**
         * Câu thông báo <b>không</b> bị đụng tới. Bộ kiểm đã soạn nó đúng; soạn lại ở đây là dựng
         * bản thứ hai, và bản thứ hai sẽ lệch.
         */
        @Test
        @DisplayName("Câu thông báo đi qua nguyên văn — chốt này chỉ canh context")
        void thongBaoDiQuaNguyenVan() {
            ImportIssue issue = nghiTrung(Map.of("personId", nguoiTrongPha.toString(), "diem", 78));
            assertThat(ImportIssueRedactor.apply(issue, null).message())
                    .isEqualTo(issue.message());
        }

        private ImportIssue nghiTrung(Map<String, Object> ungVien) {
            return ImportIssue.nhanKhau(IssueCode.IMP_SUSPECT_DUPLICATE, 7, "Họ tên",
                    "cau thong bao do bo kiem soan", Map.of("nghiNgo", List.of(ungVien)));
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> dauTien(ImportIssueRedactor.NoiDung noiDung) {
            List<Map<String, Object>> ds =
                    (List<Map<String, Object>>) noiDung.context().get("nghiNgo");
            assertThat(ds).hasSize(1);
            return ds.get(0);
        }
    }

    @Test
    @DisplayName("Kỵ húy: cắt bacTrenTen và bacTrenDoi của dòng cũ, giữ tenHuy và khoá")
    void kyHuy_catTenVaDoiBacTren() {
        UUID bacTren = UUID.randomUUID();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("tenHuy", "Lựu");
        context.put("bacTrenId", bacTren.toString());
        context.put("bacTrenTen", "Nguyễn Phúc Thuỷ Tổ");
        context.put("bacTrenDoi", 1);
        ImportIssue issue = ImportIssue.nhanKhau(IssueCode.IMP_TABOO_COLLISION, 3, "Tên huý",
                "cau thong bao do bo kiem soan", context);

        ImportIssueRedactor.NoiDung noiDung = ImportIssueRedactor.apply(issue, null);

        assertThat(noiDung.context()).containsOnlyKeys("tenHuy", "bacTrenId");
        assertThat(noiDung.context().toString()).doesNotContain("Nguyễn Phúc Thuỷ Tổ");
    }

    @Test
    @DisplayName("Mã khác đi qua nguyên vẹn: cắt bừa là làm hỏng đúng những gợi ý đáng giá nhất")
    void maKhacDiQuaNguyenVen() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("maKhongTimThay", "AT-04-O03");
        context.put("goiY", List.of("AT-04-003"));
        ImportIssue issue = ImportIssue.nhanKhau(IssueCode.IMP_PARENT_NOT_FOUND, 9, "Mã cha",
                "Dòng AT-05-012 khai mã cha là AT-04-O03 nhưng không có mã đó.", context);

        ImportIssueRedactor.NoiDung noiDung = ImportIssueRedactor.apply(issue, null);

        assertThat(noiDung.message()).isEqualTo(issue.message());
        assertThat(noiDung.context()).isEqualTo(issue.context());
    }
}
