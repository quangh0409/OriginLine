package vn.giapha.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Một dòng nhật ký chờ ghi: <b>ai đổi gì, trên thực thể nào, trước ra sao và sau ra sao</b>.
 *
 * <p>Giới hạn độ dài được cắt <b>ngay tại đây</b>, không để tới lúc chạm CSDL: Postgres huỷ mọi
 * lệnh còn lại sau khi một ràng buộc bị vi phạm, nên một câu INSERT audit hỏng sẽ kéo theo cả giao
 * dịch nghiệp vụ mà nó đang ghi vết.</p>
 */
class AuditEntryTest {

    @Nested
    @DisplayName("Trường bắt buộc")
    class TruongBatBuoc {

        @Test
        @DisplayName("entityType, entityId và action đều không được rỗng")
        void khongDuocRong() {
            assertThatThrownBy(() -> new AuditEntry("  ", "id", AuditAction.CREATE,
                    null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("entityType");
            assertThatThrownBy(() -> new AuditEntry("Person", null, AuditAction.CREATE,
                    null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("entityId");
            assertThatThrownBy(() -> new AuditEntry("Person", "id", null,
                    null, null, null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Chuỗi quá dài bị cắt chứ không làm vỡ transaction")
        void catChuoiQuaDai() {
            String daiQua = "x".repeat(200);

            AuditEntry entry = new AuditEntry(daiQua, daiQua, AuditAction.UPDATE,
                    null, null, null, null);

            assertThat(entry.entityType()).hasSize(AuditEntry.MAX_ENTITY_TYPE);
            assertThat(entry.entityId()).hasSize(AuditEntry.MAX_ENTITY_ID);
        }

        @Test
        @DisplayName("Khoảng trắng thừa được cắt gọn")
        void catKhoangTrang() {
            AuditEntry entry = new AuditEntry("  Person  ", "  abc  ", AuditAction.CREATE,
                    null, null, null, null);

            assertThat(entry.entityType()).isEqualTo("Person");
            assertThat(entry.entityId()).isEqualTo("abc");
        }
    }

    @Nested
    @DisplayName("Ảnh chụp trước / sau")
    class AnhChup {

        @Test
        @DisplayName("before null nghĩa là tạo mới, after null nghĩa là xoá")
        void nullCoNghia() {
            AuditEntry taoMoi = new AuditEntry("Person", "1", AuditAction.CREATE,
                    null, Map.of("generation", 7), List.of("generation"), null);
            AuditEntry xoa = new AuditEntry("BranchAssignment", "1", AuditAction.REVOKE_ROLE,
                    Map.of("role", "BRANCH_HEAD"), null, List.of("role"), null);

            assertThat(taoMoi.before()).isNull();
            assertThat(taoMoi.after()).isNotNull();
            assertThat(xoa.before()).isNotNull();
            assertThat(xoa.after()).isNull();
        }

        @Test
        @DisplayName("Ảnh chụp được sao chép — sửa map gốc sau đó không đổi được nhật ký")
        void anhChupLaBanSao() {
            Map<String, Object> goc = new HashMap<>();
            goc.put("status", "ACTIVE");
            AuditEntry entry = new AuditEntry("AppUser", "1", AuditAction.UPDATE,
                    goc, null, null, null);

            goc.put("status", "BỊ SỬA LÉN");

            assertThat(entry.before()).containsEntry("status", "ACTIVE");
            assertThatThrownBy(() -> entry.before().put("x", "y"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("changedFields rỗng hoặc null đều thành danh sách rỗng, không phải null")
        void changedFieldsKhongBaoGioNull() {
            assertThat(new AuditEntry("Person", "1", AuditAction.CREATE, null, null, null, null)
                    .changedFields()).isEmpty();
            assertThat(new AuditEntry("Person", "1", AuditAction.CREATE, null, null, List.of(), null)
                    .changedFields()).isEmpty();
        }

        @Test
        @DisplayName("Phần tử null trong changedFields bị loại")
        void loaiPhanTuNull() {
            List<String> conNull = new ArrayList<>(Arrays.asList("hoTen", null, "namSinh"));

            AuditEntry entry = new AuditEntry("Person", "1", AuditAction.UPDATE,
                    null, null, conNull, null);

            assertThat(entry.changedFields()).containsExactly("hoTen", "namSinh");
        }
    }

    @Test
    @DisplayName("Dạng rút gọn cho hành động không có ảnh chụp: duyệt, cấp vai trò, đăng nhập")
    void dangRutGon() {
        AuditEntry entry = AuditEntry.of("ChangeRequest", "cr-1", AuditAction.APPROVE,
                "Đã đối chiếu gia phả bản giấy");

        assertThat(entry.before()).isNull();
        assertThat(entry.after()).isNull();
        assertThat(entry.changedFields()).isEmpty();
        assertThat(entry.note()).isEqualTo("Đã đối chiếu gia phả bản giấy");
    }
}
