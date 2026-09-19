package vn.giapha.events.api.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vn.giapha.events.domain.EventType;

/**
 * Quy đổi loại sự kiện phải <b>không mất thông tin</b>.
 *
 * <p>Trước V10, bốn giá trị cơ sở dữ liệu cùng ra một mã {@code KHAC}, nên qua API một buổi họp họ
 * không phân biệt được với một đám cưới và màn "lịch việc họ" không lọc nổi thứ nó cần. Ca test
 * chốt ở đây là {@link KhongMatThongTin#moiLoaiRaMotMaRieng()}: nó đếm số mã đầu ra và so với số
 * giá trị enum, nên bất kỳ ai gộp lại hai loại trong tương lai đều làm nó đỏ ngay.</p>
 */
@DisplayName("EventTypeApiMapper — quy đổi mã loại sự kiện")
class EventTypeApiMapperTest {

    @Nested
    @DisplayName("CSDL → hợp đồng")
    class KhongMatThongTin {

        @Test
        @DisplayName("mỗi loại ra đúng một mã riêng — không loại nào bị gộp")
        void moiLoaiRaMotMaRieng() {
            Set<String> maCapChi = new HashSet<>();
            for (EventType type : EventType.values()) {
                maCapChi.add(EventTypeApiMapper.toApi(type, false));
            }
            assertThat(maCapChi)
                    .as("Gop hai loai lam mot la mat thong tin khong khoi phuc duoc o phia client")
                    .hasSize(EventType.values().length);
        }

        @Test
        @DisplayName("họp họ, cưới hỏi, khánh thành và 'khác' là bốn thứ khác nhau")
        void bonLoaiTungBiGopNayDaTachRa() {
            assertThat(EventTypeApiMapper.toApi(EventType.HOP_HO, false)).isEqualTo("HOP_HO");
            assertThat(EventTypeApiMapper.toApi(EventType.CUOI_HOI, false)).isEqualTo("CUOI_HOI");
            assertThat(EventTypeApiMapper.toApi(EventType.KHANH_THANH, false))
                    .isEqualTo("KHANH_THANH");
            assertThat(EventTypeApiMapper.toApi(EventType.KHAC, false)).isEqualTo("KHAC");
        }

        @Test
        @DisplayName("sinh nhật không còn hoá thành mừng thọ")
        void sinhNhatTachKhoiMungTho() {
            assertThat(EventTypeApiMapper.toApi(EventType.SINH_NHAT, false)).isEqualTo("SINH_NHAT");
            assertThat(EventTypeApiMapper.toApi(EventType.MUNG_THO, false)).isEqualTo("MUNG_THO");
        }

        @Test
        @DisplayName("giỗ đầu / giỗ hết có mã riêng, không gộp vào giỗ thường")
        void tieuTuongDaiTuongCoMaRieng() {
            assertThat(EventTypeApiMapper.toApi(EventType.TIEU_TUONG, false))
                    .isEqualTo("TIEU_TUONG");
            assertThat(EventTypeApiMapper.toApi(EventType.DAI_TUONG, false)).isEqualTo("DAI_TUONG");
            assertThat(EventTypeApiMapper.toApi(EventType.GIO, false)).isEqualTo("GIO_THUONG");
        }

        @Test
        @DisplayName("TE_LE là ngoại lệ có lý: tách theo cờ cấp dòng họ")
        void teLeTachTheoCapDongHo() {
            assertThat(EventTypeApiMapper.toApi(EventType.TE_LE, true)).isEqualTo("GIO_HO");
            assertThat(EventTypeApiMapper.toApi(EventType.TE_LE, false)).isEqualTo("GIO_CHI");
        }

        @Test
        @DisplayName("chạp mả giữ nguyên mã hợp đồng CHAP_MA")
        void taoMoThanhChapMa() {
            assertThat(EventTypeApiMapper.toApi(EventType.TAO_MO, false)).isEqualTo("CHAP_MA");
        }
    }

    @Nested
    @DisplayName("hợp đồng → CSDL")
    class ChieuNguoc {

        @ParameterizedTest
        @EnumSource(EventType.class)
        @DisplayName("đi một vòng rồi quay lại chính nó")
        void quayVongTraVeChinhNo(EventType type) {
            String ma = EventTypeApiMapper.toApi(type, type == EventType.TE_LE);
            assertThat(EventTypeApiMapper.toDomain(List.of(ma))).contains(type);
        }

        @Test
        @DisplayName("bộ lọc TIEU_TUONG / DAI_TUONG không còn rơi vào hư vô")
        void tieuTuongDaiTuongLocDuocSauV10() {
            assertThat(EventTypeApiMapper.toDomain(List.of("TIEU_TUONG", "DAI_TUONG")))
                    .as("Truoc V10 hai bo loc nay khong bao gio khop dong nao")
                    .containsExactlyInAnyOrder(EventType.TIEU_TUONG, EventType.DAI_TUONG);
        }

        @Test
        @DisplayName("KHAC chỉ còn khớp đúng KHAC, không nở ra bốn giá trị")
        void khacKhongConNoRa() {
            assertThat(EventTypeApiMapper.toDomain(List.of("KHAC")))
                    .containsExactly(EventType.KHAC);
        }

        @Test
        @DisplayName("GIO_HO và GIO_CHI cùng về TE_LE, gộp lại chỉ còn một giá trị")
        void gioHoVaGioChiCungVeTeLe() {
            assertThat(EventTypeApiMapper.toDomain(List.of("GIO_HO", "GIO_CHI")))
                    .containsExactly(EventType.TE_LE);
        }

        @Test
        @DisplayName("chữ thường, khoảng trắng thừa và giá trị rỗng đều xử lý được")
        void chapNhanDauVaoBanThiu() {
            assertThat(EventTypeApiMapper.toDomain(Arrays.asList("  hop_ho  ", "", null, "   ")))
                    .containsExactly(EventType.HOP_HO);
        }

        @Test
        @DisplayName("mã lạ bị bỏ qua chứ không ném lỗi — client cũ không làm sập cả trang danh sách")
        void maLaBiBoQua() {
            assertThat(EventTypeApiMapper.toDomain(List.of("KHONG_CO_THAT", "HOP_HO")))
                    .containsExactly(EventType.HOP_HO);
        }

        @Test
        @DisplayName("không có tham số lọc thì trả tập rỗng")
        void khongCoThamSoThiRong() {
            assertThat(EventTypeApiMapper.toDomain(null)).isEmpty();
            assertThat(EventTypeApiMapper.toDomain(List.of())).isEmpty();
        }
    }

    @Test
    @DisplayName("mừng thọ và sinh nhật đều là sự kiện của người còn sống")
    void mungThoChiuPhanTangRiengTu() {
        assertThat(EventType.MUNG_THO.isAboutLivingPerson()).isTrue();
        assertThat(EventType.SINH_NHAT.isAboutLivingPerson()).isTrue();
    }

    @Test
    @DisplayName("giỗ đầu / giỗ hết là lễ cúng tổ tiên — quyết định văn phong thông báo")
    void tieuTuongLaLeCungToTien() {
        assertThat(EventType.TIEU_TUONG.isAncestralRite()).isTrue();
        assertThat(EventType.DAI_TUONG.isAncestralRite()).isTrue();
    }
}
