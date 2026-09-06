package vn.giapha.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.audit.domain.AuditActor;
import vn.giapha.audit.domain.AuditEntry;
import vn.giapha.audit.domain.RequestFingerprint;
import vn.giapha.audit.domain.SensitiveFieldRedactor;
import vn.giapha.audit.domain.port.AuditActorPort;
import vn.giapha.audit.domain.port.AuditLogRepository;
import vn.giapha.audit.support.InMemoryAuditLog;
import vn.giapha.audit.support.SimpleObjectProvider;

/**
 * Mặt tiền ghi nhật ký: <b>ai đổi gì, lúc nào, trước/sau</b>.
 *
 * <p>Ba việc lớp này chịu trách nhiệm và phải có test canh: phân giải người thực hiện, gắn dấu vết
 * HTTP, và che dữ liệu Tầng 3 trước khi ghi.</p>
 */
class AuditTrailServiceTest {

    private static final UUID APP_USER = UUID.randomUUID();
    private static final UUID PERSON = UUID.randomUUID();

    private final InMemoryAuditLog auditLog = new InMemoryAuditLog();

    private AuditTrailService dichVuCoActor() {
        AuditActorPort port = sub -> "sub-nguyenvana".equals(sub)
                ? Optional.of(new AuditActor(APP_USER, PERSON))
                : Optional.empty();
        return new AuditTrailService(auditLog, SimpleObjectProvider.of(port));
    }

    private AuditTrailService dichVuKhongActor() {
        return new AuditTrailService(auditLog, SimpleObjectProvider.empty());
    }

    private static void dangNhap(String keycloakSub) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(keycloakSub).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    @BeforeEach
    void don() {
        SecurityContextHolder.clearContext();
        AuditRequestContext.clear();
    }

    @AfterEach
    void donSau() {
        SecurityContextHolder.clearContext();
        AuditRequestContext.clear();
    }

    @Nested
    @DisplayName("Ai đổi gì, lúc nào, trước/sau")
    class AiDoiGi {

        @Test
        @DisplayName("Ghi đủ thực thể, hành động, ảnh chụp trước/sau và danh sách trường đã đổi")
        void ghiDuBonManh() {
            dangNhap("sub-nguyenvana");

            dichVuCoActor().record("Person", "p-1", AuditAction.UPDATE,
                    Map.of("generation", 7), Map.of("generation", 8),
                    List.of("generation"), "Sửa theo bản chép tay");

            InMemoryAuditLog.Written written = auditLog.last();
            assertThat(written.entry().entityType()).isEqualTo("Person");
            assertThat(written.entry().entityId()).isEqualTo("p-1");
            assertThat(written.entry().action()).isEqualTo(AuditAction.UPDATE);
            assertThat(written.entry().before()).containsEntry("generation", 7);
            assertThat(written.entry().after()).containsEntry("generation", 8);
            assertThat(written.entry().changedFields()).containsExactly("generation");
            assertThat(written.entry().note()).isEqualTo("Sửa theo bản chép tay");
            assertThat(written.at()).isNotNull();
        }

        @Test
        @DisplayName("Người thực hiện được phân giải qua keycloak_sub -> app_user -> person")
        void phanGiaiNguoiThucHien() {
            dangNhap("sub-nguyenvana");

            dichVuCoActor().record("Person", "p-1", AuditAction.CREATE, null);

            assertThat(auditLog.last().actor().appUserId()).isEqualTo(APP_USER);
            assertThat(auditLog.last().actor().personId()).isEqualTo(PERSON);
            assertThat(auditLog.last().actor().isSystem()).isFalse();
        }

        @Test
        @DisplayName("Job nền không có token: vẫn ghi, người thực hiện là hệ thống")
        void jobNenGhiDuocVoiActorHeThong() {
            SecurityContextHolder.clearContext();

            dichVuCoActor().record("ReminderPlan", "r-1", AuditAction.CREATE, "job nhắc giỗ");

            assertThat(auditLog.size()).isEqualTo(1);
            assertThat(auditLog.last().actor().isSystem()).isTrue();
        }

        @Test
        @DisplayName("Token có mà chưa có app_user: vẫn ghi, hai cột actor để trống")
        void tokenChuaCoAppUser() {
            // Mat mot chut thong tin con hon mat ca dong nhat ky.
            dangNhap("sub-nguoi-la");

            dichVuCoActor().record("Person", "p-1", AuditAction.CREATE, null);

            assertThat(auditLog.size()).isEqualTo(1);
            assertThat(auditLog.last().actor().isSystem()).isTrue();
        }

        @Test
        @DisplayName("Chưa nạp adapter AuditActorPort thì vẫn ghi được")
        void thieuAdapterVanGhi() {
            dangNhap("sub-nguyenvana");

            dichVuKhongActor().record("Person", "p-1", AuditAction.CREATE, null);

            assertThat(auditLog.size()).isEqualTo(1);
            assertThat(auditLog.last().actor().isSystem()).isTrue();
        }
    }

    @Nested
    @DisplayName("Dấu vết HTTP — ba cột mà W2 để trống")
    class DauVetHttp {

        @Test
        @DisplayName("IP, User-Agent và request_id lấy từ ngữ cảnh request")
        void layDauVetTuNguCanh() {
            dangNhap("sub-nguyenvana");
            AuditRequestContext.set(new RequestFingerprint("203.0.113.7", "Mozilla/5.0", "req-42"));

            dichVuCoActor().record("Person", "p-1", AuditAction.UPDATE, null);

            RequestFingerprint dau = auditLog.last().fingerprint();
            assertThat(dau.ipAddress()).isEqualTo("203.0.113.7");
            assertThat(dau.userAgent()).isEqualTo("Mozilla/5.0");
            assertThat(dau.requestId()).isEqualTo("req-42");
        }

        @Test
        @DisplayName("Ngoài ngữ cảnh HTTP thì cả ba là null — job nền không có IP")
        void ngoaiHttpThiTrong() {
            dichVuCoActor().record("ReminderPlan", "r-1", AuditAction.CREATE, null);

            assertThat(auditLog.last().fingerprint().isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("Tầng 3 không bao giờ lọt vào audit_log")
    class Tang3KhongLot {

        @Test
        @DisplayName("Giá trị nhạy cảm bị che ở CẢ before lẫn after")
        void cheCaHaiPhia() {
            // Ben goi van phai tu loc; lop nay chi la luoi cuoi. Nhung vi cong audit nhan Map tu do
            // nen bat ky ai dung map bang tay cung co the vo tinh chep so dien thoai vao day.
            dangNhap("sub-nguyenvana");

            dichVuCoActor().record("Person", "p-1", AuditAction.UPDATE,
                    Map.of("phone", "0912345678", "generation", 7),
                    Map.of("phone", "0987654321", "generation", 8),
                    List.of("phone", "generation"), null);

            AuditEntry entry = auditLog.last().entry();
            assertThat(entry.before()).containsEntry("phone", SensitiveFieldRedactor.REDACTED);
            assertThat(entry.after()).containsEntry("phone", SensitiveFieldRedactor.REDACTED);
            // Ten truong VAN giu: kiem toan vien biet truong nao doi ma khong doc duoc noi dung.
            assertThat(entry.changedFields()).containsExactly("phone", "generation");
            assertThat(entry.before()).containsEntry("generation", 7);
        }

        @Test
        @DisplayName("Ảnh chụp có giá trị null vẫn ghi được, không làm vỡ giao dịch nghiệp vụ")
        void anhChupCoGiaTriNull() {
            // Anh chup cua mot yeu cau dinh chinh MOI luon co reviewerId = null. Neu dong audit
            // vo vi mot o trong thi chinh thao tac gui yeu cau se that bai — vet audit khong duoc
            // phep pha nghiep vu ma no ghi vet.
            dangNhap("sub-nguyenvana");
            Map<String, Object> coONull = new LinkedHashMap<>();
            coONull.put("status", "PENDING");
            coONull.put("reviewerId", null);

            dichVuCoActor().record("ChangeRequest", "cr-1", AuditAction.CREATE,
                    null, coONull, List.of("status"), null);

            assertThat(auditLog.last().entry().after())
                    .containsEntry("status", "PENDING")
                    .containsEntry("reviewerId", null);
        }
    }

    @Nested
    @DisplayName("Ghi audit hỏng thì mutation phải hỏng theo")
    class HongThiHongTheo {

        @Test
        @DisplayName("Ngoại lệ từ kho lưu trữ được để lan lên, không nuốt")
        void khongNuotNgoaiLe() {
            // Mot thay doi pha he ghi thanh cong ma khong de lai vet la thu khong ai phat hien ra
            // cho toi dung luc can tra lai.
            AuditLogRepository khoHong = new AuditLogRepository() {
                @Override
                public void append(AuditEntry entry, AuditActor actor,
                                   RequestFingerprint fingerprint) {
                    throw new IllegalStateException("audit_log khong ghi duoc");
                }

                @Override
                public List<AuditLogRow> byEntity(String entityType, String entityId,
                                                  int limit, int offset) {
                    return List.of();
                }

                @Override
                public List<AuditLogRow> byActor(UUID appUserId, int limit, int offset) {
                    return List.of();
                }
            };
            AuditTrailService dichVu =
                    new AuditTrailService(khoHong, SimpleObjectProvider.empty());

            assertThatThrownBy(() ->
                    dichVu.record("Person", "p-1", AuditAction.UPDATE, null))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Thực thể rỗng bị chặn ở Java, trước khi chạm CSDL")
        void chanTruocKhiChamCsdl() {
            // Mot cau INSERT bi CHECK tu choi se giet ca transaction dang chay (Postgres huy moi
            // lenh sau loi cho toi ROLLBACK), nen loi lap trinh phai lo ra ngay o day.
            assertThatThrownBy(() ->
                    dichVuKhongActor().record("  ", "p-1", AuditAction.UPDATE, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(auditLog.size()).isZero();
        }
    }

    @Test
    @DisplayName("Truy vấn lại theo thực thể và theo người thực hiện, mới nhất trước")
    void truyVanLai() {
        dangNhap("sub-nguyenvana");
        AuditTrailService dichVu = dichVuCoActor();
        dichVu.record("Person", "p-1", AuditAction.CREATE, "tạo");
        dichVu.record("Person", "p-1", AuditAction.UPDATE, "sửa");
        dichVu.record("Person", "p-2", AuditAction.CREATE, "tạo người khác");

        AuditQueryService truyVan = new AuditQueryService(auditLog);

        assertThat(truyVan.byEntity("Person", "p-1", 0, 20))
                .extracting(row -> row.action())
                .containsExactly("UPDATE", "CREATE");
        assertThat(truyVan.byActor(APP_USER, 0, 20)).hasSize(3);
        assertThat(truyVan.byActor(UUID.randomUUID(), 0, 20)).isEmpty();
    }
}
