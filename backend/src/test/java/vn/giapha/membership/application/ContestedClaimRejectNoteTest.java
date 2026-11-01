package vn.giapha.membership.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lý do tự động của các đơn tranh chấp bị đóng theo là câu NGƯỜI GỬI đọc (NT-TC-06). Bản trước
 * không dấu: "Nhan khau nay da duoc ghep voi mot don khac da duoc duyet".
 */
@DisplayName("Đơn tranh chấp bị đóng theo — lý do đọc được")
class ContestedClaimRejectNoteTest {

    @Test
    @DisplayName("có dấu, và nói việc cần làm tiếp")
    void coDauVaCoViecCanLam() {
        String s = PersonClaimService.LY_DO_DA_GHEP_DON_KHAC;
        assertThat(s).contains("người khác").contains("Trưởng chi").contains("gọi");
        assertThat(s).doesNotContain("Nhan khau");
    }
}
