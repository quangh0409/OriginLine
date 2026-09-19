package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.application.FakeDuplicateCandidatePort.NguoiGia;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.shared.vo.Gender;

/**
 * Ngưỡng và trọng số của bộ dò trùng.
 *
 * <p>Bộ test này cố ý cân <b>cả hai vế</b>. Chứng minh bộ dò biết kêu thì dễ; phần khó và phần
 * quan trọng hơn là chứng minh nó biết <b>im</b> đúng lúc, vì trùng tên trong một dòng họ Việt là
 * chuyện bình thường chứ không phải bất thường, và một bộ dò kêu quá tay sẽ dạy người dùng bấm
 * "vẫn ghi" theo phản xạ — lúc đó cảnh báo mất sạch giá trị.</p>
 */
class DuplicateScorerTest {

    private static final UUID CHI_GIAP = UUID.randomUUID();
    private static final UUID CHI_AT = UUID.randomUUID();

    @Nested
    @DisplayName("Phải kêu")
    class PhaiKeu {

        @Test
        @DisplayName("Tiêu chí của hợp đồng: cùng tên + năm sinh + chi")
        void tieuChiHopDong() {
            var cu = NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).namSinh(1920);
            var moi = NguoiGia.ten("Nguyen Van Tuan").doi(null).chi(CHI_GIAP).namSinh(1920);

            Optional<DuplicateMatch> match = DuplicateScorer.score(moi.chuKy(), cu.chuKy());

            assertThat(match).isPresent();
            assertThat(match.get().score()).isGreaterThanOrEqualTo(DuplicateScorer.NGUONG_NGHI_TRUNG);
            assertThat(match.get().signals())
                    .contains(DuplicateSignal.TEN_TRUNG_KHONG_DAU, DuplicateSignal.NAM_SINH_KHOP,
                            DuplicateSignal.CUNG_CHI);
        }

        @Test
        @DisplayName("Ngày giỗ trùng khít đủ để kêu dù không biết năm sinh")
        void gioTrungKhitDuManh() {
            var cu = NguoiGia.ten("Nguyễn Văn Tuấn").doi(3).namMat(1975).gio(5, 10);
            var moi = NguoiGia.ten("Nguyễn Văn Tuấn").doi(3).namMat(1974).gio(5, 10);

            Optional<DuplicateMatch> match = DuplicateScorer.score(moi.chuKy(), cu.chuKy());

            assertThat(match).isPresent();
            assertThat(match.get().signals()).contains(DuplicateSignal.GIO_TRUNG_KHIT);
            assertThat(match.get().hint()).contains("trùng ngày giỗ");
        }

        @Test
        @DisplayName("Năm sinh lệch một năm vẫn kêu khi có thêm cùng đời và cùng chi")
        void namSinhLechMotNam() {
            var cu = NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).namSinh(1920);
            var moi = NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).namSinh(1921);

            assertThat(DuplicateScorer.score(moi.chuKy(), cu.chuKy())).isPresent();
        }

        @Test
        @DisplayName("Khớp chéo lớp tên: tên húy của hồ sơ mới trùng tên thường gọi của hồ sơ cũ")
        void khopCheoLopTen() {
            var cu = NguoiGia.ten("Nguyễn Văn Tuấn").doi(4).chi(CHI_GIAP).namSinh(1901);
            var moi = new NguoiGia().themTen(NameType.HUY, "Nguyễn Văn Tuấn")
                    .doi(4).chi(CHI_GIAP).namSinh(1901);

            assertThat(DuplicateScorer.score(moi.chuKy(), cu.chuKy())).isPresent();
        }

        @Test
        @DisplayName("Đặt sai đời vẫn kêu khi bằng chứng quá mạnh: tên đủ dấu + giỗ + cùng chi")
        void bangChungDuManhVuotQuaPhanChungKhacDoi() {
            var cu = NguoiGia.ten("Nguyễn Văn Tuấn").doi(3).chi(CHI_GIAP).namMat(1975).gio(5, 10);
            var moi = NguoiGia.ten("Nguyễn Văn Tuấn").doi(4).chi(CHI_GIAP).namMat(1975).gio(5, 10);

            Optional<DuplicateMatch> match = DuplicateScorer.score(moi.chuKy(), cu.chuKy());

            assertThat(match).isPresent();
            assertThat(match.get().signals()).contains(DuplicateSignal.KHAC_DOI);
        }
    }

    @Nested
    @DisplayName("Câu giải thích: nhãn tín hiệu, không một giá trị trường nào")
    class CauGiaiThich {

        /**
         * Câu này chảy vào {@code import_issue.message} và <b>nằm lại trong CSDL</b>. Bộ dò quét
         * toàn dòng họ, nên hồ sơ bị nghi có thể là người còn sống ở một chi khác. Gieo hồ sơ cũ
         * bằng những con số <b>không thể tình cờ xuất hiện</b> rồi khẳng định chúng biến mất sạch.
         */
        @Test
        @DisplayName("Không năm sinh, không ngày giỗ, không đời thứ, không tên của hồ sơ trong phả")
        void khongLoMotGiaTriNao() {
            var cu = NguoiGia.ten("Nguyễn Văn Tuấn").doi(7).chi(CHI_GIAP).namSinh(1937)
                    .namMat(1993).gio(11, 23);
            var moi = NguoiGia.ten("Nguyễn Văn Tuấn").doi(7).chi(CHI_GIAP).namSinh(1937)
                    .namMat(1993).gio(11, 23);

            String hint = DuplicateScorer.score(moi.chuKy(), cu.chuKy()).orElseThrow().hint();

            assertThat(hint)
                    .doesNotContain("1937")   // nam sinh
                    .doesNotContain("1993")   // nam mat
                    .doesNotContain("23")     // ngay gio
                    .doesNotContain("11")     // thang gio
                    .doesNotContain("7")      // doi thu
                    .doesNotContain("Tuấn");  // ten
            // ...nhung van noi duoc VI SAO nghi, neu khong thi canh bao vo dung.
            assertThat(hint).contains("trùng ngày giỗ", "trùng năm sinh", "cùng chi", "cùng đời");
        }

        /**
         * Ca nguy hiểm nhất: năm sinh <b>lệch</b> 1–2 năm. Bản cũ in ra
         * "nam sinh lech it so voi 1920" — con số ấy là năm sinh THẬT của hồ sơ trong phả, không
         * phải thứ người dùng vừa gõ, nên lập luận "in lại thứ họ đã biết" không cứu được nó.
         */
        @Test
        @DisplayName("Năm sinh lệch: không in năm sinh thật của hồ sơ bên kia")
        void namSinhLechKhongInRaNamThat() {
            var cu = NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).namSinh(1918);
            var moi = NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).namSinh(1920);

            String hint = DuplicateScorer.score(moi.chuKy(), cu.chuKy()).orElseThrow().hint();

            assertThat(hint).doesNotContain("1918").doesNotContain("1920");
            assertThat(hint).contains("năm sinh lệch 1–2 năm");
        }

        @Test
        @DisplayName("Bằng chứng mạnh nhất đứng trước, phản chứng đứng sau cùng")
        void sapTheoSucManh() {
            var cu = NguoiGia.ten("Nguyễn Văn Tuấn").doi(3).chi(CHI_GIAP).namMat(1975).gio(5, 10);
            var moi = NguoiGia.ten("Nguyễn Văn Tuấn").doi(4).chi(CHI_GIAP).namMat(1975).gio(5, 10);

            String hint = DuplicateScorer.score(moi.chuKy(), cu.chuKy()).orElseThrow().hint();

            assertThat(hint).startsWith("trùng ngày giỗ");
            assertThat(hint).endsWith("nhưng khác đời thứ");
        }
    }

    @Nested
    @DisplayName("Phải im")
    class PhaiIm {

        @Test
        @DisplayName("Cùng tên, cùng chi, khác đời — chuyện bình thường của dòng họ, không được kêu")
        void trungTenKhacDoiThiIm() {
            var cu = NguoiGia.ten("Nguyễn Văn Tuấn").doi(3).chi(CHI_GIAP).namSinh(1890);
            var chau = NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).namSinh(1950);

            assertThat(DuplicateScorer.score(chau.chuKy(), cu.chuKy())).isEmpty();
        }

        @Test
        @DisplayName("Cùng tên, cùng năm sinh, nhưng khác đời — không thể là một người")
        void cungNamSinhMaKhacDoiThiIm() {
            var cu = NguoiGia.ten("Nguyễn Văn Tuấn").doi(4).chi(CHI_GIAP).namSinh(1920);
            var moi = NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).namSinh(1920);

            assertThat(DuplicateScorer.score(moi.chuKy(), cu.chuKy())).isEmpty();
        }

        @Test
        @DisplayName("Trùng tên tuyệt đối nhưng không có một mẩu bằng chứng ngày tháng nào")
        void khongCoBangChungNgayThangThiIm() {
            var cu = NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).nguyenQuan("Bắc Ninh");
            var moi = NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).nguyenQuan("Bac Ninh");

            assertThat(DuplicateScorer.score(moi.chuKy(), cu.chuKy())).isEmpty();
        }

        @Test
        @DisplayName("Chỉ trùng năm mất thì không tự nó mở cửa")
        void chiTrungNamMatThiIm() {
            var cu = NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).namMat(1975).gio(3, 7);
            var moi = NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).namMat(1975).gio(9, 21);

            assertThat(DuplicateScorer.score(moi.chuKy(), cu.chuKy())).isEmpty();
        }

        @Test
        @DisplayName("Khác giới tính rõ ràng thì trừ điểm đủ để im")
        void khacGioiThiIm() {
            var cu = NguoiGia.ten("Nguyễn Thanh Hà").doi(6).chi(CHI_GIAP).namSinh(1980)
                    .gioiTinh(Gender.FEMALE);
            var moi = NguoiGia.ten("Nguyen Thanh Ha").doi(6).chi(CHI_GIAP).namSinh(1980)
                    .gioiTinh(Gender.MALE);

            assertThat(DuplicateScorer.score(moi.chuKy(), cu.chuKy())).isEmpty();
        }

        @Test
        @DisplayName("Không khớp tên ở bất kỳ lớp nào thì dừng ngay, dù mọi thứ khác trùng khít")
        void khongKhopTenThiDungNgay() {
            var cu = NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).namSinh(1920).gio(5, 10);
            var moi = NguoiGia.ten("Nguyễn Văn Bảy").doi(5).chi(CHI_GIAP).namSinh(1920).gio(5, 10);

            assertThat(DuplicateScorer.score(moi.chuKy(), cu.chuKy())).isEmpty();
        }

        @Test
        @DisplayName("Giỗ khác tháng thì không phải giỗ trùng khít")
        void gioKhacThangThiIm() {
            var cu = NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).namMat(1975).gio(5, 10);
            var moi = NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_AT).namMat(1975).gio(6, 10);

            assertThat(DuplicateScorer.score(moi.chuKy(), cu.chuKy())).isEmpty();
        }

        @Test
        @DisplayName("Cùng ngày cùng tháng nhưng một bên là tháng nhuận — giỗ lệch cả tháng")
        void gioLechCoThangNhuanThiIm() {
            var cu = NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).namMat(1975).gio(5, 10);
            var moi = NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).namMat(1975)
                    .gioNhuan(5, 10);

            assertThat(DuplicateScorer.score(moi.chuKy(), cu.chuKy())).isEmpty();
        }
    }
}
