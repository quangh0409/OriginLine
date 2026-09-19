package vn.giapha.dataimport.api.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.dataimport.api.rest.dto.DuplicateEvidenceDto;
import vn.giapha.dataimport.api.rest.dto.ImportDuplicatePairDto;
import vn.giapha.dataimport.domain.DuplicateDecision;
import vn.giapha.dataimport.domain.DuplicatePair;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.LunarDeathDate;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.shared.vo.Gender;

/**
 * Bảng đối chiếu nghi trùng — <b>bất biến riêng tư của mảng {@code evidence}</b>, và đường đi
 * trọn vẹn từ cảnh báo của bộ kiểm sang cặp có quyết định.
 *
 * <p>{@code existingValue = null} phải có đúng một nghĩa: "nguồn bên kia không ghi mục này". Giao
 * diện hiển thị nó thành dòng chữ "không ghi", và người đối chiếu sẽ đọc đúng như thế — rồi hợp
 * nhất, tin rằng bên kia đang trống. Vì vậy dấu hiệu nào không được phép trưng ra thì phải
 * <b>bỏ hẳn khỏi mảng</b>, không gửi sang một ô rỗng.</p>
 *
 * <p>Test đi qua {@link DuplicatePair#tuCanhBao} thật chứ không dựng cặp bằng tay: phép đọc
 * {@code context} ấy là chỗ duy nhất quyết định nhãn tín hiệu nào được chép vào bảng quyết định, và
 * nó phải bị canh cùng chỗ với phép cắt của tầng DTO.</p>
 */
@DisplayName("Đối chiếu nghi trùng: evidence không bao giờ là ô rỗng vì bị giấu")
class ImportDuplicateMappingTest {

    private static final UUID LO = UUID.randomUUID();

    @Test
    @DisplayName("Cặp với người đã có trong phả: chỉ khoá, evidence RỖNG, không một trường nào")
    void capVoiPha_khongTruongNao() {
        UUID nguoiTrongPha = UUID.randomUUID();
        PersonRow dongTrongTep = dong(12, "AT-03-001", "Nguyen Van Tuan", 1975);

        List<ImportDuplicatePairDto> caps = map(
                List.of(canhBao(12, Map.of("personId", nguoiTrongPha.toString(),
                        // `ten` va `doi` la du lieu cu con sot lai tu mot ban nhi phan truoc khi
                        // SuspectDuplicateRule duoc va: bang quyet dinh KHONG duoc chep chung.
                        "ten", "Nguyễn Văn Tuân", "doi", 3, "diem", 86,
                        "tinHieu", "trùng ngày giỗ, cùng chi"))),
                Map.of(12, dongTrongTep),
                Map.of("AT-03-001", dongTrongTep));

        assertThat(caps).hasSize(1);
        ImportDuplicatePairDto cap = caps.get(0);
        assertThat(cap.evidence()).isEmpty();
        assertThat(cap.existing().source()).isEqualTo("TREE");
        assertThat(cap.existing().personId()).isEqualTo(nguoiTrongPha);
        assertThat(cap.existing().displayName()).isNull();
        assertThat(cap.existing().generation()).isNull();
        assertThat(cap.existing().birthYear()).isNull();
        // `hint` — cau giai thich TU DO — phai VANG MAT o ve trong pha: no co the nhac toi gia
        // tri truong cua nguoi ben kia, ma ben kia co the la mot nguoi con song o chi khac.
        assertThat(cap.hint()).isNull();
        // Thu di duoc la NHAN tin hieu: tra loi "vi sao nghi" ma khong tiet lo "nguoi ay la ai".
        assertThat(cap.signals()).isEqualTo("trùng ngày giỗ, cùng chi");
        // Ben INCOMING la du lieu nguoi nhap vua go — phai co du, neu khong thi man doi chieu rong.
        assertThat(cap.incoming().displayName()).isEqualTo("Nguyen Van Tuan");
        assertThat(cap.preselectMerge()).isTrue();
        assertThat(cap.status()).isEqualTo(ImportDuplicatePairDto.PENDING);
        assertThat(cap.decidedBy()).isNull();
        assertThat(cap.decidedAt()).isNull();
    }

    @Test
    @DisplayName("Cặp giữa hai dòng trong cùng tệp: evidence đầy đủ, null chỉ nghĩa là ô bỏ trống")
    void capTrongCungTep_evidenceDayDu() {
        PersonRow dongA = dong(12, "AT-03-001", "Nguyễn Văn Tuân", 1975)
                .withResolution(null, null);
        PersonRow dongB = new PersonRow(40, "AT-04-009", null, null, "Nguyễn Văn Tuân", "Huý Tuân",
                null, null, Gender.MALE, 3, "AT-02-001", null, null, Boolean.FALSE, null,
                new LunarDeathDate(null, 8, 15, false), "Bắc Ninh", null, null, null, null, null);

        List<ImportDuplicatePairDto> caps = map(
                List.of(canhBao(12, Map.of("ref", "AT-04-009", "ten", "Nguyễn Văn Tuân",
                        "diem", 74, "giaiThich", "trung ngay gio 15/8 am lich"))),
                Map.of(12, dongA),
                Map.of("AT-03-001", dongA, "AT-04-009", dongB));

        assertThat(caps).hasSize(1);
        ImportDuplicatePairDto cap = caps.get(0);
        assertThat(cap.existing().source()).isEqualTo("FILE");
        assertThat(cap.existing().externalCode()).isEqualTo("AT-04-009");
        assertThat(cap.hint()).isEqualTo("trung ngay gio 15/8 am lich");
        assertThat(cap.signals())
                .as("ve trong tep dung `hint`; `signals` de danh cho ve trong pha")
                .isNull();
        assertThat(cap.preselectMerge()).as("74 diem, duoi nguong tick san 85").isFalse();

        Map<String, DuplicateEvidenceDto> theoTruong = new LinkedHashMap<>();
        cap.evidence().forEach(e -> theoTruong.put(e.field(), e));

        assertThat(theoTruong.get("FULL_NAME").match()).isEqualTo(DuplicateEvidenceDto.SAME);
        assertThat(theoTruong.get("GENERATION").match()).isEqualTo(DuplicateEvidenceDto.SAME);
        // Ben kia co ten huy, ben nay khong -> "thieu o ben incoming", KHONG phai "bi giau".
        assertThat(theoTruong.get("TABOO_NAME").match())
                .isEqualTo(DuplicateEvidenceDto.MISSING_IN_FILE);
        assertThat(theoTruong.get("TABOO_NAME").existingValue()).isEqualTo("Huý Tuân");
        assertThat(theoTruong.get("TABOO_NAME").incomingValue()).isNull();
        // Ben nay co nam sinh, ben kia khong.
        assertThat(theoTruong.get("BIRTH_YEAR").match())
                .isEqualTo(DuplicateEvidenceDto.MISSING_IN_TREE);
        assertThat(theoTruong.get("BIRTH_YEAR").existingValue()).isNull();
        assertThat(theoTruong.get("BIRTH_YEAR").incomingValue()).isEqualTo("1975");
        // Ca hai deu trong -> bo han khoi mang, khong tra mot dong hai o rong.
        assertThat(theoTruong).doesNotContainKey("ORIGIN_PLACE_EMPTY");
        assertThat(theoTruong.keySet())
                .as("khong truong nao ma CA HAI ben deu trong duoc xuat hien")
                .doesNotContain("FATHER_EMPTY");
        // Diem tung tin hieu chua duoc bo do cong bo -> vang mat, va giao dien khong tu cong bu.
        assertThat(theoTruong.get("FULL_NAME").points()).isNull();
    }

    @Test
    @DisplayName("Cặp đã quyết mang theo AI quyết và LÚC NÀO — kể cả khi quyết là 'hoãn'")
    void capDaQuyet_mangTheoChuVaMoc() {
        UUID nguoiQuyet = UUID.randomUUID();
        Instant luc = Instant.parse("2026-03-04T09:15:00Z");
        PersonRow dongTrongTep = dong(12, "AT-03-001", "Nguyen Van Tuan", 1975);
        UUID capId = UUID.randomUUID();

        DuplicatePair daQuyet = new DuplicatePair(capId, LO,
                DuplicatePair.khoa("AT-03-001", DuplicatePair.Kind.FILE, null, "AT-04-009"),
                12, "AT-03-001", DuplicatePair.Kind.FILE, null, "AT-04-009", 74, "trùng ngày giỗ",
                DuplicateDecision.DEFERRED, nguoiQuyet, luc, "chờ đối chiếu sổ chi Giáp");

        List<ImportDuplicatePairDto> caps = ImportDtoMapper.duplicatePairs(List.of(daQuyet),
                Map.of(12, dongTrongTep), Map.of("AT-03-001", dongTrongTep));

        assertThat(caps).hasSize(1);
        assertThat(caps.get(0).id()).isEqualTo(capId.toString());
        assertThat(caps.get(0).status()).isEqualTo("DEFERRED");
        assertThat(caps.get(0).decidedBy()).isEqualTo(nguoiQuyet);
        assertThat(caps.get(0).decidedAt()).isEqualTo(luc);
        assertThat(caps.get(0).note()).isEqualTo("chờ đối chiếu sổ chi Giáp");
    }

    // -------------------------------------------------------------------------------------

    /** Đi trọn đường: cảnh báo của bộ kiểm → cặp (domain) → DTO (api). */
    private static List<ImportDuplicatePairDto> map(List<ImportIssue> issues,
                                                    Map<Integer, PersonRow> byNo,
                                                    Map<String, PersonRow> byCode) {
        return ImportDtoMapper.duplicatePairs(DuplicatePair.tuCanhBao(LO, issues, byNo), byNo,
                byCode);
    }

    private static ImportIssue canhBao(int rowNo, Map<String, Object> ungVien) {
        return ImportIssue.nhanKhau(IssueCode.IMP_SUSPECT_DUPLICATE, rowNo, "Họ tên",
                "cau goc", Map.of("nghiNgo", List.of(ungVien)));
    }

    private static PersonRow dong(int rowNo, String ma, String hoTen, Integer namSinh) {
        return new PersonRow(rowNo, ma, null, null, hoTen, null, null, null, Gender.MALE, 3,
                "AT-02-001", null, null, Boolean.TRUE, namSinh, null, null, null, null, null,
                null, null);
    }
}
