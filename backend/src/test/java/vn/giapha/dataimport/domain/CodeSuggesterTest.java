package vn.giapha.dataimport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Gợi ý mã gần giống.
 *
 * <p>Đây là chênh lệch giữa "không tìm thấy mã AT-04-O03" (một câu đố trên tệp 400 dòng) và "ý
 * anh là AT-04-003 phải không" (một cú sửa ba giây).</p>
 */
class CodeSuggesterTest {

    private static final List<String> DA_BIET =
            List.of("AT-04-001", "AT-04-002", "AT-04-003", "AT-05-001", "GI-01-001");

    @Test
    @DisplayName("Chữ O thay vì số 0 — ca kinh điển mà mắt người không phân biệt nổi")
    void chuOThayViSoKhong() {
        assertThat(CodeSuggester.goiY("AT-04-O03", DA_BIET)).contains("AT-04-003");
    }

    @Test
    @DisplayName("Thiếu một ký tự, thừa một ký tự")
    void thieuHoacThuaKyTu() {
        assertThat(CodeSuggester.goiY("AT-04-03", DA_BIET)).contains("AT-04-003");
        assertThat(CodeSuggester.goiY("AT-04-0033", DA_BIET)).contains("AT-04-003");
    }

    @Test
    @DisplayName("Mã hoàn toàn xa lạ thì không gợi ý bừa")
    void khongGoiYBua() {
        // Mot goi y sai con te hon khong goi y: no khien nguoi nhap sua DUNG thanh SAI.
        assertThat(CodeSuggester.goiY("ZZ-99-999", DA_BIET)).isEmpty();
    }

    @Test
    @DisplayName("Không gợi ý chính nó")
    void khongGoiYChinhNo() {
        assertThat(CodeSuggester.goiY("AT-04-003", DA_BIET)).doesNotContain("AT-04-003");
    }

    @Test
    @DisplayName("Tối đa ba gợi ý, gần nhất trước")
    void toiDaBaGoiY() {
        assertThat(CodeSuggester.goiY("AT-04-004", DA_BIET))
                .hasSizeLessThanOrEqualTo(ImportLimits.MAX_CODE_SUGGESTIONS);
    }

    @Test
    @DisplayName("Tất định: cùng đầu vào cho cùng thứ tự gợi ý")
    void tatDinh() {
        assertThat(CodeSuggester.goiY("AT-04-004", DA_BIET))
                .isEqualTo(CodeSuggester.goiY("AT-04-004", DA_BIET));
    }

    @Test
    @DisplayName("Đầu vào rỗng không làm sập gì cả")
    void dauVaoRong() {
        assertThat(CodeSuggester.goiY(null, DA_BIET)).isEmpty();
        assertThat(CodeSuggester.goiY("AT-01-001", List.of())).isEmpty();
    }
}
