package vn.giapha.dataimport.api.rest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.PersonRow;

/**
 * Chốt chặn cuối trên {@code import_issue.context} trước khi nó rời máy chủ: <b>một danh sách
 * khoá được phép</b>, không hơn.
 *
 * <h2>Lớp này đã từng làm việc khác, và việc ấy nay là thừa</h2>
 * Nó từng <b>soạn lại cả câu thông báo</b> để cắt tên và năm sinh của người trong phả ra khỏi
 * {@code IMP_SUSPECT_DUPLICATE} và {@code IMP_TABOO_COLLISION}. Đó là chữa triệu chứng: câu thông
 * báo <b>đã bị ghi</b> vào cơ sở dữ liệu với dữ liệu người khác ở trong, nên ai truy vấn thẳng
 * bảng vẫn đọc được, và endpoint chỉ là một trong nhiều lối ra. Chỗ sửa tận gốc là hai luật của bộ
 * kiểm — {@code SuspectDuplicateRule} và {@code TabooCollisionRule} — nay soạn thông báo
 * <b>bằng khoá, không bằng trường</b>. Phần soạn lại câu ở đây đã được gỡ bỏ: giữ nó lại nghĩa là
 * có <b>hai bản</b> văn bản cảnh báo ở hai tầng, và hai bản thì sẽ lệch nhau — đúng cái đã xảy ra.
 *
 * <h2>Vì sao vẫn còn lại phần này, chứ không xoá hẳn lớp</h2>
 * Không phải "cho chắc". Lý do nêu được: <b>bên ghi và bên đọc {@code context} bị ngăn cách bởi
 * một cơ sở dữ liệu, tức bởi thời gian.</b> Bộ kiểm ghi {@code context} một lần lúc kiểm lô; API
 * đọc nó ra hàng tuần sau đó, và với một lô đã {@code COMMITTED} thì hàng tháng sau — lúc ấy dòng
 * dữ liệu có thể do một bản nhị phân cũ hơn ghi ra (triển khai cuốn chiếu, khôi phục từ bản sao
 * lưu, hoặc một lô kiểm trước lần vá này mà chưa ai kiểm lại). Bên đọc <b>không được phép giả
 * định</b> bên ghi là mã nguồn hiện tại. Danh sách khoá được phép là thứ duy nhất đúng ở ranh giới
 * đó, và nó là một phép kiểm <b>cấu trúc</b> — khác loại với việc soạn văn bản của bộ kiểm, nên
 * hai chỗ không nhân đôi logic và không lệch nhau được.
 *
 * <p><b>Giới hạn đã biết, nói thẳng:</b> chốt này chỉ canh {@code context}. Nó không cứu được một
 * {@code message} cũ đã mang tên người trong phả — không có cách nào nhận ra tên người trong một
 * câu tiếng Việt bất kỳ mà không dựng lại chính cái rò rỉ đang muốn bịt. Những dòng ấy do
 * {@code V12__redact_legacy_import_issue.sql} dọn, một lần, lúc nâng cấp.</p>
 */
final class ImportIssueRedactor {

    private ImportIssueRedactor() {
    }

    /**
     * Khoá được phép cho một ứng viên <b>đã có trong phả</b> ({@code personId != null}).
     *
     * <p>Không {@code ten}, không {@code doi}, không {@code giaiThich}. {@code tinHieu} là danh
     * sách nhãn tín hiệu ("trùng ngày giỗ", "cùng chi") do bộ chấm điểm sinh ra và <b>không mang
     * giá trị trường nào</b>; {@code giaiThich} của bản cũ thì có ("trùng năm sinh 1975"), nên nó
     * nằm ngoài danh sách và bị cắt kể cả khi dòng dữ liệu là dòng cũ.</p>
     */
    private static final Set<String> KHOA_CHO_PHEP_TRONG_PHA =
            Set.of("personId", "diem", "nguon", "tinHieu");

    /** Khoá được phép của cảnh báo kỵ húy. Tên và đời của bậc trên không nằm trong đây. */
    private static final Set<String> KHOA_CHO_PHEP_KY_HUY = Set.of("tenHuy", "bacTrenId");

    /** Nội dung đã kiểm: câu thông báo và ngữ cảnh máy đọc được. */
    record NoiDung(String message, Map<String, Object> context) {
    }

    /**
     * @param row dòng ứng với {@code issue.rowNo()} trong khu vực chờ; {@code null} nếu không tra
     *        ra. Không còn được dùng để soạn lại câu thông báo — bộ kiểm đã soạn đúng ngay từ
     *        đầu — nhưng chữ ký giữ nguyên vì {@code ImportDtoMapper} sở hữu chỗ gọi.
     */
    static NoiDung apply(ImportIssue issue, PersonRow row) {
        if (issue.code() == IssueCode.IMP_SUSPECT_DUPLICATE) {
            return new NoiDung(issue.message(), locNghiTrung(issue.context()));
        }
        if (issue.code() == IssueCode.IMP_TABOO_COLLISION) {
            return new NoiDung(issue.message(), loc(issue.context(), KHOA_CHO_PHEP_KY_HUY));
        }
        return new NoiDung(issue.message(), issue.context());
    }

    /**
     * Vế trong phả đi qua danh sách khoá; vế trong chính tệp ({@code personId == null}) đi qua
     * nguyên vẹn — toàn bộ nội dung của nó là thứ người nhập vừa gõ, giấu đi chỉ làm họ không đối
     * chiếu được.
     */
    private static Map<String, Object> locNghiTrung(Map<String, Object> context) {
        List<Map<String, Object>> nguon = danhSachNghiNgo(context);
        if (nguon.isEmpty()) {
            return context;
        }
        List<Map<String, Object>> giuLai = new ArrayList<>(nguon.size());
        for (Map<String, Object> ungVien : nguon) {
            giuLai.add(ungVien.get("personId") == null
                    ? ungVien
                    : loc(ungVien, KHOA_CHO_PHEP_TRONG_PHA));
        }
        Map<String, Object> ketQua = new LinkedHashMap<>(context);
        ketQua.put("nghiNgo", List.copyOf(giuLai));
        return Map.copyOf(ketQua);
    }

    /** Giữ đúng các khoá trong danh sách, theo thứ tự gốc. Giá trị {@code null} bị bỏ luôn. */
    private static Map<String, Object> loc(Map<String, Object> nguon, Set<String> choPhep) {
        Map<String, Object> ketQua = new LinkedHashMap<>();
        nguon.forEach((khoa, giaTri) -> {
            if (giaTri != null && choPhep.contains(khoa)) {
                ketQua.put(khoa, giaTri);
            }
        });
        return Map.copyOf(ketQua);
    }

    // -------------------------------------------------------------------------------------
    // Tiện ích đọc context, dùng chung với ImportDtoMapper và ImportReadModel
    // -------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> danhSachNghiNgo(Map<String, Object> context) {
        Object raw = context == null ? null : context.get("nghiNgo");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> ketQua = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o instanceof Map<?, ?> map) {
                ketQua.add((Map<String, Object>) map);
            }
        }
        return ketQua;
    }

    static int soNguyen(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    static String chuoi(Object value) {
        return value == null ? null : Objects.toString(value, null);
    }
}
