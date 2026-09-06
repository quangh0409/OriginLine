package vn.giapha.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.vo.BranchPath;

/**
 * Dòng phân công vai trò kèm phạm vi — nguồn dữ liệu <b>duy nhất</b> nói Trưởng chi nào được đụng
 * vào nhánh nào. Vai nằm trong JWT, phạm vi thì không và không suy ra được từ token.
 */
class BranchAssignmentTest {

    private static final BranchPath CHI_GIAP = BranchPath.of("goc.chi_giap");
    private static final BranchPath NGANH_TRUONG = BranchPath.of("goc.chi_giap.nganh_truong");
    private static final BranchPath CHI_AT = BranchPath.of("goc.chi_at");

    private static BranchAssignment branchHead(BranchPath path, LocalDate from, LocalDate to) {
        return new BranchAssignment(UUID.randomUUID(), UUID.randomUUID(), RoleCode.BRANCH_HEAD,
                UUID.randomUUID(), path, from, to, null, null);
    }

    @Nested
    @DisplayName("Phạm vi phủ tới đâu")
    class PhamVi {

        @Test
        @DisplayName("Phủ đúng chi được giao và mọi hậu duệ, không phủ nhánh bên cạnh")
        void phuChiVaHauDue() {
            BranchAssignment truongChiGiap = branchHead(CHI_GIAP, null, null);

            assertThat(truongChiGiap.covers(CHI_GIAP)).isTrue();
            assertThat(truongChiGiap.covers(NGANH_TRUONG)).isTrue();
            assertThat(truongChiGiap.covers(CHI_AT)).isFalse();
        }

        @Test
        @DisplayName("branch_id null nghĩa là toàn dòng họ")
        void khongGanChiLaToanDongHo() {
            BranchAssignment hoiDong = new BranchAssignment(UUID.randomUUID(), UUID.randomUUID(),
                    RoleCode.COUNCIL, null, null, null, null, null, null);

            assertThat(hoiDong.coversEverything()).isTrue();
            assertThat(hoiDong.covers(CHI_AT)).isTrue();
            // Ke ca doi tuong chua gan chi.
            assertThat(hoiDong.covers(null)).isTrue();
        }

        @Test
        @DisplayName("Đối tượng chưa gắn chi thì phân công có phạm vi KHÔNG phủ")
        void doiTuongChuaGanChi() {
            assertThat(branchHead(CHI_GIAP, null, null).covers(null)).isFalse();
        }

        @Test
        @DisplayName("Dữ liệu hỏng: BRANCH_HEAD mà thiếu branch_id bị coi là toàn quyền")
        void duLieuHongLaToanQuyen() {
            // Day chinh la ly do RoleAssignmentService phai chan tu luc CAP: neu mot dong nhu the
            // lot vao bang, no im lang tro thanh mot Hoi dong Toc bieu doi ten khac.
            BranchAssignment hong = new BranchAssignment(UUID.randomUUID(), UUID.randomUUID(),
                    RoleCode.BRANCH_HEAD, null, null, null, null, null, "du lieu sai");

            assertThat(hong.coversEverything()).isTrue();
            assertThat(hong.covers(CHI_AT)).isTrue();
        }

        @Test
        @DisplayName("Có branch_id nhưng chưa phân giải được path thì không phủ gì")
        void thieuPathThiKhongPhu() {
            // Chi da bi xoa mem -> path khong tra ra. Huong an toan: quyen HEP hon, khong rong hon.
            BranchAssignment thieuPath = new BranchAssignment(UUID.randomUUID(), UUID.randomUUID(),
                    RoleCode.BRANCH_HEAD, UUID.randomUUID(), null, null, null, null, null);

            assertThat(thieuPath.coversEverything()).isFalse();
            assertThat(thieuPath.covers(CHI_GIAP)).isFalse();
        }
    }

    @Nested
    @DisplayName("Hiệu lực theo nhiệm kỳ")
    class HieuLuc {

        private final LocalDate homNay = LocalDate.of(2026, 9, 5);

        @Test
        @DisplayName("Hai mốc để trống nghĩa là vô thời hạn")
        void voThoiHan() {
            assertThat(branchHead(CHI_GIAP, null, null).isActiveOn(homNay)).isTrue();
        }

        @Test
        @DisplayName("Nhiệm kỳ đã mãn thì hết hiệu lực")
        void nhiemKyDaMan() {
            BranchAssignment daMan = branchHead(CHI_GIAP,
                    homNay.minusYears(5), homNay.minusDays(1));

            assertThat(daMan.isActiveOn(homNay)).isFalse();
            // Nhung van tra cuu nguoc duoc: hom qua thi con hieu luc.
            assertThat(daMan.isActiveOn(homNay.minusDays(1))).isTrue();
        }

        @Test
        @DisplayName("Nhiệm kỳ chưa bắt đầu thì chưa có hiệu lực")
        void chuaBatDau() {
            assertThat(branchHead(CHI_GIAP, homNay.plusDays(1), null).isActiveOn(homNay)).isFalse();
        }

        @Test
        @DisplayName("Hai mốc bao trọn ngày đang xét, tính cả hai đầu mút")
        void baoTronKeCaDauMut() {
            BranchAssignment nhiemKy = branchHead(CHI_GIAP, homNay, homNay);

            assertThat(nhiemKy.isActiveOn(homNay)).isTrue();
            assertThat(nhiemKy.isActiveOn(homNay.minusDays(1))).isFalse();
            assertThat(nhiemKy.isActiveOn(homNay.plusDays(1))).isFalse();
        }

        @Test
        @DisplayName("valid_to sớm hơn valid_from là dữ liệu vô nghĩa, bị chặn ngay lúc dựng")
        void mocNguocBiChan() {
            assertThatThrownBy(() -> branchHead(CHI_GIAP, homNay, homNay.minusDays(1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("valid_to");
        }
    }

    @Test
    @DisplayName("appUserId và role là bắt buộc — một dòng cấp quyền vô danh là dòng không kiểm được")
    void truongBatBuoc() {
        assertThatThrownBy(() -> new BranchAssignment(UUID.randomUUID(), null,
                RoleCode.BRANCH_HEAD, UUID.randomUUID(), CHI_GIAP, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BranchAssignment(UUID.randomUUID(), UUID.randomUUID(),
                null, UUID.randomUUID(), CHI_GIAP, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
