package vn.giapha.dataimport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.vo.Gender;

/** Từ vựng tiếng Việt trong các ô có danh sách chọn. */
class CellCodecTest {

    @Test
    @DisplayName("Giới: rộng rãi với cách gõ, vì danh sách chọn của Excel không phải bảo mật")
    void gioi() {
        assertThat(CellCodec.gioi("Nam")).isEqualTo(Gender.MALE);
        assertThat(CellCodec.gioi("nam")).isEqualTo(Gender.MALE);
        assertThat(CellCodec.gioi("M")).isEqualTo(Gender.MALE);
        assertThat(CellCodec.gioi("Nữ")).isEqualTo(Gender.FEMALE);
        assertThat(CellCodec.gioi("nu")).isEqualTo(Gender.FEMALE);
        assertThat(CellCodec.gioi("")).isEqualTo(Gender.UNKNOWN);
        assertThat(CellCodec.gioi("chưa rõ")).isEqualTo(Gender.UNKNOWN);
    }

    @Test
    @DisplayName("Còn sống: ô TRỐNG trả null, KHÁC HẲN với đã mất")
    void conSongOTrongTraNull() {
        // Mac dinh "o trong = con song" sinh ra mot pha do toan nguoi song tu doi thu ba.
        assertThat(CellCodec.conSong(null)).isNull();
        assertThat(CellCodec.conSong("  ")).isNull();
        assertThat(CellCodec.conSong("Có")).isTrue();
        assertThat(CellCodec.conSong("Không")).isFalse();
        assertThat(CellCodec.conSong("đã mất")).isFalse();
    }

    @Test
    @DisplayName("Quan hệ: mặc định con ruột, nhận ra con nuôi")
    void quanHe() {
        assertThat(CellCodec.quanHe(null)).isEqualTo(CellCodec.ParentRel.BIO);
        assertThat(CellCodec.quanHe("Ruột")).isEqualTo(CellCodec.ParentRel.BIO);
        assertThat(CellCodec.quanHe("Con nuôi")).isEqualTo(CellCodec.ParentRel.ADOPT);
        assertThat(CellCodec.quanHe("nghĩa tử")).isEqualTo(CellCodec.ParentRel.ADOPT);
    }

    @Test
    @DisplayName("Bậc hôn phối: vợ cả là 1, và một con số thì lấy nguyên")
    void bac() {
        assertThat(CellCodec.bac("Vợ cả")).isEqualTo(1);
        assertThat(CellCodec.bac("chính thất")).isEqualTo(1);
        assertThat(CellCodec.bac("Vợ hai")).isEqualTo(2);
        assertThat(CellCodec.bac("vợ lẽ")).isEqualTo(2);
        assertThat(CellCodec.bac("3")).isEqualTo(3);
        assertThat(CellCodec.bac("không rõ")).isNull();
    }

    @Test
    @DisplayName("Loại kế tự khớp đúng heir_type của lược đồ")
    void loaiKeTu() {
        assertThat(CellCodec.loaiKeTu("Đích tôn")).isEqualTo(CellCodec.HeirType.DICH_TON);
        assertThat(CellCodec.loaiKeTu("thừa tự")).isEqualTo(CellCodec.HeirType.THUA_TU);
        assertThat(CellCodec.loaiKeTu("Kế tự")).isEqualTo(CellCodec.HeirType.KE_TU);
        assertThat(CellCodec.loaiKeTu("abc")).isNull();
    }

    @Test
    @DisplayName("Số: chịu được đuôi .0 mà Excel gắn vào ô kiểu số")
    void so() {
        // Nguon cua mot lop loi "nam sinh khong doc duoc" rat kho hieu neu khong biet.
        assertThat(CellCodec.so("1945")).isEqualTo(1945);
        assertThat(CellCodec.so("1945.0")).isEqualTo(1945);
        assertThat(CellCodec.so("1,945")).isEqualTo(1945);
        assertThat(CellCodec.so("khoảng 1945")).isEqualTo(1945);
        assertThat(CellCodec.so("")).isNull();
    }
}
