package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.application.FakeDuplicateCandidatePort.NguoiGia;
import vn.giapha.genealogy.application.command.AddPersonCommand;
import vn.giapha.genealogy.domain.DatePrecision;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.shared.vo.Gender;

/**
 * Phép dò trùng trong luồng <b>thêm nhân khẩu</b>.
 *
 * <p>Điểm phải ghim: cảnh báo nghi trùng chạy <b>trước mọi lệnh ghi</b>. Nếu có bản ghi nào được
 * tạo rồi mới ném lỗi thì mỗi lần người dùng bấm "Lưu" và bị hỏi lại sẽ để lại một nhân khẩu rác
 * trong gia phả — và vì genealogy chỉ xoá mềm nên rác đó ở lại vĩnh viễn.</p>
 */
class AddPersonDuplicateTest {

    private GenealogyServiceFixture fx;

    @BeforeEach
    void dungHo() {
        fx = new GenealogyServiceFixture();
        fx.dangNhapAdmin();
    }

    @AfterEach
    void dongPhien() {
        fx.dangXuat();
    }

    @Test
    @DisplayName("Nghi trùng thì ném 409 và KHÔNG tạo bản ghi nào")
    void nghiTrungThiKhongGhiGi() {
        fx.duplicatePort.them(NguoiGia.ten("Nguyễn Văn Tuấn").chi(fx.chiGiap.id()).namSinh(1920));

        assertThatThrownBy(() -> fx.addPerson.add(lenh("Nguyễn Văn Tuấn", 1920, false)))
                .isInstanceOf(DuplicatePersonSuspectedException.class)
                .extracting(ex -> ((DuplicatePersonSuspectedException) ex).getCode())
                .isEqualTo(GenealogyProblemCodes.DUPLICATE_PERSON_SUSPECTED);

        assertThat(fx.graph.nodeDaTao).as("khong duoc tao dinh do thi truoc khi hoi nguoi dung")
                .isEmpty();
        assertThat(fx.audit.hanhDong()).isEmpty();
        assertThat(fx.treeCache.soLanEvictAll).isZero();
    }

    @Test
    @DisplayName("Gửi lại kèm confirmDuplicateOverride thì ghi bình thường và lưu vết vào audit_log")
    void xacNhanThiGhiVaLuuVet() {
        fx.duplicatePort.them(NguoiGia.ten("Nguyễn Văn Tuấn").chi(fx.chiGiap.id()).namSinh(1920));

        var view = fx.addPerson.add(lenh("Nguyễn Văn Tuấn", 1920, true));

        assertThat(view.id()).isNotNull();
        assertThat(fx.graph.nodeDaTao).containsExactly(view.id());
        assertThat(fx.audit.cuoiCung().note()).contains("Ghi de canh bao nghi trung");
    }

    @Test
    @DisplayName("Trùng tên trong họ nhưng cách nhau vài chục năm sinh thì thêm được ngay")
    void trungTenTrongHoThiThemDuocNgay() {
        fx.duplicatePort.them(NguoiGia.ten("Nguyễn Văn Tuấn").doi(3).chi(fx.chiGiap.id())
                .namSinh(1890));

        var view = fx.addPerson.add(lenh("Nguyễn Văn Tuấn", 1950, false));

        assertThat(view.id()).isNotNull();
        assertThat(fx.audit.cuoiCung().note())
                .as("khong co canh bao nao thi khong co ghi chu nao")
                .isNull();
    }

    private AddPersonCommand lenh(String ten, int namSinh, boolean xacNhanTrung) {
        return new AddPersonCommand(
                List.of(PersonName.of(NameType.THUONG_GOI, ten, true)),
                Gender.MALE, false, LifeDate.of(LocalDate.of(namSinh, 3, 20), null, DatePrecision.DAY),
                LifeDate.of(LocalDate.of(namSinh + 70, 6, 15), null, DatePrecision.DAY),
                null, null, null, null, null,
                fx.chiGiap.id(), null, null, null, List.of(), false, xacNhanTrung, null);
    }
}
