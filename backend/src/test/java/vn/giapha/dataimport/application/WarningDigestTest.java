package vn.giapha.dataimport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;

/**
 * Vân tay tập cảnh báo — thứ quyết định một xác nhận "đã xem" còn hiệu lực hay không.
 *
 * <p>Bốn tính chất, và mỗi tính chất hỏng theo một kiểu riêng:</p>
 * <ul>
 *   <li><b>Ổn định</b> — hỏng thì cứ kiểm lại là mất hiệu lực xác nhận, người nhập tick mãi.</li>
 *   <li><b>Nhạy với cảnh báo mới</b> — hỏng thì người duyệt xác nhận thứ chưa từng thấy.</li>
 *   <li><b>Không phụ thuộc thứ tự</b> — hỏng thì bài kiểm lúc xanh lúc đỏ.</li>
 *   <li><b>Bỏ qua lỗi chặn</b> — lỗi chặn đi lối khác; còn một lỗi chặn thì không ai duyệt gì.</li>
 * </ul>
 */
@DisplayName("Vân tay tập cảnh báo")
class WarningDigestTest {

    private static ImportIssue canhBao(int rowNo, String message) {
        return ImportIssue.nhanKhau(IssueCode.IMP_MISSING_GIO, rowNo, "Ngày mất âm", message,
                Map.of());
    }

    @Test
    @DisplayName("Cùng một tập cảnh báo → cùng một vân tay, dù thứ tự đưa vào khác nhau")
    void onDinhVaKhongPhuThuocThuTu() {
        ImportIssue a = canhBao(5, "Dòng 5 chưa có ngày giỗ");
        ImportIssue b = canhBao(9, "Dòng 9 chưa có ngày giỗ");

        assertThat(WarningDigest.cua(List.of(a, b)))
                .isEqualTo(WarningDigest.cua(List.of(b, a)))
                .isEqualTo(WarningDigest.cua(List.of(a, b)));
    }

    @Test
    @DisplayName("Kiểm lại sinh thêm một cảnh báo → vân tay đổi, tức xác nhận cũ hết hiệu lực")
    void themCanhBaoThiVanTayDoi() {
        ImportIssue a = canhBao(5, "Dòng 5 chưa có ngày giỗ");
        ImportIssue b = canhBao(9, "Dòng 9 chưa có ngày giỗ");

        assertThat(WarningDigest.cua(List.of(a, b))).isNotEqualTo(WarningDigest.cua(List.of(a)));
    }

    @Test
    @DisplayName("Cùng mã, cùng dòng, cùng cột nhưng câu chữ đổi → vẫn là một tập cảnh báo khác")
    void doiCauChuThiVanTayDoi() {
        assertThat(WarningDigest.cua(List.of(canhBao(5, "nghi trùng với 1 hồ sơ"))))
                .as("hai cau noi hai dieu khac nhau thi nguoi doc phai duoc hoi lai")
                .isNotEqualTo(WarningDigest.cua(List.of(canhBao(5, "nghi trùng với 3 hồ sơ"))));
    }

    @Test
    @DisplayName("Không cảnh báo nào → null: không có gì để xem thì không có gì để xác nhận")
    void khongCanhBaoThiNull() {
        assertThat(WarningDigest.cua(List.of())).isNull();
        assertThat(WarningDigest.cua(null)).isNull();

        ImportIssue loiChan = ImportIssue.nhanKhau(IssueCode.IMP_DUP_CODE, 3, "Mã",
                "Hai dòng cùng mang một mã", Map.of());
        assertThat(loiChan.chan()).isTrue();
        assertThat(WarningDigest.cua(List.of(loiChan)))
                .as("loi chan di loi khac; con mot loi chan thi khong ai duyet duoc gi")
                .isNull();
    }
}
