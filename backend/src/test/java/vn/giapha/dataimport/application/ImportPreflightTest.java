package vn.giapha.dataimport.application;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.giapha.dataimport.domain.CommitFixtures.honPhoi;
import static vn.giapha.dataimport.domain.CommitFixtures.row;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.dataimport.domain.CellCodec;
import vn.giapha.dataimport.domain.DuplicateMergePlan;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.MarriageRow;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.port.ExternalRefPort;

/**
 * Lượt soát trước khi ghi, <b>ở vai trò bước kiểm</b>: ghép vào danh sách của bộ luật sao cho màn
 * đối soát nói đúng điều bước ghi sẽ làm — không hơn (kêu trùng), không kém (NT-TC-15).
 */
@DisplayName("ImportPreflight — lượt soát trước khi ghi, chạy ở bước kiểm")
class ImportPreflightTest {

    private static final UUID CHI = UUID.randomUUID();

    /** Phả của chi: chỉ những mã này "đã có". */
    private static ImportPreflight voiPha(String... maDaCo) {
        Set<String> pha = Set.of(maDaCo);
        return new ImportPreflight(new ExternalRefPort() {
            @Override
            public Map<String, UUID> resolve(UUID branchId, Collection<String> externalCodes) {
                Map<String, UUID> kq = new LinkedHashMap<>();
                externalCodes.stream().filter(pha::contains).forEach(m -> kq.put(m, UUID.randomUUID()));
                return kq;
            }

            @Override
            public Set<String> codesOf(UUID branchId) {
                return pha;
            }

            @Override
            public long countByBranch(UUID branchId) {
                return pha.size();
            }

            @Override
            public Map<String, UUID> ownerBranchOf(Collection<String> externalCodes) {
                return Map.of();
            }

            @Override
            public void ghi(UUID branchId, String externalCode, UUID personId, UUID batchId) {
                throw new AssertionError("luot soat khong duoc ghi gi");
            }

            @Override
            public int xoaTheoLo(UUID batchId) {
                throw new AssertionError("luot soat khong duoc xoa gi");
            }
        });
    }

    private static List<ImportIssue> ghep(ImportPreflight p, List<ImportIssue> daCo,
                                          List<PersonRow> rows, List<MarriageRow> honPhoi) {
        DuplicateMergePlan trong = DuplicateMergePlan.trong();
        return p.ghepVao(daCo, CHI, trong, trong.apDung(rows, honPhoi));
    }

    private static List<String> ma(List<ImportIssue> issues) {
        return issues.stream().map(i -> i.code().name()).toList();
    }

    @Test
    @DisplayName("NT-TC-15: dâu khai Đời → lỗi chặn ở ô Đời, câu nói rõ quy ước và cách sửa")
    void dauKhaiDoiThanhLoiChanOBuocKiem() {
        List<PersonRow> rows = List.of(
                row(2, "AT-02-001").doi(2).cha("AT-01-001").build(),
                row(3, "AT-02-101").ten("Trần Thị Dâu").doi(2).build());

        List<ImportIssue> kq = ghep(voiPha("AT-01-001"), List.of(), rows,
                List.of(honPhoi(2, "AT-02-001", "AT-02-101", 1)));

        assertThat(kq).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo(IssueCode.IMP_UNDECIDED_INLAW_DOI);
            assertThat(i.chan()).isTrue();
            assertThat(i.rowNo()).isEqualTo(3);
            assertThat(i.field()).isEqualTo("Đời");
            assertThat(i.message()).contains("dâu/rể KHÔNG khai Đời", "Hội đồng Tộc biểu",
                    "xoá ô Đời ở dòng 3", "suy từ vợ/chồng");
        });
    }

    @Test
    @DisplayName("Chạy lại thì gỡ kết quả soát cũ — lỗi đã hết không được nằm lại chặn lô")
    void goKetQuaSoatCu() {
        ImportIssue cu = ImportIssue.nhanKhau(IssueCode.IMP_UNDECIDED_INLAW_DOI, 3, "Đời", "cũ",
                Map.of());
        ImportIssue canhBao = ImportIssue.nhanKhau(IssueCode.IMP_UNKNOWN_GENDER, 2, "Giới",
                "thiếu giới", Map.of());
        List<PersonRow> rows = List.of(row(2, "AT-01-001").doi(1).build());

        List<ImportIssue> kq = ghep(voiPha(), List.of(cu, canhBao), rows, List.of());

        assertThat(kq).containsExactly(canhBao);
    }

    @Test
    @DisplayName("Bộ luật đã báo IMP_CYCLE thì không nói lại bằng IMP_COMMIT_CYCLE; chưa báo thì nói")
    void vongLapKhongNoiHaiLan() {
        List<PersonRow> rows = List.of(
                row(2, "AT-02-001").cha("AT-02-002").build(),
                row(3, "AT-02-002").cha("AT-02-001").build());
        ImportIssue cuaLuat = ImportIssue.nhanKhau(IssueCode.IMP_CYCLE, 2, "Mã cha", "vòng lặp",
                Map.of());

        assertThat(ma(ghep(voiPha(), List.of(cuaLuat), rows, List.of())))
                .containsExactly("IMP_CYCLE");
        assertThat(ma(ghep(voiPha(), List.of(), rows, List.of())))
                .containsExactly("IMP_COMMIT_CYCLE");
    }

    @Test
    @DisplayName("Mã vợ ở trang Hôn phối không có ở đâu cả → chặn đúng ô Mã vợ; có trong phả → im")
    void maHonPhoiKhongPhanGiaiDuoc() {
        List<PersonRow> rows = List.of(row(2, "AT-02-001").doi(2).cha("AT-01-001").build());
        List<MarriageRow> hp = List.of(honPhoi(2, "AT-02-001", "AT-02-777", 1));

        assertThat(ghep(voiPha("AT-01-001"), List.of(), rows, hp)).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo(IssueCode.IMP_COMMIT_REF_NOT_FOUND);
            assertThat(i.sheet()).isEqualTo(ImportIssue.SHEET_HON_PHOI);
            assertThat(i.field()).isEqualTo("Mã vợ");
            assertThat(i.context()).containsEntry("maKhongTimThay", "AT-02-777");
        });
        assertThat(ghep(voiPha("AT-01-001", "AT-02-777"), List.of(), rows, hp)).isEmpty();
    }

    @Test
    @DisplayName("Kế tự không có ở đâu cả: cảnh báo của bộ luật bị THAY bằng lỗi chặn, giữ gợi ý mã")
    void keTuKhongCoODauCaThanhLoiChan() {
        List<PersonRow> rows = List.of(
                row(2, "AT-03-001").doi(3).cha("AT-02-001").keTu("AT-01-OO1", CellCodec.HeirType.DICH_TON)
                        .build());
        ImportIssue canhBao = ImportIssue.nhanKhau(IssueCode.IMP_HEIR_TARGET_NOT_FOUND, 2,
                "Kế tự cho ai (Mã)", "không thấy", Map.of("maKhongTimThay", "AT-01-OO1",
                        "goiY", List.of("AT-01-001")));

        List<ImportIssue> kq = ghep(voiPha("AT-02-001"), List.of(canhBao), rows, List.of());

        assertThat(kq).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo(IssueCode.IMP_COMMIT_REF_NOT_FOUND);
            assertThat(i.chan()).isTrue();
            assertThat(i.field()).isEqualTo("Kế tự cho ai (Mã)");
            assertThat(i.context()).containsEntry("goiY", List.of("AT-01-001"));
            assertThat(i.message()).contains("AT-01-001");
        });
    }

    @Test
    @DisplayName("Kế tự có trong phả dù không có trong tệp: vẫn chỉ là cảnh báo — bước ghi cho qua")
    void keTuCoTrongPhaThiVanLaCanhBao() {
        List<PersonRow> rows = List.of(
                row(2, "AT-03-001").doi(3).cha("AT-02-001").keTu("AT-01-001", CellCodec.HeirType.DICH_TON)
                        .build());
        ImportIssue canhBao = ImportIssue.nhanKhau(IssueCode.IMP_HEIR_TARGET_NOT_FOUND, 2,
                "Kế tự cho ai (Mã)", "không thấy trong tệp", Map.of());

        assertThat(ghep(voiPha("AT-02-001", "AT-01-001"), List.of(canhBao), rows, List.of()))
                .containsExactly(canhBao);
    }

    @Test
    @DisplayName("Dòng thiếu Mã (đã bị IMP_MISSING_CODE chặn) không làm lượt soát ném giữa chừng")
    void dongThieuMaKhongNem() {
        List<PersonRow> rows = List.of(
                row(2, null).khongRoSongChet().build(),
                row(3, "AT-01-001").doi(1).build());

        assertThat(ghep(voiPha(), List.of(), rows, List.of())).isEmpty();
    }

    @Test
    @DisplayName("Sáu mã của lượt soát đều là lỗi chặn — lô VALIDATED là lô bước ghi sẽ nhận")
    void sauMaDeuChan() {
        assertThat(ImportPreflight.MA_CUA_LUOT_SOAT).hasSize(6).allMatch(IssueCode::chan);
    }
}
