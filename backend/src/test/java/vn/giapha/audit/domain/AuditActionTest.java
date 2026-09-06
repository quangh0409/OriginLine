package vn.giapha.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Danh sách hành động phải khớp <b>từng ký tự</b> với {@code ck_audit_log_action} của
 * {@code V5__membership_audit.sql}.
 *
 * <p>Đưa nó thành enum thay vì chuỗi tự do để một hành động sai chính tả lộ ra lúc biên dịch, chứ
 * không lộ ra dưới dạng một transaction chết giữa chừng: Postgres huỷ mọi lệnh còn lại sau khi một
 * {@code CHECK} bị vi phạm, nên câu INSERT audit hỏng sẽ kéo theo cả nghiệp vụ mà nó đang ghi vết.</p>
 */
class AuditActionTest {

    /** Nguyên văn {@code ck_audit_log_action} của V5. Đổi ở đây thì phải đổi cả migration. */
    private static final String[] CK_AUDIT_LOG_ACTION = {
            "CREATE", "UPDATE", "SOFT_DELETE", "RESTORE", "ANONYMIZE",
            "LINK_RELATIONSHIP", "UNLINK_RELATIONSHIP", "MOVE_BRANCH",
            "APPROVE", "REJECT", "GRANT_ROLE", "REVOKE_ROLE",
            "LOGIN", "READ_SENSITIVE", "EXPORT"};

    @Test
    @DisplayName("Enum khớp đúng ràng buộc CHECK của bảng audit_log")
    void khopRangBuocCheck() {
        assertThat(Arrays.stream(AuditAction.values()).map(Enum::name))
                .containsExactlyInAnyOrder(CK_AUDIT_LOG_ACTION);
    }

    @Test
    @DisplayName("KHÔNG có hành động xoá cứng — xoá gia phả là xoá mềm, xoá theo luật là ẩn danh hoá")
    void khongCoXoaCung() {
        // Hai luat cua BA v2 gap nhau o day:
        //   1. Chi xoa mem cho node pha he — xoa cung lam dut lien ket cay.
        //   2. Quyen duoc lang quen (Nghi dinh 13/2023) duoc phuc vu bang AN DANH HOA, giu node.
        // Neu enum nay co mot hang so DELETE/HARD_DELETE thi som muon se co nguoi ghi vet cho mot
        // thao tac ma he thong khong duoc phep lam.
        assertThat(Arrays.stream(AuditAction.values()).map(Enum::name))
                .doesNotContain("DELETE", "HARD_DELETE", "PURGE", "ERASE");
        assertThat(AuditAction.valueOf("SOFT_DELETE")).isNotNull();
        assertThat(AuditAction.valueOf("ANONYMIZE")).isNotNull();
        assertThat(AuditAction.valueOf("RESTORE")).isNotNull();
    }

    @Test
    @DisplayName("Phân giải chuỗi: chuẩn hoá hoa/thường và khoảng trắng")
    void phanGiaiChuoi() {
        assertThat(AuditAction.of(" anonymize ")).isEqualTo(AuditAction.ANONYMIZE);
        assertThat(AuditAction.of("GRANT_ROLE")).isEqualTo(AuditAction.GRANT_ROLE);
    }

    @Test
    @DisplayName("Hành động lạ bị từ chối kèm tên hành động rõ ràng")
    void hanhDongLa() {
        assertThatThrownBy(() -> AuditAction.of("HARD_DELETE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ck_audit_log_action")
                .hasMessageContaining("HARD_DELETE");
        assertThatThrownBy(() -> AuditAction.of("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditAction.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("READ_SENSITIVE tồn tại: ghi lại VIỆC ĐỌC dữ liệu Tầng 3")
    void ghiVetViecDoc() {
        // Nghi dinh 13/2023 doi truy vet ca truy cap, khong chi truy vet sua doi. Ghi lai viec doc
        // — tuyet doi khong ghi gia tri da doc.
        assertThat(AuditAction.of("READ_SENSITIVE")).isEqualTo(AuditAction.READ_SENSITIVE);
    }
}
