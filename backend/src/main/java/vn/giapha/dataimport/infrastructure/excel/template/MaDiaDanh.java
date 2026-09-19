package vn.giapha.dataimport.infrastructure.excel.template;

/**
 * Một mục của danh mục {@code place_division}, ở dạng hiện được trong một danh sách chọn.
 *
 * <p>Danh sách chọn chỉ toàn mã trần ({@code VN}, {@code US}) thì không ai chọn đúng, nên nhãn
 * hiện ra là {@code "VN — Việt Nam"} còn thứ đi vào ô là... cũng chính chuỗi ấy. Đó là chỗ cần cẩn
 * thận: {@code TextNormalizer.normalizeCode} sẽ bỏ khoảng trắng và nâng hoa, biến nó thành
 * {@code "VN—VIỆTNAM"} chứ không phải {@code "VN"}.</p>
 *
 * <p>Vì thế danh sách chọn <b>chỉ chứa mã</b>, còn tên nằm ở cột bên cạnh trên trang "Danh mục" để
 * tra. Đánh đổi này là cố ý: một ô sai mã im lặng đắt hơn nhiều so với việc phải liếc sang trang
 * danh mục một lần.</p>
 */
public record MaDiaDanh(String ma, String ten) {

    /** Dòng tra cứu hiện trên trang "Danh mục". */
    public String dongTraCuu() {
        return ma + " — " + ten;
    }
}
