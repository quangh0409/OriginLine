package vn.giapha.dataimport.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.giapha.dataimport.domain.CommitFixtures.honPhoi;
import static vn.giapha.dataimport.domain.CommitFixtures.row;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Thứ tự ghi: cha mẹ trước, con sau — và còn vòng lặp thì chết rõ ràng chứ không treo. */
class CommitOrderTest {

    @Test
    @DisplayName("Ba đời xếp ngược trong tệp vẫn ra thứ tự ghi đúng: ông, cha, cháu")
    void baDoiXepNguoc() {
        List<PersonRow> rows = List.of(
                row(1, "AT-03-001").cha("AT-02-001").build(),
                row(2, "AT-02-001").cha("AT-01-001").build(),
                row(3, "AT-01-001").build());

        List<String> thuTu = CommitOrder.sap(rows, List.of()).thuTu().stream()
                .map(PersonRow::externalCode).toList();

        assertThat(thuTu).containsExactly("AT-01-001", "AT-02-001", "AT-03-001");
    }

    @Test
    @DisplayName("Mã cha trỏ ra ngoài lô KHÔNG tạo ràng buộc thứ tự: người ấy đã có sẵn trong phả")
    void maChaNgoaiLoKhongRangBuoc() {
        List<PersonRow> rows = List.of(
                row(1, "AT-05-001").cha("AT-04-999").build(),
                row(2, "AT-06-001").cha("AT-05-001").build());

        CommitOrder.KetQua ketQua = CommitOrder.sap(rows, List.of());

        assertThat(ketQua.coVongLap()).isFalse();
        assertThat(ketQua.thuTu()).hasSize(2);
        assertThat(ketQua.thuTu().get(0).externalCode()).isEqualTo("AT-05-001");
    }

    @Test
    @DisplayName("Dâu ghi SAU chồng, để đời thứ của bà suy được từ cạnh hôn phối")
    void dauGhiSauChong() {
        List<PersonRow> rows = List.of(
                row(1, "AT-01-002").ten("Nguyễn Thị Lựu").build(),
                row(2, "AT-01-001").ten("Nguyễn Văn Cẩn").doi(1).build());
        List<MarriageRow> honPhoi = List.of(honPhoi(1, "AT-01-001", "AT-01-002", 1));

        List<String> thuTu = CommitOrder.sap(rows, honPhoi).thuTu().stream()
                .map(PersonRow::externalCode).toList();

        assertThat(thuTu).containsExactly("AT-01-001", "AT-01-002");
    }

    @Test
    @DisplayName("Rể ghi SAU vợ khi chính người vợ mới là con trong họ")
    void reGhiSauVo() {
        List<PersonRow> rows = List.of(
                row(1, "AT-04-009").ten("Trần Văn Rể").build(),
                row(2, "AT-04-003").ten("Nguyễn Thị Gái").cha("AT-03-001").build(),
                row(3, "AT-03-001").doi(3).build());
        List<MarriageRow> honPhoi = List.of(honPhoi(1, "AT-04-009", "AT-04-003", 1));

        List<String> thuTu = CommitOrder.sap(rows, honPhoi).thuTu().stream()
                .map(PersonRow::externalCode).toList();

        assertThat(thuTu.indexOf("AT-04-009")).isGreaterThan(thuTu.indexOf("AT-04-003"));
    }

    @Test
    @DisplayName("Còn vòng lặp thì trả về đúng các mã kẹt vòng, KHÔNG treo và KHÔNG bỏ qua")
    void conVongLapThiBaoRa() {
        List<PersonRow> rows = List.of(
                row(1, "AT-05-012").cha("AT-04-003").build(),
                row(2, "AT-04-003").cha("AT-05-012").build(),
                row(3, "AT-01-001").build());

        CommitOrder.KetQua ketQua = CommitOrder.sap(rows, List.of());

        assertThat(ketQua.coVongLap()).isTrue();
        assertThat(ketQua.dongKetVong()).containsExactly("AT-04-003", "AT-05-012");
        // Phan khong nam tren vong van xep duoc — de thong diep noi duoc "ket o dung hai dong nay".
        assertThat(ketQua.thuTu()).extracting(PersonRow::externalCode)
                .containsExactly("AT-01-001");
    }

    @Test
    @DisplayName("Tất định: hai lần sắp trên cùng dữ liệu cho cùng một thứ tự, từng phần tử")
    void tatDinh() {
        List<PersonRow> rows = List.of(
                row(1, "AT-02-002").cha("AT-01-001").build(),
                row(2, "AT-02-001").cha("AT-01-001").build(),
                row(3, "AT-02-003").cha("AT-01-001").build(),
                row(4, "AT-01-001").build());

        List<String> lan1 = CommitOrder.sap(rows, List.of()).thuTu().stream()
                .map(PersonRow::externalCode).toList();
        List<String> lan2 = CommitOrder.sap(rows, List.of()).thuTu().stream()
                .map(PersonRow::externalCode).toList();

        assertThat(lan2).isEqualTo(lan1);
        assertThat(lan1).containsExactly("AT-01-001", "AT-02-001", "AT-02-002", "AT-02-003");
    }

    @Test
    @DisplayName("Một dòng khai chính nó là cha mình không làm phép sắp đứng lại")
    void tuLamChaMinh() {
        List<PersonRow> rows = List.of(row(1, "AT-02-001").cha("AT-02-001").build());

        CommitOrder.KetQua ketQua = CommitOrder.sap(rows, List.of());

        assertThat(ketQua.coVongLap()).isFalse();
        assertThat(ketQua.thuTu()).hasSize(1);
    }
}
