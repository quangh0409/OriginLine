package vn.giapha.dataimport.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Một dòng vừa đọc khỏi tệp, <b>trước khi</b> được hiểu thành nhân khẩu hay hôn phối.
 *
 * <h2>Giữ cả hai bản, và đây không phải sự thừa thãi</h2>
 * {@link #raw()} là nguyên văn ô gốc; {@link #normalized()} là bản đã NFC, đã dọn khoảng trắng.
 * Khi ba tháng sau có tranh cãi "tôi gõ đúng mà", {@code raw} là bằng chứng duy nhất phân biệt
 * được hai chuyện có cách chữa hoàn toàn khác nhau: <b>họ chép sai sổ</b>, hay <b>ta phân tích sai
 * tệp</b>.
 *
 * @param rowNo số dòng <b>đúng như Excel đánh</b> (dòng tiêu đề là 1), không phải chỉ số mảng —
 *        người nhập sẽ mở tệp ra và nhảy tới đúng số đó
 */
public record RawRow(int rowNo, Map<String, String> raw, Map<String, String> normalized) {

    public RawRow {
        raw = raw == null ? Map.of() : new LinkedHashMap<>(raw);
        normalized = normalized == null ? Map.of() : new LinkedHashMap<>(normalized);
    }

    /** Giá trị đã chuẩn hoá của một cột Nhân khẩu; {@code null} khi ô trống hoặc cột không có. */
    public String get(ImportColumn column) {
        return normalized.get(column.tieuDe());
    }

    /** Giá trị đã chuẩn hoá của một cột Hôn phối. */
    public String get(MarriageColumn column) {
        return normalized.get(column.tieuDe());
    }

    /** Dòng rỗng hoàn toàn — người nhập xoá nội dung nhưng để lại khung dòng. Bỏ qua, không báo lỗi. */
    public boolean rong() {
        return normalized.values().stream().allMatch(v -> v == null || v.isBlank());
    }
}
