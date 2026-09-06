package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Quan hệ bao hàm giữa các tầng hiển thị (BA v2 §10).
 *
 * <p>{@code PUBLIC} là tầng của <b>người đã khuất</b> — nó không nằm trên cùng một trục với
 * T1/T2/T3 (vốn nói về người còn sống), nên phép so sánh phải được đọc kỹ trước khi dùng lại ở
 * chỗ khác: {@code PUBLIC.atLeast(T3)} trả {@code true}, tức mọi lối dùng
 * {@code tier.atLeast(T3)} để quyết định "có được xem dữ liệu Tầng 3 không" sẽ <b>mở khoá cho cả
 * người đã khuất</b>.
 */
class VisibleTierTest {

    @Test
    @DisplayName("T3 bao hàm T2 và T1; T1 không bao hàm T2")
    void thuTuBaoHamCuaBaTangNguoiConSong() {
        assertThat(VisibleTier.T3.atLeast(VisibleTier.T1)).isTrue();
        assertThat(VisibleTier.T3.atLeast(VisibleTier.T2)).isTrue();
        assertThat(VisibleTier.T3.atLeast(VisibleTier.T3)).isTrue();

        assertThat(VisibleTier.T2.atLeast(VisibleTier.T1)).isTrue();
        assertThat(VisibleTier.T2.atLeast(VisibleTier.T3)).isFalse();

        assertThat(VisibleTier.T1.atLeast(VisibleTier.T1)).isTrue();
        assertThat(VisibleTier.T1.atLeast(VisibleTier.T2)).isFalse();
        assertThat(VisibleTier.T1.atLeast(VisibleTier.T3)).isFalse();
    }

    @Test
    @DisplayName("PUBLIC bao hàm mọi tầng phả hệ — kể cả T3")
    void publicBaoHamMoiTang() {
        assertThat(VisibleTier.PUBLIC.atLeast(VisibleTier.T1)).isTrue();
        assertThat(VisibleTier.PUBLIC.atLeast(VisibleTier.T2)).isTrue();
        assertThat(VisibleTier.PUBLIC.atLeast(VisibleTier.T3))
                .as("hop dong hien tai cua atLeast: dung no lam dieu kien mo khoa Tang 3 se lo "
                        + "du lieu lien he cua nguoi da khuat")
                .isTrue();
    }

    @Test
    @DisplayName("Không tầng phả hệ nào bao hàm PUBLIC")
    void khongTangNaoBaoHamPublic() {
        assertThat(VisibleTier.T1.atLeast(VisibleTier.PUBLIC)).isFalse();
        assertThat(VisibleTier.T2.atLeast(VisibleTier.PUBLIC)).isFalse();
        assertThat(VisibleTier.T3.atLeast(VisibleTier.PUBLIC)).isFalse();
        assertThat(VisibleTier.PUBLIC.atLeast(VisibleTier.PUBLIC)).isTrue();
    }
}
