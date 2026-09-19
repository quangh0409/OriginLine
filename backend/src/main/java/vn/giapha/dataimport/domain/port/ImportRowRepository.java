package vn.giapha.dataimport.domain.port;

import java.util.List;
import java.util.UUID;
import vn.giapha.dataimport.domain.MarriageRow;
import vn.giapha.dataimport.domain.PersonRow;

/**
 * Kho các dòng chờ của một lô.
 *
 * <p>Mọi phương thức đều <b>theo lô</b>. Không có lối ghi từng dòng, để không ai vô tình viết vòng
 * lặp 400 lượt ghi.</p>
 */
public interface ImportRowRepository {

    /** Thay toàn bộ dòng Nhân khẩu của lô. Lô là đơn vị nguyên tử, không ghép thêm từng phần. */
    void replacePersonRows(UUID batchId, List<PersonRow> rows);

    void replaceMarriageRows(UUID batchId, List<MarriageRow> rows);

    /** Các dòng Nhân khẩu, đã sắp theo row_no — bộ kiểm cần thứ tự tất định. */
    List<PersonRow> personRows(UUID batchId);

    List<MarriageRow> marriageRows(UUID batchId);

    /**
     * Ghi lại kết quả đối soát: dòng nào sẽ tạo mới, dòng nào cập nhật người đã có.
     *
     * <p>Chạy lại nhiều lần là bình thường — mỗi lần kiểm là một lần đối soát mới.</p>
     */
    void updateResolutions(UUID batchId, List<PersonRow> rows);
}
