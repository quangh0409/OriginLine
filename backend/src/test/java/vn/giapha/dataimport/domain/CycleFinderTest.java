package vn.giapha.dataimport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Dò vòng lặp tổ tiên — duyệt sâu ba màu bằng ngăn xếp tường minh. */
class CycleFinderTest {

    @Test
    @DisplayName("Cây bình thường: không có vòng lặp nào")
    void cayBinhThuong() {
        CycleFinder f = new CycleFinder();
        f.themCanh("AT-02-001", "AT-01-001");
        f.themCanh("AT-03-001", "AT-02-001");
        f.themCanh("AT-03-002", "AT-02-001");
        assertThat(f.timVongLap()).isEmpty();
    }

    @Test
    @DisplayName("Hai cha mẹ cùng một ông tổ KHÔNG phải vòng lặp")
    void haiDuongToiCungMotToTienKhongPhaiVongLap() {
        // Anh em ho lay nhau la chuyen co that trong dong ho Viet. Do thi co hai duong di toi cung
        // mot dinh nhung khong he co vong lap — bao nham o day la buoc nguoi nhap sua mot du lieu
        // hoan toan dung.
        CycleFinder f = new CycleFinder();
        f.themCanh("CHAU", "CHA");
        f.themCanh("CHAU", "ME");
        f.themCanh("CHA", "CU");
        f.themCanh("ME", "CU");
        assertThat(f.timVongLap()).isEmpty();
    }

    @Test
    @DisplayName("Vòng lặp trong tệp bị bắt, kèm đường đi đầy đủ để mở sổ ra tra")
    void vongLapTrongTep() {
        CycleFinder f = new CycleFinder();
        f.themCanh("AT-05-012", "AT-04-003");
        f.themCanh("AT-04-003", "AT-05-012");

        List<List<String>> vong = f.timVongLap();
        assertThat(vong).hasSize(1);
        assertThat(vong.get(0)).containsExactly("AT-04-003", "AT-05-012", "AT-04-003");
    }

    @Test
    @DisplayName("Vòng lặp dài ba đời")
    void vongLapDai() {
        CycleFinder f = new CycleFinder();
        f.themCanh("A", "B");
        f.themCanh("B", "C");
        f.themCanh("C", "A");
        List<List<String>> vong = f.timVongLap();
        assertThat(vong).hasSize(1);
        assertThat(vong.get(0)).hasSize(4).startsWith("A").endsWith("A");
    }

    @Test
    @DisplayName("Vòng lặp XUYÊN BIÊN: nửa trong tệp, nửa trong phả đã có")
    void vongLapXuyenBien() {
        // Dong AT-09-001 khai cha la mot nguoi DA CO (person:P1). Chuoi to tien cua P1, gieo tu do
        // thi that, lai di nguoc ve chinh AT-09-001. Khong phep kiem nao chi nhin tep bat duoc.
        CycleFinder f = new CycleFinder();
        f.themCanh("AT-09-001", "person:P1");
        f.themCanh("person:P1", "person:P2");
        f.themCanh("person:P2", "AT-09-001");

        List<List<String>> vong = f.timVongLap();
        assertThat(vong).hasSize(1);
        assertThat(vong.get(0)).contains("AT-09-001", "person:P1", "person:P2");
    }

    @Test
    @DisplayName("Cùng một vòng chỉ được báo MỘT lần, dù chạm tới từ nhiều điểm xuất phát")
    void khongBaoTrung() {
        CycleFinder f = new CycleFinder();
        f.themCanh("A", "B");
        f.themCanh("B", "A");
        f.themCanh("X", "A");
        f.themCanh("Y", "B");
        assertThat(f.timVongLap()).hasSize(1);
    }

    @Test
    @DisplayName("Tất định: hai lần chạy trên cùng dữ liệu cho kết quả giống hệt nhau")
    void tatDinh() {
        // Nghiem thu doi hai lan chay bo kiem cho danh sach loi giong het nhau. Neu ket qua phu
        // thuoc thu tu duyet cua mot HashMap thi bai kiem ay luc xanh luc do.
        List<List<String>> lan1 = dungVaChay();
        List<List<String>> lan2 = dungVaChay();
        assertThat(lan1).isEqualTo(lan2);
    }

    private static List<List<String>> dungVaChay() {
        CycleFinder f = new CycleFinder();
        f.themCanh("D", "C");
        f.themCanh("C", "D");
        f.themCanh("B", "A");
        f.themCanh("A", "B");
        f.themCanh("E", "A");
        return f.timVongLap();
    }

    @Test
    @DisplayName("Chuỗi trực hệ rất dài không làm tràn ngăn xếp")
    void chuoiTrucHeRatDai() {
        // Ly do dung ArrayDeque thay vi de quy: cung doan ma nay se chay cho GEDCOM o dot sau, noi
        // 50.000 dong la binh thuong.
        CycleFinder f = new CycleFinder();
        for (int i = 0; i < 50_000; i++) {
            f.themCanh("P" + i, "P" + (i + 1));
        }
        assertThat(f.timVongLap()).isEmpty();
    }
}
