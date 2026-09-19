package vn.giapha.dataimport.infrastructure.excel.template;

import java.util.List;
import java.util.UUID;
import vn.giapha.genealogy.application.DisclosureAudience;

/**
 * Cổng lấy <b>ảnh chụp hiện trạng của một chi</b> để điền sẵn vào mẫu Excel.
 *
 * <h2>Vì sao cổng này nằm ở gói template chứ không ở {@code domain.port}</h2>
 * Đây là <b>nợ kỹ thuật có chủ ý và có hạn</b>, không phải một quyết định kiến trúc. Việc sinh mẫu
 * là chuyện của tầng hạ tầng (nó nói bằng chuỗi ô Excel, không bằng khái niệm của miền), nên một
 * cổng ở {@code domain.port} sẽ phải mang kiểu chỉ tồn tại vì Excel. Ngoài ra gói
 * {@code dataimport.domain} và {@code dataimport.infrastructure.jdbc} đang do luồng khác mở, nên
 * thêm tệp vào đó lúc này là va chạm.
 *
 * <p><b>Khi hai luồng kia hạ cánh:</b> chuyển {@link BranchRosterJdbcAdapter} sang
 * {@code infrastructure.jdbc} và giữ interface này ở đây. Không có gì trong thiết kế cản trở việc
 * đó — adapter không giữ trạng thái.</p>
 *
 * <h2>Ranh giới riêng tư đi qua NGƯỜI GỌI, không qua một mức cố định</h2>
 * Mọi phương thức đọc người đều <b>bắt buộc</b> nhận {@link DisclosureAudience}: mẫu Excel được
 * sinh <i>thay mặt một người cụ thể</i> — Trưởng chi đang bấm nút Tải mẫu — và nội dung tệp phải
 * đúng bằng thứ người ấy vốn đã được xem trên màn hình, không hơn. Chữ ký này là lý do không hiện
 * thực nào của cổng "quên" mất người gọi được.
 *
 * <p>Phép lọc thật nằm ở {@code genealogy}: {@code PrivacyTierService} qua mặt tiền
 * {@code PersonDisclosureService}. Gói này <b>không</b> giữ bản luật riêng tư nào — trước kia nó
 * có một bản chép trong {@code NhanKhauDaCo}, và bản chép ấy đã lệch. Xem javadoc
 * {@link NhanKhauDaCo}.</p>
 *
 * <p><b>Chỉ đọc.</b> Không hiện thực nào của cổng này được ghi vào {@code person},
 * {@code relationship} hay đồ thị AGE — đó là bất biến lớn nhất của cả context.</p>
 */
public interface BranchRosterPort {

    /**
     * Danh tính người đang tải mẫu về, dựng <b>một lần</b> cho cả tệp.
     *
     * <p>Dựng lại giữa chừng thì hai trang của cùng một tệp có thể được lọc theo hai ngữ cảnh khác
     * nhau — một trang có tên, trang kia không, và không ai hiểu vì sao.</p>
     */
    DisclosureAudience nguoiTaiVe();

    /**
     * Thông tin nhận dạng của chi, dùng cho tên tệp và tiêu đề trang Hướng dẫn.
     *
     * @return {@code null} khi không có chi nào mang id ấy — bộ sinh vẫn phải sinh được một mẫu
     *         trắng thay vì nổ, vì mẫu trắng là thứ hữu ích cho một chi hoàn toàn mới
     */
    Chi chi(UUID branchId);

    /**
     * Những người của chi đã có mã {@code EXCEL_MA} trong {@code person_external_ref}, <b>đã lọc
     * theo {@code nguoiTaiVe}</b>.
     *
     * <p>Người đã xoá mềm <b>không</b> có mặt: một tệp gửi ra ngoài không nên chở theo người mà
     * dòng họ đã quyết định gỡ khỏi danh sách làm việc. Mã của họ vẫn giữ chỗ trong
     * {@code person_external_ref}, nên nếu Trưởng chi dùng lại mã ấy cho người khác thì bước đối
     * soát sẽ ra UPDATE — và đó là lý do trang Hướng dẫn dặn không tái sử dụng mã cũ.</p>
     *
     * <p>Người mà <b>người gọi không được biết là tồn tại</b> cũng không có mặt, và vắng mặt theo
     * đúng cách ấy: không dòng trống, không ô "bị ẩn". Hệ quả cần biết: mã của họ sẽ trống chỗ
     * trong tệp, nên nếu Trưởng chi nhập một người mới vào đúng mã đó thì bước đối soát ra UPDATE
     * chứ không phải CREATE.</p>
     */
    List<NhanKhauDaCo> nhanKhau(UUID branchId, DisclosureAudience nguoiTaiVe);

    /**
     * Các cuộc hôn phối mà <b>cả hai</b> người đều đã có mã trong chi này <b>và đều hiện được với
     * người gọi</b>.
     */
    List<HonPhoiDaCo> honPhoi(UUID branchId, DisclosureAudience nguoiTaiVe);

    /**
     * Danh mục mã tỉnh/quốc gia đang có trong {@code place_division}.
     *
     * <p><b>Rỗng là trạng thái hợp lệ</b> và bộ sinh phải xử lý đúng: danh mục 34 tỉnh sau sáp
     * nhập là văn bản pháp quy do quản trị nạp, không phải thứ chép tay vào migration. Khi rỗng,
     * mẫu <b>không</b> gắn danh sách chọn cho cột "Mã nguyên quán" và nói thẳng trong trang Hướng
     * dẫn là danh mục chưa có — không chặn ai, và cũng không giả vờ có một danh sách.</p>
     */
    List<MaDiaDanh> danhMucDiaDanh();

    /**
     * @param ten     tên hiển thị có dấu, ví dụ "Chi Ất"
     * @param path    đường dẫn {@code ltree} — đưa vào trang Hướng dẫn để đối chiếu khi có hai chi
     *                trùng tên
     * @param soNguoi số người đã có mã trong chi; 0 nghĩa là chi nhập lần đầu
     */
    record Chi(UUID id, String ten, String path, long soNguoi) {
    }
}
