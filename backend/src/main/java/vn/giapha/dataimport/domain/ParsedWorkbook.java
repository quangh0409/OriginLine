package vn.giapha.dataimport.domain;

import java.util.List;

/**
 * Kết quả đọc một tệp: hai trang, đã thành dòng, chưa kiểm gì cả.
 *
 * <p>Ranh giới cố ý: bộ đọc <b>không</b> phán xét dữ liệu. Nó chỉ trả lời "tệp này có những dòng
 * gì". Mọi câu hỏi về tính đúng đắn thuộc về bộ kiểm — tách hai việc ra thì bộ kiểm chạy lại được
 * nhiều lần trên cùng một lần đọc, và bộ đọc test được mà không cần cơ sở dữ liệu.</p>
 *
 * @param personHeaders tiêu đề gốc của trang Nhân khẩu, giữ lại để báo lỗi "thiếu cột" bằng đúng
 *        chữ người nhập nhìn thấy
 */
public record ParsedWorkbook(List<RawRow> personRows,
                             List<RawRow> marriageRows,
                             List<String> personHeaders,
                             List<String> marriageHeaders) {

    public ParsedWorkbook {
        personRows = personRows == null ? List.of() : List.copyOf(personRows);
        marriageRows = marriageRows == null ? List.of() : List.copyOf(marriageRows);
        personHeaders = personHeaders == null ? List.of() : List.copyOf(personHeaders);
        marriageHeaders = marriageHeaders == null ? List.of() : List.copyOf(marriageHeaders);
    }

    public static ParsedWorkbook rong() {
        return new ParsedWorkbook(List.of(), List.of(), List.of(), List.of());
    }
}
