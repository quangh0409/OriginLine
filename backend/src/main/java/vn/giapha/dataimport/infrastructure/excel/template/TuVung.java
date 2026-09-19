package vn.giapha.dataimport.infrastructure.excel.template;

import java.util.List;
import java.util.function.Function;

/**
 * Một <b>tập giá trị đóng</b> của một cột, kèm luôn bộ giải mã sẽ đọc nó ở phía máy chủ.
 *
 * <h2>Vì sao bộ giải mã nằm ngay trong đối tượng từ vựng</h2>
 * Danh sách chọn trong Excel chỉ có ích khi mọi giá trị trong đó được
 * {@link vn.giapha.dataimport.domain.CellCodec} hiểu đúng. Nếu ai đó thêm mục "Con đẻ" vào danh
 * sách "Quan hệ" mà {@code CellCodec} không biết từ ấy, người dùng sẽ chọn đúng thứ mẫu mời họ chọn
 * rồi bị báo sai — kiểu hỏng khó chịu nhất vì lỗi nằm ở phía ta còn thông báo chỉ về phía họ.
 *
 * <p>Để ghép đôi ấy khỏi đứt, mỗi mục mang theo <b>giá trị mong đợi</b> sau khi giải mã, và
 * {@code TemplateVocabularyTest} chạy {@link #boGiai} trên từng nhãn rồi so. Danh sách chọn vì thế
 * không thể chứa một giá trị mà máy chủ không hiểu.</p>
 *
 * @param tenVungDatTen tên vùng đặt tên (named range) trên trang "Danh mục"; chỉ chữ hoa và gạch
 *        dưới vì Excel không nhận tên vùng có dấu cách hay dấu tiếng Việt
 * @param boGiai        đúng hàm mà tầng ứng dụng sẽ gọi khi đọc ô này
 */
public record TuVung(String tenVungDatTen, Function<String, Object> boGiai, List<Muc> muc) {

    /**
     * @param nhan    chữ hiện trong ô Excel — <b>có dấu tiếng Việt</b>, vì người điền đọc nó
     * @param mongDoi giá trị {@link #boGiai} phải trả về cho {@link #nhan}
     */
    public record Muc(String nhan, Object mongDoi) {
    }

    public TuVung {
        muc = List.copyOf(muc);
    }

    /** Các nhãn theo đúng thứ tự hiện trong danh sách thả xuống. */
    public List<String> nhan() {
        return muc.stream().map(Muc::nhan).toList();
    }
}
