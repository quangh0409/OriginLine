package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonFixtures;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.Gender;

/**
 * Đọc hồ sơ và các quan hệ trực tiếp, <b>đã qua bộ lọc riêng tư</b>.
 *
 * <p>Hai luật được ghim ở đây. Một: "không tồn tại" và "không được biết là tồn tại" trả cùng một
 * kết quả — {@code 404}, vì trả {@code 403} là tự xác nhận người đó có thật. Hai: một cạnh quan hệ
 * chỉ xuất hiện khi <b>đầu kia</b> cũng hiển thị được; nếu không thì chỉ cần đếm số cạnh là suy ra
 * được có một người còn sống đang bị ẩn ở đầu bên kia.
 */
class PersonQueryServiceTest {

    private GenealogyServiceFixture fx;
    private Person cuTo;
    private Person conSong;

    @BeforeEach
    void dungHo() {
        fx = new GenealogyServiceFixture();
        cuTo = fx.seed(PersonFixtures.daKhuat("Cụ Tổ", Gender.MALE), fx.chiGiap);
        conSong = fx.seed(PersonFixtures.nam("Người Còn Sống"), fx.chiGiap);
        fx.seedParent(cuTo, conSong, false);
    }

    @AfterEach
    void dongPhien() {
        fx.dangXuat();
    }

    @Test
    @DisplayName("Khách xem người đã khuất: trả hồ sơ công khai")
    void khachXemNguoiDaKhuat() {
        fx.dangXuat();

        PersonView view = fx.query.byId(cuTo.rawId());

        assertThat(view.displayName()).isEqualTo("Cụ Tổ");
        assertThat(view.alive()).isFalse();
    }

    @Test
    @DisplayName("Khách xem người còn sống: 404, KHÔNG phải 403")
    void khachXemNguoiConSongTra404() {
        fx.dangXuat();

        assertThatThrownBy(() -> fx.query.byId(conSong.rawId()))
                .as("tra 403 la tu xac nhan nguoi do co that — dung thu ma viec loc dang co giau")
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.NOT_FOUND);

        assertThat(fx.query.find(conSong.rawId()))
                .as("ban GraphQL cua cung ngu nghia do la null")
                .isEmpty();
    }

    @Test
    @DisplayName("Nhân khẩu không tồn tại và nhân khẩu bị ẩn cho ra CÙNG một phản hồi")
    void khongTonTaiVaBiAnChoCungMotPhanHoi() {
        fx.dangXuat();

        Throwable khongCoThat = org.assertj.core.api.Assertions
                .catchThrowable(() -> fx.query.byId(UUID.randomUUID()));
        Throwable biAn = org.assertj.core.api.Assertions
                .catchThrowable(() -> fx.query.byId(conSong.rawId()));

        assertThat(khongCoThat).isInstanceOf(NotFoundException.class);
        assertThat(biAn).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Cạnh tới một người còn sống bị ẩn biến mất hoàn toàn khỏi danh sách quan hệ")
    void canhToiNguoiBiAnBienMatHoanToan() {
        fx.dangXuat();

        PersonView view = fx.query.byId(cuTo.rawId());

        assertThat(view.relationships())
                .as("chi can dem so canh la suy ra duoc co mot nguoi con song dang bi an o dau kia")
                .isEmpty();
    }

    @Test
    @DisplayName("Thành viên đã đăng nhập thấy đủ cạnh vì cả hai đầu đều hiển thị được")
    void thanhVienThayDuCanh() {
        fx.dangNhapThanhVien(UUID.randomUUID(), GenealogyServiceFixture.P_CHI_GIAP);

        PersonView view = fx.query.byId(cuTo.rawId());

        assertThat(view.relationships()).hasSize(1);
        assertThat(view.relationships().get(0).toPersonId()).isEqualTo(conSong.rawId());
    }

    @Test
    @DisplayName("Bản ghi đã xoá mềm: 404 với thành viên, hiển thị với Hội đồng")
    void banGhiDaXoaMemChiHienVoiHoiDong() {
        fx.dangNhapAdmin();
        fx.softDelete.softDelete(conSong.rawId(), "trùng");
        fx.dangXuat();

        fx.dangNhapThanhVien(UUID.randomUUID(), GenealogyServiceFixture.P_CHI_GIAP);
        assertThatThrownBy(() -> fx.query.byId(conSong.rawId())).isInstanceOf(NotFoundException.class);

        fx.dangXuat();
        fx.dangNhapHoiDong();
        assertThat(fx.query.byId(conSong.rawId()).deleted()).isTrue();
    }

    @Test
    @DisplayName("Nạp nhiều hồ sơ một lượt cũng lọc riêng tư, không bỏ sót ai vào danh sách")
    void napNhieuHoSoCungDuocLoc() {
        fx.dangXuat();

        assertThat(fx.query.visibleByIds(java.util.List.of(cuTo.rawId(), conSong.rawId())))
                .extracting(PersonView::id)
                .containsExactly(cuTo.rawId());
        assertThat(fx.query.visibleByIds(java.util.List.of())).isEmpty();
        assertThat(fx.query.visibleByIds(null)).isEmpty();
    }

    @Test
    @DisplayName("Danh sách tổ tiên giữ nguyên thứ tự của phép duyệt đồ thị, gần nhất trước")
    void danhSachToTienGiuThuTuDuyet() {
        fx.dangNhapAdmin();
        Person chau = fx.seed(PersonFixtures.nam("Cháu"), fx.chiGiap);
        fx.seedParent(conSong, chau, false);

        assertThat(fx.query.ancestorsOf(chau.rawId(), 5))
                .extracting(PersonView::id)
                .containsExactly(conSong.rawId(), cuTo.rawId());
    }

    @Test
    @DisplayName("Con cháu bỏ chính gốc ra khỏi kết quả")
    void conChauBoChinhGocRaKhoiKetQua() {
        fx.dangNhapAdmin();

        assertThat(fx.query.descendantsOf(cuTo.rawId(), 5, 100))
                .extracting(PersonView::id)
                .containsExactly(conSong.rawId());
    }

    @Test
    @DisplayName("Đếm số con cho nút mở rộng của canvas")
    void demSoConChoNutMoRong() {
        fx.dangNhapAdmin();

        assertThat(fx.query.childCountOf(cuTo.rawId())).isEqualTo(1);
        assertThat(fx.query.childCountOf(conSong.rawId())).isZero();
    }

    @Test
    @DisplayName("Người đã khuất bị lọc vẫn được đi XUYÊN QUA ở tầng đồ thị")
    void nguoiBiLocVanDuocDiXuyenQua() {
        fx.dangXuat();
        Person chau = fx.seed(PersonFixtures.daKhuat("Cháu Đã Khuất", Gender.FEMALE), fx.chiGiap);
        fx.seedParent(conSong, chau, false);

        assertThat(fx.query.ancestorsOf(chau.rawId(), 5))
                .as("bo han nguoi bi loc khoi phep duyet se lam dut duong len to tien va khien cac "
                        + "doi tren bien mat theo")
                .extracting(PersonView::id)
                .containsExactly(cuTo.rawId());
    }
}
