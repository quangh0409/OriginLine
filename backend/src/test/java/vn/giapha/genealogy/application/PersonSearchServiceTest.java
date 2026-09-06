package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.application.command.PersonSearchQuery;
import vn.giapha.genealogy.application.view.PageView;
import vn.giapha.genealogy.application.view.PersonSummaryView;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonFixtures;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.vo.Gender;

/**
 * Tìm kiếm nhân khẩu theo tên (FR-4.4) — Postgres FTS cộng {@code unaccent}, <b>không</b>
 * Elasticsearch và <b>không</b> Redis.
 *
 * <p>Thứ tự bắt buộc là <b>lấy rộng → lọc riêng tư → mới cắt trang</b>. Cắt trang trước khi lọc thì
 * một trang 20 dòng có thể chỉ còn 3 dòng và người dùng thấy các trang dài ngắn thất thường. Hệ quả
 * phải chấp nhận: {@code totalElements} là con số <b>đã lọc</b>, không phải tổng tuyệt đối — trả
 * tổng tuyệt đối thì chỉ cần so hai con số là biết dòng họ đang giấu bao nhiêu người còn sống.
 */
class PersonSearchServiceTest {

    private GenealogyServiceFixture fx;
    private Person cuTo;
    private Person conSong;

    @BeforeEach
    void dungHo() {
        fx = new GenealogyServiceFixture();
        cuTo = fx.seed(PersonFixtures.daKhuat("Nguyễn Văn An", Gender.MALE), fx.chiGiap);
        conSong = fx.seed(PersonFixtures.nam("Nguyễn Văn Bính"), fx.chiGiap);
    }

    @AfterEach
    void dongPhien() {
        fx.dangXuat();
    }

    private static PersonSearchQuery truyVan(String q) {
        return new PersonSearchQuery(q, null, null, null, null, false, 0, 20, null);
    }

    @Test
    @DisplayName("Khách chỉ nhận được người đã khuất; người còn sống biến mất khỏi cả tổng số")
    void khachChiNhanNguoiDaKhuat() {
        fx.dangXuat();
        fx.searchPort.tra(cuTo.rawId(), NameType.THUONG_GOI, 0.9)
                .tra(conSong.rawId(), NameType.THUONG_GOI, 0.85);

        PageView<PersonSummaryView> trang = fx.search.search(truyVan("Nguyễn Văn"));

        assertThat(trang.items()).extracting(PersonSummaryView::id).containsExactly(cuTo.rawId());
        assertThat(trang.page().totalElements())
                .as("tra tong tuyet doi thi chi can so hai con so la biet dong ho dang giau bao nhieu nguoi")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Thành viên nhận cả hai; lớp tên đã khớp được gắn vào kết quả")
    void thanhVienNhanCaHaiVaCoLopTenDaKhop() {
        fx.dangNhapThanhVien(UUID.randomUUID(), GenealogyServiceFixture.P_CHI_GIAP);
        fx.searchPort.tra(cuTo.rawId(), NameType.HUY, 0.9)
                .tra(conSong.rawId(), NameType.THUONG_GOI, 0.85);

        PageView<PersonSummaryView> trang = fx.search.search(truyVan("Nguyễn Văn"));

        assertThat(trang.items()).hasSize(2);
        assertThat(trang.items()).extracting(PersonSummaryView::matchedNameType)
                .containsExactly(NameType.HUY, NameType.THUONG_GOI);
    }

    @Test
    @DisplayName("Tìm trong bản ghi đã xoá mềm chỉ dành cho Hội đồng và Quản trị hệ thống")
    void timTrongBanGhiDaXoaChiDanhChoClanWide() {
        fx.dangNhapThanhVien(UUID.randomUUID(), GenealogyServiceFixture.P_CHI_GIAP);
        PersonSearchQuery kemDaXoa = new PersonSearchQuery("Nguyễn", null, null, null, null,
                true, 0, 20, null);

        assertThatThrownBy(() -> fx.search.search(kemDaXoa))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.FORBIDDEN);
    }

    @Test
    @DisplayName("Bộ lọc chi được dịch sang tiền tố ltree trước khi xuống CSDL")
    void boLocChiDichSangTienToLtree() {
        fx.dangNhapAdmin();
        fx.searchPort.tra(cuTo.rawId(), NameType.THUONG_GOI, 0.9);
        PersonSearchQuery theoChi = new PersonSearchQuery("Nguyễn", 3, fx.chiGiap.id(), "Bắc Ninh",
                Boolean.TRUE, false, 0, 20, null);

        fx.search.search(theoChi);

        assertThat(fx.searchPort.filterDaNhan.branchPathPrefix()).isEqualTo("goc.chi_giap");
        assertThat(fx.searchPort.filterDaNhan.generation()).isEqualTo(3);
        assertThat(fx.searchPort.filterDaNhan.nativePlace()).isEqualTo("Bắc Ninh");
        assertThat(fx.searchPort.filterDaNhan.alive()).isTrue();
        assertThat(fx.searchPort.filterDaNhan.includeDeleted()).isFalse();
    }

    @Test
    @DisplayName("Truy vấn xuống CSDL lấy RỘNG hơn trang cần, để bù phần bị bộ lọc bỏ đi")
    void truyVanLayRongHonTrangCan() {
        fx.dangNhapAdmin();
        fx.searchPort.tra(cuTo.rawId(), NameType.THUONG_GOI, 0.9);

        fx.search.search(new PersonSearchQuery("Nguyễn", null, null, null, null, false, 0, 20, null));

        assertThat(fx.searchPort.limitDaNhan).isGreaterThan(20);
    }

    @Test
    @DisplayName("Không có kết quả thô thì trả trang rỗng, không đi hỏi kho nhân khẩu")
    void khongCoKetQuaThoThiTraTrangRong() {
        fx.dangNhapAdmin();

        PageView<PersonSummaryView> trang = fx.search.search(truyVan("Không ai tên vậy"));

        assertThat(trang.items()).isEmpty();
        assertThat(trang.page().totalElements()).isZero();
        assertThat(trang.page().totalPages()).isZero();
        assertThat(trang.page().hasNext()).isFalse();
    }

    @Test
    @DisplayName("Sắp xếp chạy SAU bộ lọc, nếu không thứ tự trang sẽ nhảy loạn giữa các vai")
    void sapXepChaySauBoLoc() {
        fx.dangNhapAdmin();
        Person canh = fx.seed(PersonFixtures.daKhuat("Nguyễn Văn Canh", Gender.MALE), fx.chiGiap);
        fx.searchPort.tra(conSong.rawId(), NameType.THUONG_GOI, 0.9)
                .tra(cuTo.rawId(), NameType.THUONG_GOI, 0.85)
                .tra(canh.rawId(), NameType.THUONG_GOI, 0.8);

        PageView<PersonSummaryView> trang = fx.search.search(
                new PersonSearchQuery("Nguyễn", null, null, null, null, false, 0, 20, "name,asc"));

        assertThat(trang.items()).extracting(PersonSummaryView::displayName)
                .as("sap xep theo thu tu tu nhien cua chuoi; ten thu nghiem co dat de ky tu khac "
                        + "nhau dau tien deu la ASCII, tranh bay sap xep chuoi Unicode co dau")
                .containsExactly("Nguyễn Văn An", "Nguyễn Văn Bính", "Nguyễn Văn Canh");
    }

    @Test
    @DisplayName("Sắp xếp giảm dần theo tên")
    void sapXepGiamDanTheoTen() {
        fx.dangNhapAdmin();
        fx.searchPort.tra(cuTo.rawId(), NameType.THUONG_GOI, 0.9)
                .tra(conSong.rawId(), NameType.THUONG_GOI, 0.85);

        PageView<PersonSummaryView> trang = fx.search.search(
                new PersonSearchQuery("Nguyễn", null, null, null, null, false, 0, 20, "name,desc"));

        assertThat(trang.items()).extracting(PersonSummaryView::displayName)
                .containsExactly("Nguyễn Văn Bính", "Nguyễn Văn An");
    }

    @Test
    @DisplayName("Cắt trang chạy sau cùng, trên danh sách đã lọc và đã sắp xếp")
    void catTrangChaySauCung() {
        fx.dangNhapAdmin();
        Person canh = fx.seed(PersonFixtures.daKhuat("Nguyễn Văn Canh", Gender.MALE), fx.chiGiap);
        fx.searchPort.tra(cuTo.rawId(), NameType.THUONG_GOI, 0.9)
                .tra(conSong.rawId(), NameType.THUONG_GOI, 0.85)
                .tra(canh.rawId(), NameType.THUONG_GOI, 0.8);

        PageView<PersonSummaryView> trang1 = fx.search.search(
                new PersonSearchQuery("Nguyễn", null, null, null, null, false, 0, 2, "name,asc"));
        PageView<PersonSummaryView> trang2 = fx.search.search(
                new PersonSearchQuery("Nguyễn", null, null, null, null, false, 1, 2, "name,asc"));

        assertThat(trang1.items()).hasSize(2);
        assertThat(trang1.page().hasNext()).isTrue();
        assertThat(trang2.items()).extracting(PersonSummaryView::displayName)
                .containsExactly("Nguyễn Văn Canh");
        assertThat(trang2.page().hasNext()).isFalse();
        assertThat(trang2.page().totalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("Trang vượt quá số kết quả trả danh sách rỗng chứ không ném lỗi")
    void trangVuotQuaSoKetQuaTraRong() {
        fx.dangNhapAdmin();
        fx.searchPort.tra(cuTo.rawId(), NameType.THUONG_GOI, 0.9);

        PageView<PersonSummaryView> trang = fx.search.search(
                new PersonSearchQuery("Nguyễn", null, null, null, null, false, 5, 20, null));

        assertThat(trang.items()).isEmpty();
    }

    @Test
    @DisplayName("Kích thước trang bị chặn trên ở 100 và dưới ở 1")
    void kichThuocTrangBiChanTren() {
        assertThat(new PersonSearchQuery("x", null, null, null, null, false, 0, 5000, null).size())
                .isEqualTo(100);
        assertThat(new PersonSearchQuery("x", null, null, null, null, false, -3, 0, null).size())
                .isEqualTo(1);
        assertThat(new PersonSearchQuery("x", null, null, null, null, false, -3, 10, null).page())
                .isZero();
    }
}
