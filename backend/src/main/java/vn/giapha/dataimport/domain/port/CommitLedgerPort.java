package vn.giapha.dataimport.domain.port;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.giapha.dataimport.domain.CommitEntry;
import vn.giapha.dataimport.domain.LunarDeathDate;

/**
 * Cổng ghi/đọc <b>sổ cái của một lần ghi</b> ({@code import_commit_entry}).
 *
 * <p>Nó tồn tại để câu hỏi "lô này đã viết ra những gì" có một câu trả lời chính xác thay vì một
 * phép suy đoán theo thời điểm tạo. Suy đoán sai ở đây nghĩa là gỡ nhầm một cạnh của dòng họ.</p>
 */
public interface CommitLedgerPort {

    /** Ghi cả sổ cái một lần, ở cuối transaction ghi lô. */
    void ghi(UUID batchId, List<CommitEntry> entries);

    /** Đọc lại sổ cái, thứ tự tất định: người trước, cạnh sau. */
    List<CommitEntry> doc(UUID batchId);

    /** Xoá sổ cái của một lô — chỉ gọi sau khi đã gỡ xong lô ấy. */
    void xoa(UUID batchId);

    /**
     * {@code person.version} hiện tại của một loạt người.
     *
     * <p>Đọc <b>sau</b> khi mọi lệnh ghi của lô đã chạy: nối một cạnh cha–con còn đặt lại đời thứ
     * cho người con, tức là còn tăng version một nhịp nữa. Chụp version quá sớm thì mọi lần gỡ lô
     * đều bị từ chối với lý do "có người đã sửa" trong khi không ai sửa cả.</p>
     */
    Map<UUID, Long> phienBanCua(List<UUID> personIds);

    /**
     * Vá hai cột mà context {@code genealogy} <b>không biết tới</b>, trong cùng transaction ghi lô.
     *
     * <h2>Vì sao phải có lối này, và vì sao nó KHÔNG phải "đường ghi thứ hai"</h2>
     * Hai cột, hai lý do khác nhau, và cả hai đều là cột vô hướng của bảng {@code person} — không
     * cột nào chạm tới {@code relationship} hay đồ thị AGE, nên bất biến lớn nhất của dự án không
     * bị đụng tới.
     * <ul>
     *   <li>{@code native_place_code} do chính {@code V9__dataimport.sql} thêm vào và là đầu vào
     *       duy nhất gộp nhóm được cho báo cáo dân số (FR-4.3). Aggregate {@code Person} của
     *       {@code genealogy} chưa có trường tương ứng.</li>
     *   <li>{@code death_lunar} <b>khi không rõ năm</b>. Lược đồ cho phép ({@code ck_person_death_lunar}
     *       chỉ đòi {@code day} + {@code month}), nhưng {@code LunarDate} của shared kernel bắt
     *       buộc có năm. Mà "mất ngày 15 tháng 8, không rõ năm" là chuyện rất thường trong sổ cũ và
     *       vẫn đủ để cúng giỗ. Bỏ qua ca này nghĩa là đúng nhóm hồ sơ cổ nhất của dòng họ
     *       <b>không bao giờ được nhắc giỗ</b> — mà nhắc giỗ là lý do dòng họ mở ứng dụng.</li>
     * </ul>
     *
     * <p><b>Phải gọi ở cuối</b>, sau khi mọi lệnh ghi qua {@link PhaWritePort} đã xong: dòng
     * {@code person} lúc ấy đã tồn tại thật trong CSDL và sẽ không bị một lần flush nào của ORM ghi
     * đè lên nữa.</p>
     *
     * @param maNguyenQuan mã tỉnh/quốc gia; {@code null} thì bỏ qua cột ấy
     * @param gioKhongRoNam ngày giỗ thiếu năm; {@code null} thì bỏ qua cột ấy
     */
    void vaCotNgoai(UUID personId, String maNguyenQuan, LunarDeathDate gioKhongRoNam);
}
