package vn.giapha.clan.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.clan.domain.ClanReadiness.Facts;
import vn.giapha.clan.domain.ClanReadiness.Step;
import vn.giapha.clan.domain.ClanReadiness.StepKey;

@DisplayName("Bảng sẵn sàng của dòng họ")
class ClanReadinessTest {

    private static Step buoc(ClanReadiness r, StepKey key) {
        return r.steps().stream().filter(s -> s.key() == key).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("bản cài trắng: năm bước theo đúng thứ tự, không bước nào xong")
    void banCaiTrang() {
        ClanReadiness r = ClanReadiness.of(new Facts(false, false, false, 0, 0, 0));
        List<Step> steps = r.steps();
        assertThat(steps).extracting(s -> s.key().wire())
                .containsExactly("profile", "council", "import", "invite", "done");
        assertThat(steps).noneMatch(Step::done);
        assertThat(buoc(r, StepKey.PROFILE).hint()).contains("Lưu").contains("khởi tạo");
        assertThat(buoc(r, StepKey.DONE).hint()).contains("Còn 4 bước");
    }

    @Test
    @DisplayName("hồ sơ tự điền ở V23 nhưng chưa có bộ luật CLAN: bước hồ sơ CHƯA xong, và câu nhắc nói bấm Lưu")
    void hoSoTuDienChuaCoBoLuat() {
        ClanReadiness r = ClanReadiness.of(new Facts(true, true, false, 1, 1500, 2));
        Step hoSo = buoc(r, StepKey.PROFILE);
        assertThat(hoSo.done()).isFalse();
        assertThat(hoSo.hint()).contains("bấm Lưu một lần").contains("bộ luật danh xưng");
        assertThat(r.allDone()).isFalse();
    }

    @Test
    @DisplayName("đủ cả bốn bước thì bước done xong, câu nhắc nói đã sẵn sàng")
    void duBonBuoc() {
        ClanReadiness r = ClanReadiness.of(new Facts(true, true, true, 2, 1500, 1));
        assertThat(r.steps()).allMatch(Step::done);
        assertThat(buoc(r, StepKey.COUNCIL).hint()).contains("2 tài khoản Hội đồng Tộc biểu");
        assertThat(buoc(r, StepKey.IMPORT).hint()).contains("1500 nhân khẩu");
        assertThat(buoc(r, StepKey.INVITE).hint()).contains("1 mã mời");
        assertThat(buoc(r, StepKey.DONE).hint()).isEqualTo("Dòng họ đã sẵn sàng sử dụng.");
    }

    @Test
    @DisplayName("bước sau không bị khoá bởi bước trước: có Hội đồng trước khi có hồ sơ vẫn tính là xong")
    void khongKhoaTheoThuTu() {
        ClanReadiness r = ClanReadiness.of(new Facts(false, false, false, 1, 0, 0));
        assertThat(buoc(r, StepKey.COUNCIL).done()).isTrue();
        assertThat(buoc(r, StepKey.DONE).hint()).contains("Còn 3 bước");
    }

    @Test
    @DisplayName("mọi câu nhắc là tiếng Việt CÓ DẤU — giao diện hiện nguyên văn")
    void cauNhacCoDau() {
        for (Facts f : List.of(new Facts(false, false, false, 0, 0, 0),
                new Facts(true, true, true, 1, 1, 1), new Facts(true, false, false, 0, 0, 0))) {
            assertThat(ClanReadiness.of(f).steps())
                    .allSatisfy(s -> assertThat(s.hint()).matches(".*[ăâđêôơưạảấầẩậắằẳặẹẻẽếềểễệịọỏốồổỗộớờởợụủứừửữựỳỵỷỹáàãéèíìóòõúùýĐ].*"));
        }
    }
}
