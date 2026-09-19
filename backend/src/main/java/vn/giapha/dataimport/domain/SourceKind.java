package vn.giapha.dataimport.domain;

/**
 * Nguồn của một lô.
 *
 * <p>Đợt này chỉ dùng {@link #EXCEL}. {@link #GEDCOM} có mặt <b>từ ngày đầu</b> một cách có chủ ý:
 * nhờ nó mà bộ nhập GEDCOM ở đợt sau chỉ phải viết một bộ phân tích, chứ không phải viết lại cả
 * đường ống — khu vực chờ, bộ kiểm, khoá bất biến và bước ghi đều dùng lại nguyên xi. Bỏ enum này
 * đi cho gọn thì đợt sau phải chạy migration trên dữ liệu thật.</p>
 */
public enum SourceKind {
    EXCEL,
    GEDCOM
}
