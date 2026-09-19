package vn.giapha.dataimport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Đọc ô "Ngày mất âm" theo quy ước gõ của trang Hướng dẫn. */
class LunarDeathDateTest {

    @Test
    @DisplayName("15/8 — ngày và tháng, không rõ năm là HỢP LỆ")
    void ngayThangKhongNam() {
        LunarDeathDate d = LunarDeathDate.doc("15/8").orElseThrow().value();
        assertThat(d.day()).isEqualTo(15);
        assertThat(d.month()).isEqualTo(8);
        assertThat(d.coNam()).isFalse();
        assertThat(d.leap()).isFalse();
    }

    @Test
    @DisplayName("Tháng nhuận nhận cả có dấu lẫn không dấu")
    void thangNhuan() {
        assertThat(LunarDeathDate.doc("15/8 nhuận").orElseThrow().value().leap()).isTrue();
        assertThat(LunarDeathDate.doc("15/8 nhuan").orElseThrow().value().leap()).isTrue();
        assertThat(LunarDeathDate.doc("15/8").orElseThrow().value().leap()).isFalse();
    }

    @Test
    @DisplayName("Có năm, đặt trước hoặc sau chữ nhuận")
    void coNam() {
        assertThat(LunarDeathDate.doc("15/8/1945").orElseThrow().value().year()).isEqualTo(1945);
        assertThat(LunarDeathDate.doc("15/8 nhuận 1945").orElseThrow().value().year()).isEqualTo(1945);
        assertThat(LunarDeathDate.doc("15/8 nhuận 1945").orElseThrow().value().leap()).isTrue();
    }

    @Test
    @DisplayName("Ô trống là hợp lệ — người còn sống không có ngày giỗ")
    void oTrong() {
        assertThat(LunarDeathDate.doc(null)).isEmpty();
        assertThat(LunarDeathDate.doc("   ")).isEmpty();
    }

    @Test
    @DisplayName("Số sê-ri của Excel bị bắt, KHÔNG bị đoán ngược thành ngày âm")
    void soSeRi() {
        // Neu cho qua thi day se thanh mot ngay gio sai, khong loi, khong log — ca ho cung nham
        // ngay, moi nam mot lan.
        assertThat(LunarDeathDate.doc("45889").orElseThrow().loi())
                .isEqualTo(IssueCode.IMP_LUNAR_DATE_IS_SERIAL);
        assertThat(LunarDeathDate.doc(LunarDeathDate.DAU_NGAY_DUONG + "45889.0").orElseThrow().loi())
                .isEqualTo(IssueCode.IMP_LUNAR_DATE_IS_SERIAL);
    }

    @Test
    @DisplayName("Ô không đọc được báo lỗi riêng, không im lặng bỏ qua")
    void khongDocDuoc() {
        assertThat(LunarDeathDate.doc("khoảng tháng tám").orElseThrow().loi())
                .isEqualTo(IssueCode.IMP_LUNAR_DATE_UNPARSEABLE);
        assertThat(LunarDeathDate.doc("31/8").orElseThrow().loi())
                .isEqualTo(IssueCode.IMP_LUNAR_DATE_UNPARSEABLE);
        assertThat(LunarDeathDate.doc("15/13").orElseThrow().loi())
                .isEqualTo(IssueCode.IMP_LUNAR_DATE_UNPARSEABLE);
    }

    @Test
    @DisplayName("Hiển thị lại đúng quy ước gõ, để người nhập nhận ra chính ô mình đã gõ")
    void hienThiLai() {
        assertThat(LunarDeathDate.doc("15/8 nhuận 1945").orElseThrow().value())
                .hasToString("15/8 nhuận/1945");
        assertThat(LunarDeathDate.doc("15/8").orElseThrow().value()).hasToString("15/8");
    }
}
