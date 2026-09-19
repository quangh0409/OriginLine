package vn.giapha.dataimport.infrastructure.excel.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.dataimport.domain.CellCodec;

/**
 * Ghim chiều còn lại của hợp đồng: <b>mọi giá trị mà mẫu mời người dùng chọn, máy chủ phải hiểu
 * đúng</b>.
 *
 * <p>Không có bộ test này thì một mục thêm vào danh sách thả xuống — "Con đẻ" chẳng hạn — sẽ được
 * người dùng chọn đúng như mẫu bảo, rồi bị {@link CellCodec} đọc thành mặc định hoặc thành
 * {@code null}. Lỗi khi ấy nằm ở phía ta mà thông báo lại chỉ về phía họ.</p>
 */
class TemplateVocabularyTest {

    @Test
    @DisplayName("Mọi giá trị trong danh sách thả xuống đều được CellCodec giải đúng")
    void moiGiaTriDeuGiaiDuoc() {
        List<String> sai = new ArrayList<>();
        for (TuVung tv : TemplateVocabulary.tatCa()) {
            for (TuVung.Muc muc : tv.muc()) {
                Object thuc = tv.boGiai().apply(muc.nhan());
                if (!java.util.Objects.equals(thuc, muc.mongDoi())) {
                    sai.add(tv.tenVungDatTen() + " / \"" + muc.nhan() + "\": mong " + muc.mongDoi()
                            + " nhung duoc " + thuc);
                }
            }
        }
        assertThat(sai).isEmpty();
    }

    @Test
    @DisplayName("Nhãn mà bộ đọc roster sinh ra cũng nằm trong danh sách thả xuống")
    void nhanRosterNamTrongDanhSach() {
        // Cac nhan nay do BranchRosterJdbcAdapter viet ra khi dien san. Neu chung lech khoi danh
        // sach thi Excel se bao "gia tri ngoai danh sach" ngay tren chinh du lieu he thong phat ra
        // — mot cach chac chan de nguoi dien mat long tin vao ca cai mau.
        List<String> gioi = nhan("DM_GIOI");
        assertThat(gioi).contains("Nam", "Nữ", "Không rõ");

        assertThat(nhan("DM_QUAN_HE")).contains("Con ruột", "Con nuôi");
        assertThat(nhan("DM_CON_SONG")).contains("Có", "Không");
        assertThat(nhan("DM_LOAI_KE_TU")).contains("Đích tôn", "Thừa tự", "Kế tự");
        assertThat(nhan("DM_LY_DO")).contains("Ly hôn", "Qua đời", "Huỷ hôn", "Khác");
    }

    @Test
    @DisplayName("Tên vùng đặt tên hợp lệ với Excel và không trùng nhau")
    void tenVungHopLe() {
        List<String> ten = TemplateVocabulary.tatCa().stream()
                .map(TuVung::tenVungDatTen).toList();

        assertThat(ten).doesNotHaveDuplicates();
        // Excel khong nhan ten vung co dau cach hay dau tieng Viet; bat dau bang chu hoac gach duoi.
        assertThat(ten).allMatch(s -> s.matches("^[A-Z_][A-Z0-9_]*$"));
    }

    private static List<String> nhan(String tenVung) {
        return TemplateVocabulary.tatCa().stream()
                .filter(tv -> tv.tenVungDatTen().equals(tenVung))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Khong co vung dat ten " + tenVung))
                .nhan();
    }
}
