package vn.giapha.dataimport.domain.port;

import java.util.List;
import java.util.UUID;

/**
 * Cổng lấy <b>các cạnh con-tới-cha</b> phía trên một mỏ neo — người đã có trong phả mà tệp đang
 * nhập trỏ tới.
 *
 * <h2>Chỉ phục vụ đúng một việc: vòng lặp xuyên biên</h2>
 * Một dòng trong tệp khai cha là một người đã có; người đã có ấy lại là hậu duệ của một người khác
 * <b>trong tệp</b>. Vòng lặp đó nằm nửa trong nửa ngoài, và không phép kiểm nào chỉ nhìn tệp bắt
 * được nó.
 *
 * <h2>Vì sao trả về CẠNH chứ không phải danh sách tổ tiên phẳng</h2>
 * Một người có hai cha mẹ, nên phía trên một mỏ neo là một đồ thị chứ không phải một chuỗi. Trả về
 * danh sách phẳng rồi nối chúng thành chuỗi ở bên gọi sẽ dựng ra những cạnh <b>không tồn tại</b>
 * (cha nối sang mẹ), và bộ dò vòng lặp sẽ báo những vòng không có thật. Cạnh thì không mất thông
 * tin.
 *
 * <p>Thực tế một tệp chỉ có 1–5 mỏ neo (thường là cụ tổ của chi), nên đây là vài lượt duyệt, không
 * phải 400.</p>
 *
 * <p><b>Chỉ đọc.</b> Context này không bao giờ ghi vào đồ thị.</p>
 */
public interface AnchorAncestorPort {

    /**
     * @param maxDepth trần số đời đi ngược. Có trần là bắt buộc: nếu đồ thị đã có sẵn một vòng lặp
     *        từ trước (dữ liệu cũ, nhập tay) thì một phép duyệt không trần sẽ chạy mãi.
     * @return các cạnh con-tới-cha gặp được; rỗng khi người đó không có cha mẹ nào trong phả
     */
    List<Canh> ancestorEdges(UUID personId, int maxDepth);

    /** Một cạnh phả hệ: {@code con} có cha hoặc mẹ là {@code cha}. */
    record Canh(UUID con, UUID cha) {
    }
}
