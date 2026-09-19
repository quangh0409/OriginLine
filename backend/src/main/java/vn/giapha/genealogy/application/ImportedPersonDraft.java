package vn.giapha.genealogy.application;

import java.util.Map;
import java.util.UUID;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.LunarDate;

/**
 * Một nhân khẩu <b>sắp được ghi vào phả</b> từ một đường nhập liệu hàng loạt.
 *
 * <h2>Vì sao lại là một kiểu riêng, không phải {@code AddPersonCommand}</h2>
 * {@code AddPersonCommand} nói bằng value object của {@code genealogy.domain}
 * ({@code PersonName}, {@code LifeDate}, {@code PrivacyConsent}). Đưa nó ra ngoài ranh giới
 * context là đẩy domain POJO qua biên — đúng thứ {@code genealogy/package-info} cấm và
 * {@code DomainPurityTest} canh. Bản ghi này chỉ gồm kiểu nguyên thuỷ và VO của shared kernel,
 * nên context khác cầm được mà không phải biết một lớp domain nào.
 *
 * <h2>Ba trường vắng mặt một cách cố ý</h2>
 * <ul>
 *   <li><b>đời thứ</b> — suy ra từ {@link #chaId()}/{@link #meId()}/{@link #vongChongId()}, không
 *       nhận từ bên gọi. Nhận vào là mở đường cho đời thứ trong bảng lệch với đồ thị.</li>
 *   <li><b>điện thoại, email, địa chỉ, ảnh</b> — mẫu Excel cố ý không có (ràng buộc "người còn
 *       sống không nhập hộ"). Không có chỗ nhận thì không ai nhét vào được.</li>
 *   <li><b>mức riêng tư</b> — mặc định KÍN cho người sống, người đã khuất là công khai theo
 *       BA v2 §10. Đường nhập liệu không được phép mở dữ liệu của người sống.</li>
 * </ul>
 *
 * @param thuongGoi tên thường gọi — lớp tên chính, bắt buộc
 * @param huy tên huý; {@code null} nếu sổ không chép
 * @param thuy thuỵ hiệu (tên đặt sau khi mất, khắc trên bài vị)
 * @param hanNom chữ Hán–Nôm của tên thường gọi
 * @param ngayGio ngày giỗ âm lịch. <b>Chỉ nhận được khi biết năm</b>: {@link LunarDate} của shared
 *        kernel bắt buộc có năm, trong khi "mất 15 tháng 8, không rõ năm" là chuyện thường trong
 *        sổ cũ. Ca không rõ năm đi vào đây với {@code null} và bên gọi tự vá cột
 *        {@code death_lunar} trong <b>cùng transaction</b> — xem javadoc của cổng ghi bên
 *        {@code dataimport}.
 * @param chaId cha (ruột hoặc nuôi) đã tồn tại trong phả; {@code null} khi chưa biết
 * @param vongChongId vợ/chồng đã tồn tại, dùng cho <b>dâu/rể</b> — người không có cha mẹ trong
 *        họ. Cạnh hôn phối được tạo <b>ngay lúc này</b> chứ không để lại cho bước sau: đời thứ
 *        được suy từ liên kết, nên một dâu tạo ra mà chưa có liên kết nào sẽ vĩnh viễn không có
 *        đời thứ và không xếp được vào hàng nào trên phả đồ.
 * @param laChong {@code true} khi <b>người đang được tạo</b> đứng ở vai chồng của cạnh hôn phối ấy
 *        (ca rể). Quyết định chiều cạnh, và chiều quyết định {@code spouse_order} thuộc về ai —
 *        "vợ thứ mấy" là thứ tự các bà <b>của một người chồng</b>.
 * @param bacHonPhoi bậc của cạnh hôn phối ấy: vợ cả là 1
 * @param conNuoi {@code true} thì cạnh cha/mẹ là {@code PARENT_ADOPT}
 * @param ghiChuNguon nguồn dữ liệu, ví dụ "lô a1b2 · dòng 137 · chi-at.xlsx" — ba tháng sau, mở
 *        hồ sơ một cụ là biết ngay dòng nào của tệp nào sinh ra nó
 */
@org.springframework.modulith.NamedInterface("ghi-pha")
public record ImportedPersonDraft(String thuongGoi,
                                  String huy,
                                  String thuy,
                                  String hanNom,
                                  Gender gender,
                                  boolean conSong,
                                  Integer namSinh,
                                  LunarDate ngayGio,
                                  String nguyenQuan,
                                  UUID chiId,
                                  UUID chaId,
                                  UUID meId,
                                  UUID vongChongId,
                                  boolean laChong,
                                  Integer bacHonPhoi,
                                  boolean conNuoi,
                                  Map<String, Object> thuocTinh,
                                  String ghiChuNguon) {

    public ImportedPersonDraft {
        thuocTinh = thuocTinh == null ? Map.of() : Map.copyOf(thuocTinh);
    }
}
