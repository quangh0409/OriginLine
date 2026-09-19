package vn.giapha.genealogy.application;

import java.util.Objects;

/**
 * <b>Người đang nhận dữ liệu</b> — ảnh chụp danh tính của một lượt gọi, ở dạng context khác cầm
 * được mà không nhìn thấy bên trong.
 *
 * <h2>Vì sao kiểu này tồn tại thay vì để facade tự đoán</h2>
 * Mọi phép lọc riêng tư của {@code genealogy} đều là hàm của <b>cặp</b> (hồ sơ, người gọi): cùng
 * một cụ bà còn sống, Hội đồng Tộc biểu thấy đủ, Trưởng chi chỉ thấy tên và đời, Khách vãng lai
 * không thấy là có người ấy. Một mặt tiền trả "hồ sơ đã lọc" mà <b>không</b> nhận người gọi thì chỉ
 * còn một cách: lọc theo một mức cố định — và mức cố định ấy chính là bản chép luật thứ hai mà lớp
 * này sinh ra để xoá. Vì thế {@link PersonDisclosureService#hoSo} <b>bắt buộc</b> nhận một
 * {@code DisclosureAudience}: quên nghĩ về người gọi không còn là một lỗi biểu diễn được.
 *
 * <h2>Chỉ dựng được bằng hai cách, và cả hai đều an toàn</h2>
 * <ol>
 *   <li>{@link PersonDisclosureService#nguoiGoiHienTai()} — đọc {@code SecurityContext} của
 *       request đang chạy qua {@link PrivacyTierService#caller()}. Đây là lối duy nhất tạo ra được
 *       một ngữ cảnh <b>có quyền</b>.</li>
 *   <li>{@link #khach()} — Khách vãng lai, tức mức <b>kín nhất</b> hệ thống có. Public vì test và
 *       cổng giả cần một giá trị hợp lệ để truyền; và vì nếu ai đó dùng nhầm nó ở đường thật thì
 *       hậu quả là <i>thiếu dữ liệu</i>, không phải rò dữ liệu.</li>
 * </ol>
 *
 * <p>Constructor thật là <b>package-private</b>: không context nào ngoài {@code genealogy} dựng
 * được một ngữ cảnh mang vai ADMIN/COUNCIL để tự nâng quyền cho mình. Đó là lý do lớp này không
 * phải một {@code record}.</p>
 *
 * <p><b>Dựng một lần cho mỗi request rồi truyền đi.</b> Hỏi lại giữa chừng vừa tốn hai câu SELECT
 * sang {@code membership}, vừa mở đường cho hai nhân khẩu trong cùng một tệp Excel bị xét theo hai
 * ngữ cảnh khác nhau — xem javadoc {@link CallerContext}.</p>
 */
@org.springframework.modulith.NamedInterface("loc-rieng-tu")
public final class DisclosureAudience {

    private final CallerContext caller;

    DisclosureAudience(CallerContext caller) {
        this.caller = Objects.requireNonNull(caller, "DisclosureAudience.caller khong duoc null");
    }

    /**
     * Khách vãng lai: không token, không nhân khẩu, không phạm vi chi nào.
     *
     * <p>Theo BA v2 §10 và Nghị định 13/2023, ngữ cảnh này <b>không thấy bất kỳ người còn sống
     * nào</b> — kể cả tên. Dùng làm giá trị mặc định fail-closed.</p>
     */
    public static DisclosureAudience khach() {
        return new DisclosureAudience(CallerContext.guest());
    }

    /**
     * Tên vai kỹ thuật, <b>chỉ để ghi log và để test khẳng định</b>.
     *
     * <p>Không dùng nó để tự quyết định hiển thị ở context khác: làm thế là dựng lại bản luật thứ
     * hai bằng chuỗi ký tự. Câu hỏi "được thấy gì" chỉ có một nơi trả lời, và nơi đó là
     * {@link PrivacyTierService}.</p>
     */
    public String vai() {
        return caller.role().name();
    }

    /** Ngữ cảnh thật — <b>không</b> rời khỏi gói {@code genealogy.application}. */
    CallerContext caller() {
        return caller;
    }

    @Override
    public String toString() {
        return "DisclosureAudience[vai=" + vai() + "]";
    }
}
