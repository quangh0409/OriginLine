package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.application.FakeDuplicateCandidatePort.NguoiGia;
import vn.giapha.genealogy.domain.DatePrecision;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.LunarDate;

/**
 * Hành vi của bộ dò trùng ở mức use case: chặn / cho qua, gọi theo lô, và tự soi trong lô.
 */
class DuplicatePersonCheckerTest {

    private static final UUID CHI_GIAP = UUID.randomUUID();

    private final FakeDuplicateCandidatePort port = new FakeDuplicateCandidatePort();
    private final DuplicatePersonChecker checker = new DuplicatePersonChecker(port);

    @Test
    @DisplayName("Nghi trùng mà chưa xác nhận thì ném 409 DUPLICATE_PERSON_SUSPECTED")
    void nemKhiChuaXacNhan() {
        UUID cuId = port.them(NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).namSinh(1920));

        assertThatThrownBy(() -> checker.check(probeTuan(1920), false))
                .isInstanceOf(DuplicatePersonSuspectedException.class)
                .hasMessageContaining("Nghi trung")
                .extracting(ex -> ((DuplicatePersonSuspectedException) ex).getCode())
                .isEqualTo(GenealogyProblemCodes.DUPLICATE_PERSON_SUSPECTED);

        assertThat(checker.check(probeTuan(1920)))
                .singleElement()
                .extracting(DuplicateMatch::personId)
                .isEqualTo(cuId);
    }

    @Test
    @DisplayName("Có cờ xác nhận thì cho qua và trả về ghi chú để lưu vào audit_log")
    void choQuaKhiDaXacNhan() {
        UUID idTuan = port.them(NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).namSinh(1920));

        String note = checker.check(probeTuan(1920), true);

        assertThat(note)
                .as("ghi de phai de lai dau vet trong nhat ky")
                .startsWith("Ghi de canh bao nghi trung")
                .contains(idTuan.toString());
        assertThat(note)
                .as("`audit_log` la bang ma bo loc phan tang KHONG canh, nen ghi chu chi duoc mang"
                        + " KHOA — ung vien nghi trung co the la nguoi con song o mot chi khac")
                .doesNotContain("Nguyễn Văn Tuấn");
    }

    @Test
    @DisplayName("Không nghi ngờ thì không ghi chú gì và không ném gì")
    void khongNghiNgoThiIm() {
        port.them(NguoiGia.ten("Nguyễn Văn Tuấn").doi(3).chi(CHI_GIAP).namSinh(1860));

        assertThatCode(() -> checker.check(probeTuan(1920), false)).doesNotThrowAnyException();
        assertThat(checker.check(probeTuan(1920), false)).isNull();
    }

    @Test
    @DisplayName("Cả lô chỉ tốn đúng một vòng gọi cửa lọc và một vòng bỏ dấu")
    void caLoChiMotVongGoi() {
        for (int i = 0; i < 20; i++) {
            port.them(NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).namSinh(1900 + i));
        }
        List<DuplicateProbe> lo = List.of(probeLo("r1", "Nguyễn Văn Tuấn", 1905),
                probeLo("r2", "Nguyễn Văn Bảy", 1907),
                probeLo("r3", "Nguyen Van Tuan", 1911));

        List<DuplicateReport> reports = checker.check(lo);

        assertThat(port.soLanTraUngVien).isEqualTo(1);
        assertThat(port.soLanBoDau).isEqualTo(1);
        assertThat(reports).hasSize(3);
        assertThat(reports).extracting(DuplicateReport::ref).containsExactly("r1", "r2", "r3");
        assertThat(reports.get(1).coNghiNgo()).isFalse();
    }

    @Test
    @DisplayName("Lô tự soi chính mình: hai dòng trùng nhau trong cùng tệp nhập vẫn bị bắt")
    void loTuSoiChinhMinh() {
        List<DuplicateReport> reports = checker.check(List.of(
                probeLo("dong-1", "Nguyễn Văn Tuấn", 1920),
                probeLo("dong-2", "Nguyen Van Tuan", 1920)));

        assertThat(reports.get(0).coNghiNgo()).isFalse();
        assertThat(reports.get(1).matches()).singleElement().satisfies(match -> {
            assertThat(match.trongCungLo()).isTrue();
            assertThat(match.personId()).isNull();
            assertThat(match.ref()).isEqualTo("dong-1");
        });
    }

    @Test
    @DisplayName("Đang sửa hồ sơ thì không tự báo trùng với chính mình")
    void khongTuBaoTrungVoiChinhMinh() {
        UUID chinhMinh = port.them(NguoiGia.ten("Nguyễn Văn Tuấn").doi(5).chi(CHI_GIAP).namSinh(1920));

        DuplicateProbe probe = new DuplicateProbe(null,
                List.of(PersonName.of(NameType.THUONG_GOI, "Nguyễn Văn Tuấn", true)),
                Gender.MALE, 5, CHI_GIAP, birth(1920), null, null, chinhMinh);

        assertThat(checker.check(probe)).isEmpty();
    }

    @Test
    @DisplayName("Ngày giỗ trùng khít bắt được bản trùng mà tiêu chí năm sinh của hợp đồng bỏ sót")
    void gioBatDuocCaKhiKhongCoNamSinh() {
        port.them(NguoiGia.ten("Nguyễn Văn Tuấn").doi(4).chi(CHI_GIAP).namMat(1975).gio(5, 10));

        DuplicateProbe probe = new DuplicateProbe(null,
                List.of(PersonName.of(NameType.THUONG_GOI, "Nguyễn Văn Tuấn", true)),
                Gender.MALE, 4, CHI_GIAP, null,
                LifeDate.of(null, new LunarDate(1976, 5, 10, false), DatePrecision.DAY), null, null);

        assertThat(checker.check(probe)).singleElement().satisfies(match ->
                assertThat(match.signals()).contains(DuplicateSignal.GIO_TRUNG_KHIT));
    }

    @Test
    @DisplayName("Lô rỗng không gọi CSDL lần nào")
    void loRongKhongGoiCsdl() {
        assertThat(checker.check(List.of())).isEmpty();
        assertThat(port.soLanBoDau).isZero();
        assertThat(port.soLanTraUngVien).isZero();
    }

    // ---------------------------------------------------------------------------------------

    private DuplicateProbe probeTuan(int namSinh) {
        return DuplicateProbe.of(
                List.of(PersonName.of(NameType.THUONG_GOI, "Nguyễn Văn Tuấn", true)),
                Gender.MALE, 5, CHI_GIAP, birth(namSinh), null, null);
    }

    private DuplicateProbe probeLo(String ref, String ten, int namSinh) {
        return new DuplicateProbe(ref, List.of(PersonName.of(NameType.THUONG_GOI, ten, true)),
                Gender.MALE, 5, CHI_GIAP, birth(namSinh), null, null, null);
    }

    private static LifeDate birth(int year) {
        return LifeDate.of(LocalDate.of(year, 3, 20), null, DatePrecision.DAY);
    }
}
