package vn.giapha.dataimport.infrastructure.excel.export;

import java.time.LocalDate;
import java.util.List;

/**
 * Đầu vào trọn vẹn của một bản xuất: phần đầu đề của lô, cộng danh sách lỗi.
 *
 * <p>Số lỗi chặn và số cảnh báo <b>đếm từ chính {@link #loi()}</b> chứ không nhận từ
 * {@code import_batch.blocking_count}. Hai con số ấy thường bằng nhau, nhưng khi lệch thì bản đếm
 * đúng là bản đếm những dòng <b>thật sự có mặt trong tệp này</b> — nói "4 lỗi phải sửa" rồi liệt kê
 * 5 dòng là cách nhanh nhất để người nhập thôi tin cả bảng.</p>
 *
 * @param tenChi         tên chi/ngành, để người nhận biết tệp này của ai khi nó nằm trong thư mục
 *                       Tải về cạnh ba bản khác
 * @param tenTepGoc      tên tệp Excel người nhập đã tải lên; {@code null} nếu lô không giữ được
 * @param ngayXuat       ngày sinh bản xuất — Trưởng chi sẽ có nhiều bản sau nhiều lần sửa, và
 *                       "(1)", "(2)" của trình duyệt không nói được bản nào mới hơn
 */
public record LoBaoLoi(String maLo,
                       String tenChi,
                       String tenTepGoc,
                       LocalDate ngayXuat,
                       List<LoiXuatExcel> loi) {

    public LoBaoLoi {
        loi = loi == null ? List.of() : List.copyOf(loi);
        ngayXuat = ngayXuat == null ? LocalDate.now() : ngayXuat;
    }

    public List<LoiXuatExcel> loiChan() {
        return loi.stream().filter(LoiXuatExcel::chan).toList();
    }

    public List<LoiXuatExcel> canhBao() {
        return loi.stream().filter(l -> !l.chan()).toList();
    }
}
