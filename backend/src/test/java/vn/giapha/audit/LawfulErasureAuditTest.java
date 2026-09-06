package vn.giapha.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.audit.domain.AuditEntry;
import vn.giapha.audit.domain.SensitiveFieldRedactor;
import vn.giapha.audit.domain.port.AuditLogRepository;
import vn.giapha.audit.support.InMemoryAuditLog;
import vn.giapha.audit.support.SimpleObjectProvider;

/**
 * <b>Quyền được lãng quên được phục vụ bằng ẩn danh hoá, KHÔNG bằng xoá</b> — BA v2 §10, Nghị định
 * 13/2023.
 *
 * <h2>Vì sao luật này phải có test riêng</h2>
 * Hai ràng buộc kéo ngược chiều nhau và rất dễ bị "hoà giải" sai:
 * <ul>
 *   <li>Pháp luật đòi xoá dữ liệu cá nhân khi người dùng yêu cầu;</li>
 *   <li>Phả hệ đòi <b>giữ node huyết thống</b> — xoá một người là cắt cây làm đôi, mọi hậu duệ mất
 *       đường về tổ.</li>
 * </ul>
 * Cách hoà giải đúng và duy nhất: bóc sạch dữ liệu Tầng 3, giữ nguyên node và các cạnh quan hệ.
 * Một lập trình viên vội vàng rất dễ thêm một lối {@code DELETE} "cho đúng luật" — các test ở đây
 * là thứ sẽ đỏ khi điều đó xảy ra.
 *
 * <p>Việc ẩn danh hoá bản thân nó nằm ở {@code genealogy.application.AnonymizePersonService}; ở đây
 * ghim <b>phần của context audit</b>: bảng nhật ký phải chứng minh được rằng thao tác đã xảy ra,
 * node vẫn còn, và không giá trị Tầng 3 nào bị chép lại vào chính bảng nhật ký.</p>
 */
class LawfulErasureAuditTest {

    private final InMemoryAuditLog auditLog = new InMemoryAuditLog();
    private final AuditTrailService auditTrail =
            new AuditTrailService(auditLog, SimpleObjectProvider.empty());

    @Test
    @DisplayName("Không tồn tại hành động xoá cứng nào trong từ vựng của audit")
    void khongCoHanhDongXoaCung() {
        List<String> hanhDong = Arrays.stream(AuditAction.values()).map(Enum::name).toList();

        assertThat(hanhDong).contains("ANONYMIZE", "SOFT_DELETE", "RESTORE");
        assertThat(hanhDong).noneMatch(ten -> ten.contains("HARD")
                || ten.equals("DELETE")
                || ten.contains("PURGE")
                || ten.contains("ERASE"));
    }

    @Test
    @DisplayName("Ẩn danh hoá được ghi bằng ANONYMIZE, KHÔNG bằng SOFT_DELETE")
    void anDanhHoaKhacXoaMem() {
        // Hai thao tac khac han ve phap ly: xoa mem la mot quyet dinh nghiep vu cua dong ho va co
        // the khoi phuc; an danh hoa la thuc thi mot quyen cua ca nhan va KHONG khoi phuc duoc.
        // Ghi chung mot hanh dong thi ba nam sau khong ai tra lai duoc dieu gi da xay ra.
        auditTrail.record("Person", "p-1", AuditAction.ANONYMIZE,
                truocKhiAnDanh(), sauKhiAnDanh(),
                List.of("phone", "email", "fullAddress", "birthDate", "avatarKey"),
                "Yêu cầu xoá dữ liệu theo Nghị định 13/2023");

        AuditEntry entry = auditLog.last().entry();
        assertThat(entry.action()).isEqualTo(AuditAction.ANONYMIZE);
        assertThat(entry.action()).isNotEqualTo(AuditAction.SOFT_DELETE);
    }

    @Test
    @DisplayName("Vết ẩn danh hoá giữ định danh node — bằng chứng cây phả hệ không bị cắt")
    void vetGiuDinhDanhNode() {
        auditTrail.record("Person", "p-1", AuditAction.ANONYMIZE,
                truocKhiAnDanh(), sauKhiAnDanh(), List.of("phone"), "quyền được lãng quên");

        AuditEntry entry = auditLog.last().entry();
        // Node van tra cuu duoc: entity_id con nguyen, va anh chup SAU van con cac truong huyet
        // thong (doi thu, chi, quan he) de cay khong dut.
        assertThat(entry.entityId()).isEqualTo("p-1");
        assertThat(entry.after())
                .containsEntry("generation", 7)
                .containsEntry("primaryBranchPath", "goc.chi_giap")
                .containsEntry("isDeleted", false);
    }

    @Test
    @DisplayName("Chỉ ghi TÊN trường bị bóc, KHÔNG ghi giá trị vừa bị yêu cầu xoá")
    void chiGhiTenTruongBiBoc() {
        // Neu audit_log chep lai chinh so dien thoai vua bi yeu cau xoa thi thao tac "xoa" tro
        // thanh mot lan sao luu — va audit_log la bang chi ghi them, khong go ra duoc.
        auditTrail.record("Person", "p-1", AuditAction.ANONYMIZE,
                truocKhiAnDanh(), sauKhiAnDanh(),
                List.of("phone", "email", "fullAddress"), "quyền được lãng quên");

        AuditEntry entry = auditLog.last().entry();
        assertThat(entry.changedFields()).containsExactly("phone", "email", "fullAddress");
        assertThat(entry.before()).containsEntry("phone", SensitiveFieldRedactor.REDACTED);
        assertThat(entry.before()).containsEntry("email", SensitiveFieldRedactor.REDACTED);
        assertThat(String.valueOf(entry.before())).doesNotContain("0912345678");
        assertThat(String.valueOf(entry.before())).doesNotContain("nguyenvana@example.test");
        assertThat(String.valueOf(entry.before())).doesNotContain("Số 5, ngõ 12");
    }

    @Test
    @DisplayName("audit_log chỉ ghi thêm: cổng không có phương thức sửa hay xoá lịch sử")
    void nhatKyChiGhiThem() {
        // Bang that co trigger tg_audit_log_immutable chan UPDATE. Mot cong cho phep sua lich su la
        // mau thuan voi chinh ly do lich su ton tai — va la lo hong de "don dep" dau vet cua mot
        // thao tac sai.
        List<String> tenPhuongThuc = Arrays.stream(AuditLogRepository.class.getDeclaredMethods())
                .map(Method::getName)
                .map(ten -> ten.toLowerCase(Locale.ROOT))
                .toList();

        assertThat(tenPhuongThuc).contains("append");
        assertThat(tenPhuongThuc).noneMatch(ten -> ten.startsWith("update")
                || ten.startsWith("delete")
                || ten.startsWith("remove")
                || ten.startsWith("purge")
                || ten.startsWith("save"));
    }

    /** Hồ sơ trước khi ẩn danh hoá — có đủ dữ liệu Tầng 3. */
    private static Map<String, Object> truocKhiAnDanh() {
        Map<String, Object> truoc = new LinkedHashMap<>();
        truoc.put("generation", 7);
        truoc.put("primaryBranchPath", "goc.chi_giap");
        truoc.put("isDeleted", false);
        truoc.put("phone", "0912345678");
        truoc.put("email", "nguyenvana@example.test");
        truoc.put("fullAddress", "Số 5, ngõ 12, phường X, Hà Nội");
        truoc.put("birthDate", "1978-03-14");
        truoc.put("avatarKey", "portraits/abc.jpg");
        return truoc;
    }

    /** Hồ sơ sau khi ẩn danh hoá — Tầng 3 trống, node và dữ kiện huyết thống còn nguyên. */
    private static Map<String, Object> sauKhiAnDanh() {
        Map<String, Object> sau = new LinkedHashMap<>();
        sau.put("generation", 7);
        sau.put("primaryBranchPath", "goc.chi_giap");
        sau.put("isDeleted", false);
        sau.put("phone", null);
        sau.put("email", null);
        sau.put("fullAddress", null);
        sau.put("birthDate", null);
        sau.put("avatarKey", null);
        return sau;
    }
}
