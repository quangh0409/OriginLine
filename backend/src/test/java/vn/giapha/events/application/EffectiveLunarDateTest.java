package vn.giapha.events.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventSubject;
import vn.giapha.events.domain.EventType;
import vn.giapha.shared.vo.LunarDate;

/**
 * {@code person.death_lunar} là <b>nguồn chân lý</b> của ngày giỗ (BA v2).
 *
 * <p>Người trong họ sửa ngày mất trên hồ sơ nhân khẩu và mong cả họ được nhắc theo ngày mới. Bắt họ
 * nhớ sửa thêm một bản ghi sự kiện là thiết kế sai — nên khi hai giá trị lệch nhau, <b>hồ sơ nhân
 * khẩu thắng</b>.</p>
 */
class EffectiveLunarDateTest {

    private static final UUID PERSON = UUID.randomUUID();
    private static final UUID BRANCH = UUID.randomUUID();

    private static Event gio(LunarDate lunarTrenSuKien) {
        return EventFixtures.gio(UUID.randomUUID(), PERSON, lunarTrenSuKien, BRANCH);
    }

    private static EventSubject nguoiDaKhuat(LunarDate deathLunar) {
        return EventFixtures.deceased(PERSON, deathLunar, EventFixtures.branch(BRANCH, "root.chi1"));
    }

    private static EventSubject nguoiConSong() {
        return EventFixtures.alive(PERSON, EventFixtures.branch(BRANCH, "root.chi1"));
    }

    /**
     * Sau khi đính chính "thật ra cụ còn sống", {@code person.death_lunar} bị gỡ nhưng bản ghi
     * {@code event} kiểu GIO <b>vẫn giữ ngày âm của riêng nó</b>. Nếu lúc này vẫn lui về ngày trên
     * sự kiện thì cả chi/ngành tiếp tục nhận nhắc giỗ của một người đang sống — sai, và rất mất
     * lòng trong bối cảnh dòng họ.
     */
    @Test
    @DisplayName("Gio cua nguoi CON SONG: khong co ngay am hieu luc, khong sinh lich nhac")
    void nguoiConSongThiKhongCoGio() {
        Event event = gio(LunarDate.of(1985, 3, 15));

        assertThat(EffectiveLunarDate.of(event, nguoiConSong()))
                .as("nguoi dang song thi khong co gio, du ban ghi su kien con ngay am")
                .isNull();
    }

    /**
     * Ngược lại: đã khuất mà khuyết ngày mất là chuyện thường của gia phả cổ. Lúc đó ngày âm chép
     * trong bảng {@code event} là nguồn duy nhất và <b>hợp lệ</b> — không được nhầm sang ca trên.
     */
    @Test
    @DisplayName("Da khuat nhung khuyet ngay mat: van lay ngay am chep tren su kien")
    void daKhuatKhuyetNgayThiVanLayTheoSuKien() {
        Event event = gio(LunarDate.of(1985, 3, 15));

        assertThat(EffectiveLunarDate.of(event, nguoiDaKhuat(null)))
                .isEqualTo(LunarDate.of(1985, 3, 15));
    }

    @Test
    @DisplayName("Gio: death_lunar cua ho so THANG ngay am chep trong bang event")
    void hoSoNhanKhauThang() {
        Event event = gio(LunarDate.of(1985, 3, 15));
        EventSubject subject = nguoiDaKhuat(LunarDate.of(1985, 3, 12));

        LunarDate hieuLuc = EffectiveLunarDate.of(event, subject);

        assertThat(hieuLuc).isEqualTo(LunarDate.of(1985, 3, 12));
    }

    @Test
    @DisplayName("Gio: co nguoi nhung ho so khuyet death_lunar thi dung ngay am cua su kien")
    void hoSoKhuyetThiDungNgayCuaSuKien() {
        Event event = gio(LunarDate.of(1985, 3, 15));
        EventSubject subject = nguoiDaKhuat(null);

        assertThat(EffectiveLunarDate.of(event, subject)).isEqualTo(LunarDate.of(1985, 3, 15));
    }

    @Test
    @DisplayName("Su kien cap dong ho khong gan nhan khau thi dung ngay am cua chinh no")
    void suKienCapDongHoDungNgayCuaChinhNo() {
        Event gioTo = EventFixtures.gioTo(UUID.randomUUID(), LunarDate.of(1700, 1, 10));

        assertThat(EffectiveLunarDate.of(gioTo, null)).isEqualTo(LunarDate.of(1700, 1, 10));
    }

    @Test
    @DisplayName("Khong phai gio thi death_lunar KHONG duoc lan sang - te le co ngay cua rieng no")
    void khongPhaiGioThiKhongLayDeathLunar() {
        Event teLe = Event.builder(UUID.randomUUID())
                .type(EventType.TE_LE)
                .personId(PERSON)
                .title("Te le tai tu duong")
                .lunarDate(LunarDate.of(2020, 8, 1))
                .lunarBased(true)
                .recurring(true)
                .build();

        LunarDate hieuLuc = EffectiveLunarDate.of(teLe, nguoiDaKhuat(LunarDate.of(1985, 3, 12)));

        assertThat(hieuLuc).isEqualTo(LunarDate.of(2020, 8, 1));
    }

    @Test
    @DisplayName("Co nhuan cua death_lunar duoc giu nguyen, khong bi lam phang")
    void giuNguyenCoNhuan() {
        Event event = gio(LunarDate.of(1985, 6, 10));
        EventSubject subject = nguoiDaKhuat(LunarDate.ofLeap(1985, 6, 10));

        LunarDate hieuLuc = EffectiveLunarDate.of(event, subject);

        // Cụ mất vào tháng 6 NHUẬN. Đánh rơi cờ nhuận ở đây là cả họ giỗ lệch đúng một tháng.
        assertThat(hieuLuc.leapMonth()).isTrue();
        assertThat(hieuLuc).isEqualTo(LunarDate.ofLeap(1985, 6, 10));
    }

    @Test
    @DisplayName("Nam am cua death_lunar la nam mat lich su, khong tham gia so sanh gio hang nam")
    void namAmChiMangTinhLichSu() {
        Event event = gio(LunarDate.of(2024, 3, 12));
        EventSubject subject = nguoiDaKhuat(LunarDate.of(1985, 3, 12));

        // Cùng ngày, cùng tháng, khác năm -> không phải lệch dữ liệu, chỉ là giỗ lặp hằng năm.
        assertThat(EffectiveLunarDate.of(event, subject).day()).isEqualTo(12);
        assertThat(EffectiveLunarDate.of(event, subject).month()).isEqualTo(3);
    }
}
