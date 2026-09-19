package vn.giapha.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Mã mời: sinh, chuẩn hoá, băm.
 *
 * <p>Ba nhóm ca, mỗi nhóm canh một ràng buộc đã chốt: <b>đoán không ra</b> (bảng chữ + độ dài +
 * không trùng), <b>gõ được bởi một cụ 70 tuổi</b> (chuẩn hoá dấu gạch, chữ thường, ký tự dễ nhầm),
 * và <b>không lưu mã thô</b> (băm tất định, một chiều).</p>
 */
@DisplayName("Mã mời")
class InvitationCodeTest {

    @Test
    @DisplayName("mã sinh ra luôn 10 ký tự, chỉ dùng bảng chữ đã loại I/L/O/U")
    void maSinhRaDungBangChu() {
        for (int i = 0; i < 200; i++) {
            String code = InvitationCode.generate();
            String normalized = InvitationCode.normalize(code);
            assertThat(normalized).hasSize(10);
            assertThat(normalized).matches("[0-9A-HJKMNP-TV-Z]{10}");
            // Bon ky tu de nham phai vang mat khoi ma in tren phieu giay.
            assertThat(normalized).doesNotContain("I").doesNotContain("L")
                    .doesNotContain("O").doesNotContain("U");
        }
    }

    @Test
    @DisplayName("mã được chia nhóm cho dễ đọc, và dấu gạch không phải một phần của giá trị")
    void maDuocChiaNhom() {
        String code = InvitationCode.generate();
        assertThat(code).matches("[0-9A-Z]{5}-[0-9A-Z]{5}");
        assertThat(InvitationCode.normalize(code)).doesNotContain("-");
    }

    @Test
    @DisplayName("200 mã liên tiếp không trùng nhau")
    void maKhongTrung() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            assertThat(seen.add(InvitationCode.normalize(InvitationCode.generate()))).isTrue();
        }
    }

    @Test
    @DisplayName("chữ thường, khoảng trắng và dấu gạch đều cho ra cùng một mã")
    void chuanHoaCachGo() {
        String canonical = "K7M2QD9HFX";
        assertThat(InvitationCode.normalize("k7m2q-d9hfx")).isEqualTo(canonical);
        assertThat(InvitationCode.normalize("K7M2Q D9HFX")).isEqualTo(canonical);
        assertThat(InvitationCode.normalize("  K7M2Q-D9HFX  ")).isEqualTo(canonical);
        assertThat(InvitationCode.normalize("K7M2Q.D9HFX")).isEqualTo(canonical);
    }

    @Test
    @DisplayName("O gõ nhầm thay cho số 0, I/L thay cho số 1 — vẫn vào được")
    void chuanHoaKyTuDeNham() {
        // Moi lan go sai la mot cu dien thoai cho Truong chi; day la ly do phep dich nay ton tai.
        assertThat(InvitationCode.normalize("OOOOO-11111"))
                .isEqualTo(InvitationCode.normalize("00000-11111"));
        assertThat(InvitationCode.normalize("IIIII-LLLLL"))
                .isEqualTo("1111111111");
    }

    @Test
    @DisplayName("mã sai độ dài hoặc chứa ký tự lạ thì bị từ chối")
    void maSaiDinhDangBiTuChoi() {
        assertThatThrownBy(() -> InvitationCode.normalize("K7M2Q"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InvitationCode.normalize("K7M2Q-D9HFX-EXTRA"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InvitationCode.normalize("K7M2Q-D9HF@"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InvitationCode.normalize(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("băm tất định: cùng mã gõ theo mọi cách đều cho một băm")
    void bamTatDinh() {
        String hash = InvitationCode.hash("K7M2Q-D9HFX");
        assertThat(InvitationCode.hash("k7m2qd9hfx")).isEqualTo(hash);
        assertThat(InvitationCode.hash("K7M2Q D9HFX")).isEqualTo(hash);
        // Tat dinh la yeu cau bat buoc: tra cuu di qua ux_invitation_code_hash.
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("băm không chứa mã thô — đây là cả điểm của việc lưu băm")
    void bamKhongChuaMaTho() {
        String code = "K7M2Q-D9HFX";
        String hash = InvitationCode.hash(code);
        assertThat(hash).doesNotContain("K7M2Q").doesNotContain("D9HFX")
                .doesNotContain("K7M2QD9HFX").doesNotContain(code);
    }

    @Test
    @DisplayName("hai mã khác nhau cho hai băm khác nhau")
    void bamPhanBietDuocMa() {
        assertThat(InvitationCode.hash("K7M2Q-D9HFX"))
                .isNotEqualTo(InvitationCode.hash("K7M2Q-D9HFY"));
    }
}
