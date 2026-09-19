package vn.giapha.dataimport.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.giapha.dataimport.domain.CommitFixtures.honPhoi;
import static vn.giapha.dataimport.domain.CommitFixtures.row;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ba ca Hội đồng Tộc biểu <b>chưa chốt</b>: bước ghi phải dừng có kiểm soát, không được đoán.
 *
 * <p>Điều quan trọng không kém là <b>không kêu bừa</b>: nếu mỗi lô đều bị chặn thì người nhập sẽ
 * tìm cách đi vòng, và ta mất luôn tác dụng của phép soát.</p>
 */
class CommitPreflightTest {

    @Test
    @DisplayName("Lô sạch: không một vấn đề nào")
    void loSachThiImLang() {
        List<PersonRow> rows = List.of(
                row(1, "AT-01-001").doi(1).build(),
                row(2, "AT-02-001").doi(2).cha("AT-01-001").build());

        assertThat(CommitPreflight.soat(rows, List.of())).isEmpty();
    }

    @Test
    @DisplayName("Không rõ sống hay mất: ô Còn sống trống và không có ngày giỗ → dừng")
    void khongRoSongChet() {
        List<PersonRow> rows = List.of(row(7, "AT-04-012").khongRoSongChet().build());

        List<ImportIssue> issues = CommitPreflight.soat(rows, List.of());

        assertThat(issues).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo(IssueCode.IMP_UNDECIDED_LIFE_STATUS);
            assertThat(i.chan()).isTrue();
            assertThat(i.rowNo()).isEqualTo(7);
            assertThat(i.message()).contains("Còn sống", "Ngày mất âm");
        });
    }

    @Test
    @DisplayName("Con nuôi của người trong họ → dừng, vì mẫu chỉ chở được một cặp cha/mẹ")
    void conNuoiTrongHo() {
        List<PersonRow> rows = List.of(
                row(1, "AT-03-001").doi(3).build(),
                row(2, "AT-04-005").doi(4).cha("AT-03-001").conNuoi().build());

        List<ImportIssue> issues = CommitPreflight.soat(rows, List.of());

        assertThat(issues).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo(IssueCode.IMP_UNDECIDED_ADOPTION);
            assertThat(i.rowNo()).isEqualTo(2);
            assertThat(i.message()).contains("con nuôi", "cha ruột");
        });
    }

    @Test
    @DisplayName("Dâu khai Đời từ đời 2 trở lên → dừng: đời của chồng hay đời họ gốc?")
    void dauKhaiDoi() {
        List<PersonRow> rows = List.of(
                row(1, "AT-02-001").doi(2).cha("AT-01-001").build(),
                row(2, "AT-02-009").ten("Trần Thị Dâu").doi(2).build());
        List<MarriageRow> honPhoi = List.of(honPhoi(1, "AT-02-001", "AT-02-009", 1));

        List<ImportIssue> issues = CommitPreflight.soat(rows, honPhoi);

        assertThat(issues).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo(IssueCode.IMP_UNDECIDED_INLAW_DOI);
            assertThat(i.rowNo()).isEqualTo(2);
            assertThat(i.message()).contains("dâu/rể", "để trống ô Đời");
        });
    }

    @Test
    @DisplayName("Dâu BỎ TRỐNG ô Đời thì không chặn — không có khẳng định nào để hiểu nhầm")
    void dauBoTrongDoiThiKhongChan() {
        List<PersonRow> rows = List.of(
                row(1, "AT-02-001").doi(2).cha("AT-01-001").build(),
                row(2, "AT-02-009").ten("Trần Thị Dâu").build());
        List<MarriageRow> honPhoi = List.of(honPhoi(1, "AT-02-001", "AT-02-009", 1));

        assertThat(CommitPreflight.soat(rows, honPhoi)).isEmpty();
    }

    @Test
    @DisplayName("Vợ thuỷ tổ ghi Đời 1 thì không chặn: số 1 mang cùng nghĩa dưới cả hai quy ước")
    void voThuyToDoiMotThiKhongChan() {
        List<PersonRow> rows = List.of(
                row(1, "AT-01-001").doi(1).build(),
                row(2, "AT-01-002").ten("Nguyễn Thị Lựu").doi(1).build());
        List<MarriageRow> honPhoi = List.of(honPhoi(1, "AT-01-001", "AT-01-002", 1));

        assertThat(CommitPreflight.soat(rows, honPhoi)).isEmpty();
    }

    @Test
    @DisplayName("Con gái của họ lấy chồng KHÔNG bị coi là dâu: bà có cha trong phả")
    void conGaiTrongHoKhongPhaiDau() {
        List<PersonRow> rows = List.of(
                row(1, "AT-03-001").doi(3).build(),
                row(2, "AT-04-003").ten("Nguyễn Thị Gái").doi(4).cha("AT-03-001").build(),
                row(3, "AT-04-009").ten("Trần Văn Rể").build());
        List<MarriageRow> honPhoi = List.of(honPhoi(1, "AT-04-009", "AT-04-003", 1));

        assertThat(CommitPreflight.soat(rows, honPhoi)).isEmpty();
    }

    @Test
    @DisplayName("Tất định: hai lần soát cho danh sách giống hệt nhau")
    void tatDinh() {
        List<PersonRow> rows = List.of(
                row(3, "AT-04-012").khongRoSongChet().build(),
                row(1, "AT-03-001").doi(3).build(),
                row(2, "AT-04-005").doi(4).cha("AT-03-001").conNuoi().build());

        List<String> lan1 = CommitPreflight.soat(rows, List.of()).stream()
                .map(i -> i.rowNo() + "|" + i.code()).toList();
        List<String> lan2 = CommitPreflight.soat(rows, List.of()).stream()
                .map(i -> i.rowNo() + "|" + i.code()).toList();

        assertThat(lan2).isEqualTo(lan1);
        assertThat(lan1).containsExactly("2|IMP_UNDECIDED_ADOPTION", "3|IMP_UNDECIDED_LIFE_STATUS");
    }
}
