package vn.giapha.genealogy.api.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import vn.giapha.genealogy.application.DuplicateMatch;
import vn.giapha.genealogy.application.DuplicatePersonSuspectedException;
import vn.giapha.genealogy.application.DuplicateSignal;
import vn.giapha.genealogy.application.GenealogyConflictException;
import vn.giapha.genealogy.application.GenealogyProblemCodes;
import vn.giapha.genealogy.application.TabooNameConflictException;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.TabooConflict;
import vn.giapha.genealogy.domain.TabooMatchKind;

/**
 * Thân lỗi 409 phải khớp {@code ConflictProblem} của hợp đồng.
 *
 * <h2>Vì sao nghi trùng cần một nhánh riêng</h2>
 * {@link DuplicatePersonSuspectedException} kế thừa {@link GenealogyConflictException} nên tự động
 * ra đúng 409 và đúng mã — nhưng rơi vào nhánh chung thì thân lỗi mang {@code overridable: false} và
 * không có {@code overrideField} lẫn danh sách ứng viên. Với client, đó là một ngõ cụt: người nhập
 * liệu thấy "nghi trùng" mà không biết trùng với ai, cũng không có đường nào để nói "đây là người
 * khác, cứ ghi". Cảnh báo không kèm lối đi tiếp thì chỉ còn là một lệnh cấm.
 *
 * <h2>Và vì sao một nửa số ca ở đây là ca RIÊNG TƯ</h2>
 * Cả hai bộ dò (nghi trùng, kỵ húy) quét <b>toàn dòng họ</b> và không biết người gọi là ai, còn
 * thân lỗi 409 thì đi thẳng ra HTTP mà <b>không</b> qua {@code PrivacyTierService}. Nên ngoài
 * "thân lỗi có đủ dùng không", bộ test này còn hỏi câu thứ hai: <b>có giá trị nào đọc từ phả rời
 * khỏi tiến trình qua đây không</b>. Các ca ấy cố ý khẳng định trên <b>toàn bộ</b> thân lỗi ở dạng
 * chuỗi, không trên từng trường: lỗi đã xảy ra một lần rồi, và nó xảy ra ở chỗ giá trị bị nối vào
 * một câu tiếng Việt trong {@code detail} chứ không ở một trường DTO.
 */
@DisplayName("GenealogyExceptionHandler — thân lỗi 409")
class GenealogyExceptionHandlerTest {

    /** Giá trị chỉ tồn tại ở phía phả — không ô nào của người gọi mang chúng. */
    private static final String TEN_TRONG_PHA = "Nguyễn Văn Tuân";
    private static final String TEN_HUY_TRONG_PHA = "Nguyễn Phúc Đệ";

    private final GenealogyExceptionHandler handler = new GenealogyExceptionHandler();

    private final MockHttpServletRequest request = request("/api/v1/persons");

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRequestURI(uri);
        return req;
    }

    private static DuplicateMatch ungVien(UUID personId, String ref, String ten, Integer doi) {
        return new DuplicateMatch(personId, ref, ten, doi, UUID.randomUUID(), 88,
                List.of(DuplicateSignal.TEN_TRUNG_CO_DAU, DuplicateSignal.GIO_TRUNG_KHIT),
                ten, "trùng họ tên đủ dấu, trùng ngày giỗ");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> conflictsOf(ProblemDetail problem) {
        Object value = problem.getProperties() == null ? null : problem.getProperties().get("conflicts");
        return (List<Map<String, Object>>) value;
    }

    /** Cả thân lỗi ở dạng chuỗi — {@code detail} cộng mọi thuộc tính mở rộng. */
    private static String tatCa(ProblemDetail problem) {
        return problem.getTitle() + "\n" + problem.getDetail() + "\n" + problem.getProperties();
    }

    // =========================================================================================
    // Nghi trùng
    // =========================================================================================

    @Nested
    @DisplayName("Nghi trùng nhân khẩu")
    class NghiTrung {

        @Test
        @DisplayName("overridable = true kèm overrideField đúng tên trường xác nhận")
        void nghiTrungLaCanhBaoGhiDeDuoc() {
            DuplicatePersonSuspectedException ex = new DuplicatePersonSuspectedException(
                    List.of(ungVien(UUID.randomUUID(), null, TEN_TRONG_PHA, 5)));

            ProblemDetail problem = handler.handleDuplicateSuspected(ex, request);

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
            assertThat(problem.getProperties()).containsEntry("code",
                    GenealogyProblemCodes.DUPLICATE_PERSON_SUSPECTED);
            assertThat(problem.getProperties())
                    .as("Rot vao nhanh chung thi cho nay la false va client khong con duong di tiep")
                    .containsEntry("overridable", true)
                    .containsEntry("overrideField", "confirmDuplicateOverride");
        }

        @Test
        @DisplayName("conflicts[] mang khoá + tín hiệu — đủ để dựng hộp thoại rồi đi hỏi GET /persons/{id}")
        void conflictsMangKhoaVaTinHieu() {
            UUID personId = UUID.randomUUID();
            DuplicatePersonSuspectedException ex = new DuplicatePersonSuspectedException(
                    List.of(ungVien(personId, null, TEN_TRONG_PHA, 5)));

            List<Map<String, Object>> conflicts =
                    conflictsOf(handler.handleDuplicateSuspected(ex, request));

            assertThat(conflicts).hasSize(1);
            Map<String, Object> ungVien = conflicts.get(0);
            assertThat(ungVien)
                    .as("khoa la thu duy nhat noi ve ho so ben kia")
                    .containsEntry("personId", personId.toString());
            assertThat(ungVien).containsEntry("score", 88);
            assertThat(ungVien).containsEntry("signals",
                    List.of("TEN_TRUNG_CO_DAU", "GIO_TRUNG_KHIT"));
            assertThat(ungVien.get("hint")).isNotNull();
        }

        /**
         * Ca riêng tư. Bộ dò quét toàn dòng họ và không biết người gọi là ai, nên ứng viên có thể
         * là một người còn sống ở chi khác — thân lỗi không được nói họ là ai.
         */
        @Test
        @DisplayName("RIÊNG TƯ: không một giá trị nào đọc từ phả rời khỏi thân lỗi")
        void khongPhatMotGiaTriNaoDocTuPha() {
            UUID personId = UUID.randomUUID();
            UUID branchId = UUID.randomUUID();
            DuplicateMatch match = new DuplicateMatch(personId, null, TEN_TRONG_PHA, 5, branchId, 88,
                    List.of(DuplicateSignal.TEN_TRUNG_KHONG_DAU), TEN_HUY_TRONG_PHA,
                    "trùng họ tên khi bỏ dấu");

            ProblemDetail problem = handler.handleDuplicateSuspected(
                    new DuplicatePersonSuspectedException(List.of(match)), request);

            assertThat(tatCa(problem))
                    .as("ten hien thi doc tu pha")
                    .doesNotContain(TEN_TRONG_PHA)
                    // matchedName la person_name.full_name cua ho so BEN KIA: khi khop o muc bo dau
                    // no phat ra ban CO DAU ma nguoi goi chua tung biet, va no khop cheo moi lop
                    // ten nen hoan toan co the la ten huy.
                    .as("ten da khop, doc tu pha")
                    .doesNotContain(TEN_HUY_TRONG_PHA)
                    .as("doi thu doc tu pha")
                    .doesNotContain("\"generation\"", "generation=")
                    .as("chi/nganh doc tu pha")
                    .doesNotContain(branchId.toString());
            assertThat(tatCa(problem))
                    .as("...nhung khoa thi phai co, neu khong giao dien khong doi chieu duoc")
                    .contains(personId.toString());
        }

        @Test
        @DisplayName("detail nói VÌ SAO nghi và PHẢI LÀM GÌ TIẾP, nhưng không nói ai")
        void detailVanDungDuoc() {
            ProblemDetail problem = handler.handleDuplicateSuspected(
                    new DuplicatePersonSuspectedException(
                            List.of(ungVien(UUID.randomUUID(), null, TEN_TRONG_PHA, 5))),
                    request);

            assertThat(problem.getDetail())
                    .doesNotContain(TEN_TRONG_PHA)
                    .contains("88")                          // diem
                    .contains("trùng ngày giỗ")              // vi sao nghi
                    .contains("personId")                    // phai lam gi tiep
                    .contains("Xac nhan");                   // van ghi de duoc
        }

        @Test
        @DisplayName("ứng viên là dòng chưa ghi trong cùng lô: personId null KHÔNG được làm nổ handler")
        void ungVienTrongCungLoNhapLieu() {
            DuplicatePersonSuspectedException ex = new DuplicatePersonSuspectedException(
                    List.of(ungVien(null, "dong-12", "Nguyễn Thị Mão", null)));

            List<Map<String, Object>> conflicts =
                    conflictsOf(handler.handleDuplicateSuspected(ex, request));

            assertThat(conflicts).hasSize(1);
            // Map.of nem NullPointerException o day — day la truong hop BINH THUONG, khong phai loi.
            assertThat(conflicts.get(0)).containsEntry("personId", null);
            assertThat(conflicts.get(0)).containsEntry("ref", "dong-12");
        }

        @Test
        @DisplayName("giữ nguyên thứ tự giảm dần theo điểm của tầng application")
        void giuThuTuUngVien() {
            DuplicateMatch cao = ungVien(UUID.randomUUID(), null, "Ứng viên điểm cao", 5);
            DuplicateMatch thap = new DuplicateMatch(UUID.randomUUID(), null, "Ứng viên điểm thấp", 5,
                    null, 61, List.of(DuplicateSignal.TEN_TRUNG_KHONG_DAU), "x",
                    "trùng họ tên khi bỏ dấu");

            List<Map<String, Object>> conflicts = conflictsOf(handler.handleDuplicateSuspected(
                    new DuplicatePersonSuspectedException(List.of(cao, thap)), request));

            assertThat(conflicts).extracting(entry -> entry.get("score"))
                    .containsExactly(88, 61);
            assertThat(conflicts).extracting(entry -> entry.get("personId"))
                    .containsExactly(cao.personId().toString(), thap.personId().toString());
        }
    }

    // =========================================================================================
    // Kỵ húy
    // =========================================================================================

    @Nested
    @DisplayName("Kỵ húy")
    class KyHuy {

        private TabooConflict vaCham(UUID ancestorId, TabooMatchKind kind) {
            return new TabooConflict(ancestorId, TEN_TRONG_PHA, 3, TEN_HUY_TRONG_PHA, NameType.HUY,
                    kind, "Doi thu 3 (tren 2 doi)");
        }

        @Test
        @DisplayName("overridable = true kèm overrideField đúng tên trường xác nhận")
        void kyHuyLaCanhBaoGhiDeDuoc() {
            ProblemDetail problem = handler.handleTabooConflict(
                    new TabooNameConflictException(
                            List.of(vaCham(UUID.randomUUID(), TabooMatchKind.EXACT))),
                    request);

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
            assertThat(problem.getProperties())
                    .containsEntry("code", GenealogyProblemCodes.KY_HUY_CONFLICT)
                    .containsEntry("overridable", true)
                    .containsEntry("overrideField", "confirmTabooOverride");
        }

        @Test
        @DisplayName("conflicts[] mang khoá bậc trên + lớp tên + kiểu khớp")
        void conflictsMangKhoaVaKieuKhop() {
            UUID ancestorId = UUID.randomUUID();

            List<Map<String, Object>> conflicts = conflictsOf(handler.handleTabooConflict(
                    new TabooNameConflictException(
                            List.of(vaCham(ancestorId, TabooMatchKind.UNACCENTED))),
                    request));

            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.get(0))
                    .containsEntry("ancestorPersonId", ancestorId.toString())
                    .containsEntry("matchedNameType", "HUY")
                    .containsEntry("matchKind", "UNACCENTED");
        }

        /**
         * Ca riêng tư. Phép dò kỵ húy chọn bậc trên theo <b>đời thứ</b>, không theo sống-mất và
         * không theo chi — nên "bậc trên" hoàn toàn có thể là một ông bác còn sống ở chi khác.
         */
        @Test
        @DisplayName("RIÊNG TƯ: không tên, không đời thứ, không tên húy đọc từ phả")
        void khongPhatDanhTinhBacTren() {
            UUID ancestorId = UUID.randomUUID();

            ProblemDetail problem = handler.handleTabooConflict(
                    new TabooNameConflictException(
                            List.of(vaCham(ancestorId, TabooMatchKind.UNACCENTED))),
                    request);

            assertThat(tatCa(problem))
                    .as("ten hien thi cua bac tren")
                    .doesNotContain(TEN_TRONG_PHA)
                    // tabooName la person_name.full_name DOC TU PHA: voi UNACCENTED no la ban co
                    // dau, voi GIVEN_NAME no la ten huy day du — ca hai deu khac o nguoi goi vua go.
                    .as("ten huy day du luu trong pha")
                    .doesNotContain(TEN_HUY_TRONG_PHA)
                    .as("doi thu cua bac tren, ke ca khi nam trong relationHint")
                    .doesNotContain("Doi thu 3", "\"ancestorGeneration\"", "ancestorGeneration=");
            assertThat(tatCa(problem)).contains(ancestorId.toString());
        }

        /**
         * {@code TabooNameConflictException.getMessage()} vẫn nối {@code tabooName} vào câu mô tả —
         * hữu ích trong log của tiến trình, nhưng không được đi ra HTTP. Handler vì thế soạn
         * {@code detail} của riêng nó; ca này ghim điều đó để không ai "dọn dẹp" bằng cách quay về
         * dùng {@code ex.getMessage()}.
         */
        @Test
        @DisplayName("detail do handler soạn, KHÔNG lấy từ getMessage() của ngoại lệ")
        void detailKhongLayTuNgoaiLe() {
            TabooNameConflictException ex = new TabooNameConflictException(
                    List.of(vaCham(UUID.randomUUID(), TabooMatchKind.EXACT)));

            ProblemDetail problem = handler.handleTabooConflict(ex, request);

            assertThat(ex.getMessage())
                    .as("bay: cau cua ngoai le co ten huy doc tu pha")
                    .contains(TEN_HUY_TRONG_PHA);
            assertThat(problem.getDetail())
                    .isNotEqualTo(ex.getMessage())
                    .doesNotContain(TEN_HUY_TRONG_PHA)
                    .contains("trung nguyen van")            // vi sao canh bao
                    .contains("ancestorPersonId")            // phai lam gi tiep
                    .contains("Xac nhan de van ghi");        // van ghi de duoc
        }
    }

    // =========================================================================================

    @Test
    @DisplayName("xung đột thường vẫn là overridable = false — nhánh chung không bị đổi nghĩa")
    void xungDotThuongVanKhongGhiDeDuoc() {
        ProblemDetail problem = handler.handleConflict(
                new GenealogyConflictException(GenealogyProblemCodes.OPTIMISTIC_LOCK_CONFLICT,
                        "Ban ghi da bi nguoi khac sua"),
                request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getProperties())
                .containsEntry("overridable", false)
                .doesNotContainKey("overrideField")
                .doesNotContainKey("conflicts");
    }
}
