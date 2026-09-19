package vn.giapha.dataimport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Chuẩn hoá ô chữ — lớp test quan trọng nhất của bộ đọc, vì mọi lỗi ở đây đều <b>vô hình</b>.
 */
class TextNormalizerTest {

    @Test
    @DisplayName("NFD thành NFC: hai chuỗi trông giống hệt nhau phải trở thành bằng nhau")
    void nfdThanhNfc() {
        String nfc = "Nguyễn Văn Cẩn";
        String nfd = Normalizer.normalize(nfc, Normalizer.Form.NFD);

        // Day la tien de cua ca bai test: truoc khi chuan hoa, hai chuoi KHONG bang nhau.
        assertThat(nfd).isNotEqualTo(nfc);

        assertThat(TextNormalizer.normalize(nfd)).isEqualTo(nfc);
        assertThat(TextNormalizer.laNfc(TextNormalizer.normalize(nfd))).isTrue();
    }

    @Test
    @DisplayName("Khoảng trắng cứng từ Word và ký tự zero-width bị dọn sạch")
    void donKhoangTrangVaKyTuVoHinh() {
        String banFromWord = "Nguyễn Văn​ Cẩn﻿";
        assertThat(TextNormalizer.normalize(banFromWord)).isEqualTo("Nguyễn Văn Cẩn");
    }

    @Test
    @DisplayName("Khoảng trắng liên tiếp gộp lại, hai đầu cắt đi")
    void gopKhoangTrang() {
        assertThat(TextNormalizer.normalize("  Nguyễn   Văn  Cẩn  ")).isEqualTo("Nguyễn Văn Cẩn");
    }

    @Test
    @DisplayName("Ô trống trả null, không trả chuỗi rỗng")
    void oTrongTraNull() {
        assertThat(TextNormalizer.normalize("   ")).isNull();
        assertThat(TextNormalizer.normalize(null)).isNull();
    }

    @Test
    @DisplayName("Mã: at-02-001 và AT - 02 - 001 là một")
    void maNangHoaVaBoKhoangTrang() {
        assertThat(TextNormalizer.normalizeCode("at-02-001")).isEqualTo("AT-02-001");
        assertThat(TextNormalizer.normalizeCode("AT - 02 - 001")).isEqualTo("AT-02-001");
        assertThat(TextNormalizer.normalizeCode(" ")).isNull();
    }

    @Test
    @DisplayName("Chuẩn hoá KHÔNG được bỏ dấu tiếng Việt")
    void khongBoDau() {
        // Bo dau o day thi ten trong pha bien thanh "Nguyen Van Can" vinh vien. Cot khong dau la
        // viec cua CSDL (vn_unaccent), khong phai cua buoc doc tep.
        assertThat(TextNormalizer.normalize("Nguyễn Văn Cẩn")).contains("ễ", "ă", "ẩ");
    }
}
