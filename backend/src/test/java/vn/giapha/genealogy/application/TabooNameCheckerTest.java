package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.application.GenealogyTestDoubles.FakeTabooNamePort;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.TabooConflict;

/**
 * <b>Cảnh báo kỵ húy (FR-1.6)</b> — cảnh báo, <i>không phải</i> lệnh cấm.
 *
 * <p>Kỵ húy là tục kiêng gọi tên thật của bậc trên; đặt trùng tên húy một cụ bị xem là thất kính.
 * Thẩm quyền quyết định thuộc về dòng họ, nên luồng có đúng hai bước: lần gọi đầu ném ngoại lệ và
 * <b>không ghi gì cả</b>; lần gọi sau kèm cờ xác nhận thì ghi, và lý do ghi đè được lưu vào
 * {@code audit_log}.
 */
class TabooNameCheckerTest {

    private final FakeTabooNamePort port = new FakeTabooNamePort();
    private final TabooNameChecker checker = new TabooNameChecker(port);

    @Test
    @DisplayName("Không va chạm thì không cảnh báo và không có ghi chú nhật ký")
    void khongVaChamThiKhongCanhBao() {
        String note = checker.check(
                List.of(PersonName.of(NameType.HUY, "Nguyễn Văn Tân", true)), 5, null, false);

        assertThat(note).isNull();
    }

    @Test
    @DisplayName("Bước 1 — trùng tên húy bậc trên mà chưa xác nhận thì ném cảnh báo, KHÔNG ghi")
    void buoc1TrungTenHuyThiNemCanhBao() {
        port.vaChamVoi("Nguyễn Văn Tuân", FakeTabooNamePort.cuTo("Nguyễn Văn Tuân", 3));

        TabooNameConflictException ex = catchThrowableOfType(
                () -> checker.check(List.of(PersonName.of(NameType.HUY, "Nguyễn Văn Tuân", true)),
                        7, null, false),
                TabooNameConflictException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(GenealogyProblemCodes.KY_HUY_CONFLICT);
        assertThat(ex.conflicts()).singleElement()
                .satisfies(conflict -> {
                    assertThat(conflict.tabooName()).isEqualTo("Nguyễn Văn Tuân");
                    assertThat(conflict.ancestorGeneration()).isEqualTo(3);
                    assertThat(conflict.matchedNameType()).isEqualTo(NameType.HUY);
                });
        assertThat(ex.getMessage())
                .as("hop thoai phai liet ke du 'cu nao, doi thu may, trung ten huy nao'")
                .contains("Nguyễn Văn Tuân")
                .contains("doi thu 3");
    }

    @Test
    @DisplayName("Bước 2 — có xác nhận thì cho ghi và trả ghi chú để lưu vào audit_log")
    void buoc2CoXacNhanThiChoGhi() {
        TabooConflict conflict = FakeTabooNamePort.cuTo("Nguyễn Văn Tuân", 3);
        port.vaChamVoi("Nguyễn Văn Tuân", conflict);

        String note = checker.check(
                List.of(PersonName.of(NameType.HUY, "Nguyễn Văn Tuân", true)), 7, null, true);

        assertThat(note)
                .as("viec ghi de phai de lai dau vet trong nhat ky")
                .startsWith("Ghi de canh bao ky huy:")
                .contains("Nguyễn Văn Tuân")
                .contains(conflict.ancestorPersonId().toString());
    }

    @Test
    @DisplayName("Chỉ lớp tên HUY bị soi — tự, hiệu, thụy, thường gọi, pháp danh không phạm huý")
    void chiLopHuyBiSoi() {
        checker.check(List.of(
                PersonName.of(NameType.TU, "Tử Kính", false),
                PersonName.of(NameType.HIEU, "Ức Trai", false),
                PersonName.of(NameType.THUY, "Văn Trinh", false),
                PersonName.of(NameType.THUONG_GOI, "Cụ Cả", true),
                PersonName.of(NameType.PHAP_DANH, "Thích Minh Đức", false)), 5, null, false);

        assertThat(port.daHoi)
                .as("quet ca cac lop ten khac chi tao ra mot bien canh bao gia, khien nguoi dung "
                        + "bam 'van ghi' theo phan xa — luc do canh bao mat sach gia tri")
                .isEmpty();
    }

    @Test
    @DisplayName("Tên húy trùng nhau trong cùng một lô chỉ được hỏi CSDL một lần")
    void tenHuyTrungNhauChiHoiMotLan() {
        checker.check(List.of(
                PersonName.of(NameType.HUY, "Nguyễn Văn Tuân", true),
                PersonName.of(NameType.HUY, "Nguyễn Văn Tuân", false)), 5, null, false);

        assertThat(port.daHoi).containsExactly("Nguyễn Văn Tuân");
    }

    @Test
    @DisplayName("Nhiều tên húy khác nhau đều được soi, va chạm được gom lại")
    void nhieuTenHuyDeuDuocSoi() {
        port.vaChamVoi("Nguyễn Văn Tuân", FakeTabooNamePort.cuTo("Nguyễn Văn Tuân", 3));
        port.vaChamVoi("Nguyễn Đình Trọng", FakeTabooNamePort.cuTo("Nguyễn Đình Trọng", 2));

        TabooNameConflictException ex = catchThrowableOfType(
                () -> checker.check(List.of(
                        PersonName.of(NameType.HUY, "Nguyễn Văn Tuân", true),
                        PersonName.of(NameType.HUY, "Nguyễn Đình Trọng", false)), 8, null, false),
                TabooNameConflictException.class);

        assertThat(ex.conflicts()).hasSize(2);
        assertThat(port.daHoi).containsExactly("Nguyễn Văn Tuân", "Nguyễn Đình Trọng");
    }

    @Test
    @DisplayName("Danh sách tên rỗng hoặc null không gây cảnh báo")
    void danhSachRongKhongGayCanhBao() {
        assertThat(checker.check(List.of(), 5, null, false)).isNull();
        assertThat(checker.check(null, 5, null, false)).isNull();
        assertThat(port.daHoi).isEmpty();
    }

    @Test
    @DisplayName("Đời thứ null vẫn quét được — phạm vi khi đó là toàn dòng họ")
    void doiThuNullVanQuetDuoc() {
        port.vaChamVoi("Nguyễn Văn Tuân", FakeTabooNamePort.cuTo("Nguyễn Văn Tuân", 3));

        assertThatThrownBy(() -> checker.check(
                List.of(PersonName.of(NameType.HUY, "Nguyễn Văn Tuân", true)), null, null, false))
                .isInstanceOf(TabooNameConflictException.class);
    }

    @Test
    @DisplayName("Đời thứ và id cần loại trừ được truyền nguyên vẹn xuống cổng tra cứu")
    void doiThuVaIdLoaiTruDuocTruyenXuongCong() {
        UUID toi = UUID.randomUUID();

        checker.check(List.of(PersonName.of(NameType.HUY, "Nguyễn Văn Tuân", true)), 7, toi, false);

        assertThat(port.daHoi).containsExactly("Nguyễn Văn Tuân");
        assertThat(port.doiThuDaNhan).containsExactly(7);
        assertThat(port.excludeIdDaNhan)
                .as("khong truyen excludeId thi nguoi dang sua se tu bao trung voi chinh minh")
                .isEqualTo(toi);
    }

    @Test
    @DisplayName("Ngoại lệ kỵ húy mang mã 409 riêng, tách khỏi các xung đột trạng thái khác")
    void ngoaiLeKyHuyMangMaRieng() {
        TabooNameConflictException ex = new TabooNameConflictException(List.of());

        assertThat(ex.getCode()).isEqualTo(GenealogyProblemCodes.KY_HUY_CONFLICT);
        assertThat(ex.conflicts()).isEmpty();
        assertThat(ex.getMessage()).isNotBlank();
    }
}
