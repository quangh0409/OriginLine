package vn.giapha.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Vòng đời một yêu cầu đính chính: gửi → duyệt / từ chối / rút lại.
 *
 * <p>Aggregate này cố ý <b>không</b> tự kiểm quyền — nó không biết {@code ltree}. Việc so path là
 * của {@code BranchScopeGuard} và được kiểm ở {@code BranchScopeGuardTest} /
 * {@code ChangeRequestServiceTest}. Ở đây chỉ kiểm luật chuyển trạng thái.</p>
 */
class ChangeRequestTest {

    private static final UUID NGUOI_GUI = UUID.randomUUID();
    private static final UUID NGUOI_DUYET = UUID.randomUUID();
    private static final UUID NHAN_KHAU = UUID.randomUUID();
    private static final UUID CHI_DICH = UUID.randomUUID();
    private static final Instant LUC = Instant.parse("2026-09-05T03:00:00Z");

    private static ChangeRequest moi() {
        return ChangeRequest.submit(UUID.randomUUID(), ChangeRequestType.UPDATE_PERSON,
                NHAN_KHAU, CHI_DICH,
                Map.of("deathLunar", "15/07 Giáp Thìn", "phone", "0900000000"),
                "Ngày giỗ cụ ghi sai một tháng", NGUOI_GUI);
    }

    @Nested
    @DisplayName("Gửi yêu cầu")
    class Gui {

        @Test
        @DisplayName("Yêu cầu mới luôn ở trạng thái PENDING và chưa có người duyệt")
        void yeuCauMoi() {
            ChangeRequest request = moi();

            assertThat(request.status()).isEqualTo(ChangeRequestStatus.PENDING);
            assertThat(request.status().isOpen()).isTrue();
            assertThat(request.reviewerId()).isNull();
            assertThat(request.reviewedAt()).isNull();
            assertThat(request.requestedBy()).isEqualTo(NGUOI_GUI);
        }

        @Test
        @DisplayName("Chỉ CREATE_PERSON được phép không trỏ tới nhân khẩu nào")
        void loaiYeuCauDoiNhanKhau() {
            // Khop ck_change_request_person cua V5.
            assertThat(ChangeRequestType.CREATE_PERSON.requiresExistingPerson()).isFalse();
            ChangeRequest themNguoiMoi = ChangeRequest.submit(UUID.randomUUID(),
                    ChangeRequestType.CREATE_PERSON, null, CHI_DICH, Map.of(), null, NGUOI_GUI);
            assertThat(themNguoiMoi.personId()).isNull();

            assertThatThrownBy(() -> ChangeRequest.submit(UUID.randomUUID(),
                    ChangeRequestType.UPDATE_PERSON, null, CHI_DICH, Map.of(), null, NGUOI_GUI))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nhan khau co san");
        }

        @Test
        @DisplayName("payload trả ra là bản sao — sửa bên ngoài không chạm được vào aggregate")
        void payloadBatBien() {
            Map<String, Object> goc = new LinkedHashMap<>();
            goc.put("hoTen", "Nguyễn Văn A");
            ChangeRequest request = ChangeRequest.submit(UUID.randomUUID(),
                    ChangeRequestType.UPDATE_PERSON, NHAN_KHAU, CHI_DICH, goc, null, NGUOI_GUI);

            goc.put("hoTen", "Bị sửa lén");

            assertThat(request.payload()).containsEntry("hoTen", "Nguyễn Văn A");
            assertThatThrownBy(() -> request.payload().put("x", "y"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Duyệt và từ chối")
    class Duyet {

        @Test
        @DisplayName("Duyệt ghi lại người duyệt và thời điểm — ck_change_request_reviewed")
        void duyet() {
            ChangeRequest request = moi();

            request.approve(NGUOI_DUYET, "Đã đối chiếu gia phả bản giấy", LUC);

            assertThat(request.status()).isEqualTo(ChangeRequestStatus.APPROVED);
            assertThat(request.reviewerId()).isEqualTo(NGUOI_DUYET);
            assertThat(request.reviewedAt()).isEqualTo(LUC);
            assertThat(request.reviewNote()).isEqualTo("Đã đối chiếu gia phả bản giấy");
        }

        @Test
        @DisplayName("Từ chối BẮT BUỘC kèm lý do — người gửi có quyền biết vì sao")
        void tuChoiPhaiCoLyDo() {
            ChangeRequest request = moi();

            assertThatThrownBy(() -> request.reject(NGUOI_DUYET, "   ", LUC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ly do");
            assertThatThrownBy(() -> request.reject(NGUOI_DUYET, null, LUC))
                    .isInstanceOf(IllegalArgumentException.class);
            // Bi tu choi khong duoc lam hong trang thai dang mo.
            assertThat(request.status()).isEqualTo(ChangeRequestStatus.PENDING);

            request.reject(NGUOI_DUYET, "Trái với bản chép tay năm Bảo Đại thứ 10", LUC);
            assertThat(request.status()).isEqualTo(ChangeRequestStatus.REJECTED);
        }

        @Test
        @DisplayName("KHÔNG ai được tự duyệt yêu cầu của chính mình")
        void khongTuDuyet() {
            // Mot Truong chi van ghi thang duoc trong nhanh minh ma khong can qua day. Neu nguoi ay
            // lai duyet duoc chinh de nghi minh gui thi luong duyet chi con la mot buoc bam them,
            // va audit_log ghi lai mot cuoc "phe duyet" khong co ai kiem tra ai.
            ChangeRequest request = moi();

            assertThatThrownBy(() -> request.approve(NGUOI_GUI, "tự duyệt", LUC))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("tu duyet");
            assertThatThrownBy(() -> request.reject(NGUOI_GUI, "tự từ chối", LUC))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(request.status()).isEqualTo(ChangeRequestStatus.PENDING);
        }

        @Test
        @DisplayName("Người duyệt không được null")
        void nguoiDuyetKhongNull() {
            assertThatThrownBy(() -> moi().approve(null, "ok", LUC))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Thiếu thời điểm thì lấy hiện tại, không để trống")
        void thieuThoiDiem() {
            ChangeRequest request = moi();

            request.approve(NGUOI_DUYET, null, null);

            assertThat(request.reviewedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Trạng thái cuối là cuối")
    class TrangThaiCuoi {

        @Test
        @DisplayName("Đã duyệt thì không duyệt lại, không từ chối, không rút")
        void daDuyetThiKhoa() {
            ChangeRequest request = moi();
            request.approve(NGUOI_DUYET, "ok", LUC);

            assertThatThrownBy(() -> request.approve(NGUOI_DUYET, "ok lần hai", LUC))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("trang thai cuoi");
            assertThatThrownBy(() -> request.reject(NGUOI_DUYET, "đổi ý", LUC))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> request.cancel(NGUOI_GUI, LUC))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Đã từ chối thì không lặng lẽ trở thành đã duyệt")
        void daTuChoiThiKhoa() {
            // Cho mo lai nghia la mot yeu cau bi tu choi co the thanh da duyet ma audit_log chi
            // thay mot dong. Muon doi y thi gui yeu cau moi.
            ChangeRequest request = moi();
            request.reject(NGUOI_DUYET, "Không đủ căn cứ", LUC);

            assertThatThrownBy(() -> request.approve(NGUOI_DUYET, "duyệt bù", LUC))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(request.status()).isEqualTo(ChangeRequestStatus.REJECTED);
        }

        @Test
        @DisplayName("PENDING là trạng thái mở duy nhất")
        void chiPendingLaMo() {
            assertThat(ChangeRequestStatus.PENDING.isOpen()).isTrue();
            assertThat(ChangeRequestStatus.APPROVED.isFinal()).isTrue();
            assertThat(ChangeRequestStatus.REJECTED.isFinal()).isTrue();
            assertThat(ChangeRequestStatus.CANCELLED.isFinal()).isTrue();
        }
    }

    @Nested
    @DisplayName("Người gửi rút lại")
    class RutLai {

        @Test
        @DisplayName("Chính người gửi rút được — không cần luật không tự duyệt")
        void nguoiGuiRutDuoc() {
            ChangeRequest request = moi();

            request.cancel(NGUOI_GUI, LUC);

            assertThat(request.status()).isEqualTo(ChangeRequestStatus.CANCELLED);
            assertThat(request.reviewerId()).isEqualTo(NGUOI_GUI);
            assertThat(request.reviewedAt()).isEqualTo(LUC);
        }

        @Test
        @DisplayName("Người khác không rút hộ được, kể cả người có quyền duyệt")
        void nguoiKhacKhongRutHo() {
            ChangeRequest request = moi();

            assertThatThrownBy(() -> request.cancel(NGUOI_DUYET, LUC))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Chi nguoi gui");
        }
    }

    @Nested
    @DisplayName("Ảnh chụp cho audit_log")
    class AnhChupAudit {

        @Test
        @DisplayName("Chỉ ghi TÊN trường được đề nghị sửa, KHÔNG ghi giá trị")
        void khongChepGiaTriPayload() {
            // audit_log la bang chi ghi them, co trigger chan UPDATE. Mot so dien thoai lot vao do
            // la lot vinh vien — chi con cach xoa dong, ma xoa dong nhat ky thi con te hon.
            ChangeRequest request = moi();

            Map<String, Object> snapshot = request.auditSnapshot();

            assertThat(snapshot).containsKey("payloadFields");
            @SuppressWarnings("unchecked")
            java.util.List<String> fields = (java.util.List<String>) snapshot.get("payloadFields");
            assertThat(fields).containsExactly("deathLunar", "phone");
            assertThat(snapshot).doesNotContainKey("payload");
            assertThat(snapshot.toString()).doesNotContain("0900000000");
        }

        @Test
        @DisplayName("Ghi đủ ai gửi, ai duyệt, nhắm vào nhân khẩu và chi nào")
        void ghiDuNguoiVaDoiTuong() {
            ChangeRequest request = moi();
            request.approve(NGUOI_DUYET, "ok", LUC);

            Map<String, Object> snapshot = request.auditSnapshot();

            assertThat(snapshot)
                    .containsEntry("status", "APPROVED")
                    .containsEntry("requestedBy", NGUOI_GUI.toString())
                    .containsEntry("reviewerId", NGUOI_DUYET.toString())
                    .containsEntry("personId", NHAN_KHAU.toString())
                    .containsEntry("targetBranchId", CHI_DICH.toString())
                    .containsEntry("type", "UPDATE_PERSON");
        }
    }

    @Test
    @DisplayName("Loại yêu cầu lạ bị từ chối kèm tên loại rõ ràng")
    void loaiLa() {
        assertThatThrownBy(() -> ChangeRequestType.of("XOA_HAN_NHAN_KHAU"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("XOA_HAN_NHAN_KHAU");
        assertThatThrownBy(() -> ChangeRequestType.of(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ChangeRequestType.of(" update_person ")).isEqualTo(ChangeRequestType.UPDATE_PERSON);
    }
}
