package vn.giapha.dataimport.infrastructure.excel.export;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Một dòng lỗi/cảnh báo <b>ở dạng bộ xuất Excel cần</b>.
 *
 * <h2>Vì sao không nhận thẳng {@code ImportIssueDto} hay {@code ImportIssue}</h2>
 * Hai lý do, cả hai đều là kiến trúc chứ không phải khẩu vị:
 * <ul>
 *   <li>{@code ImportIssueDto} sống ở {@code api.rest.dto}. Bộ xuất nằm ở {@code infrastructure};
 *       để nó nhìn ngược lên {@code api} là dựng một vòng phụ thuộc giữa hai gói, và chiều đúng là
 *       {@code api → application → domain}.</li>
 *   <li>{@code ImportIssue} là thứ <b>bộ luật sinh ra</b> và mang {@code context} <b>chưa qua chốt
 *       riêng tư</b>. Nhận nó ở đây nghĩa là bộ xuất có thể được gọi bằng dữ liệu thô — đúng cái
 *       cần làm cho không viết được. Kiểu riêng này bắt bên gọi phải đi qua đường đọc đã cắt
 *       ({@code ImportReadModel.issueDtos}) trước khi dựng được một dòng.</li>
 * </ul>
 *
 * @param severity   {@code BLOCKING} hoặc {@code WARNING} — quyết định dòng này nằm ở trang nào
 * @param code       mã lỗi, ví dụ {@code IMP_PARENT_NOT_FOUND}. Vừa là khoá tra
 *                   {@link ContextChoPhep}, vừa là thứ người dùng đọc cho bộ phận hỗ trợ nghe qua
 *                   điện thoại khi bí
 * @param sheet      {@code NHAN_KHAU} · {@code HON_PHOI} · {@code LO}
 * @param rowNo      số dòng đúng như Excel đánh; {@code null} với {@code sheet = LO}
 * @param field      tên cột tiếng Việt
 * @param externalCode cột {@code Mã} của dòng — người nhập tra ngược vào sổ giấy bằng mã, không
 *                   bằng số dòng
 * @param message    câu tiếng Việt hoàn chỉnh do bộ kiểm soạn. <b>Không soạn lại ở đây</b>: soạn
 *                   lại là dựng bản văn bản thứ hai, và hai bản thì sẽ lệch nhau
 * @param context    {@code import_issue.context} đã qua chốt của tầng api. Vẫn <b>chưa</b> được coi
 *                   là sạch — xem {@link ContextChoPhep}
 */
public record LoiXuatExcel(String severity,
                           String code,
                           String sheet,
                           Integer rowNo,
                           String field,
                           String externalCode,
                           String message,
                           Map<String, Object> context) {

    public static final String BLOCKING = "BLOCKING";

    public LoiXuatExcel {
        context = context == null ? Map.of() : khongNull(context);
    }

    /**
     * Bản sao bất biến, đã bỏ giá trị {@code null}.
     *
     * <p><b>Không</b> gọi thẳng {@link Map#copyOf}: nó ném {@link NullPointerException} khi map có
     * giá trị {@code null}, và {@code context} rất hay mang một trường chưa có giá trị (năm sinh
     * chưa biết, người bị nghi chưa được ghi). Cái bẫy ấy đã một lần làm đổ cả luồng yêu cầu đính
     * chính ở chỗ khác trong dự án này. Ở đây nó còn tệ hơn một bậc: nó sẽ làm <b>hỏng cả bản
     * xuất</b> — tức làm người nhập mất đường thoát — vì đúng một ô trống trong một dòng lỗi.</p>
     */
    private static Map<String, Object> khongNull(Map<String, Object> src) {
        Map<String, Object> copy = new LinkedHashMap<>();
        src.forEach((k, v) -> {
            if (v != null) {
                copy.put(k, v);
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    /** Lỗi chặn thì không cho bấm duyệt; cảnh báo thì cho. Hai trang khác nhau vì hai hệ quả. */
    public boolean chan() {
        return BLOCKING.equals(severity);
    }
}
