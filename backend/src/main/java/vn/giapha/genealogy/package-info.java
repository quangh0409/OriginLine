/**
 * Bounded context <b>genealogy</b> — cây phả hệ, thêm/sửa nhân khẩu, xóa mềm, quan hệ họ hàng.
 *
 * <p>Khái niệm lõi: Person (aggregate root), PersonName (húy/tự/hiệu/thụy), Branch (chi/ngành/cành/nhánh, ltree), Relationship; port PersonRepository, TreeGraphPort.</p>
 *
 * <p>Bốn lớp theo kiến trúc Hexagonal, phụ thuộc <b>một chiều</b>:
 * {@code api → application → domain}; {@code infrastructure} hiện thực các port do
 * {@code domain} khai báo. Context khác chỉ được gọi qua application service public
 * của context này hoặc qua domain event — không bao giờ chạm thẳng repository.</p>
 *
 * <h2>Named interface {@code "do-trung"} — cái gì được mở ra ngoài, và vì sao chỉ có thế</h2>
 *
 * <p>{@code dataimport} cần đúng hai phép dò đã có sẵn ở đây: {@code DuplicatePersonChecker}
 * (nghi trùng người) và {@code TabooNameChecker} (kỵ húy). Không mở thì đường nhập liệu buộc phải
 * viết bộ chấm điểm thứ hai, và triệu chứng sẽ là <b>màn nhập liệu bảo trùng còn màn thêm người
 * bảo không</b> — không ai truy ra được vì sao.</p>
 *
 * <p><b>Mở qua facade, không mở thẳng hai bộ dò.</b> Chữ ký của chúng nói bằng value object của
 * {@code domain} ({@code PersonName}, {@code LifeDate}, {@code NameType}, {@code DatePrecision},
 * {@code TabooConflict}), nên mở thẳng là đẩy value object của domain qua ranh giới context. Vì
 * thế {@code PersonScreeningService} đứng ra làm <b>cửa duy nhất</b>, nói bằng kiểu của chính tầng
 * {@code application}. Đúng <b>năm kiểu</b> mang nhãn {@code "do-trung"}, tất cả đều ở
 * {@code application}:</p>
 *
 * <ul>
 *   <li>{@code PersonScreeningService} — facade, hai phép quét;</li>
 *   <li>{@code ScreeningSubject} — đầu vào, chỉ gồm kiểu nguyên thuỷ và VO của shared kernel;</li>
 *   <li>{@code TabooHit} — va chạm kỵ húy ở dạng dùng được ngoài context;</li>
 *   <li>{@code DuplicateReport}, {@code DuplicateMatch} — đầu ra của phép dò trùng. Hai kiểu này
 *       <b>không</b> cần bản sao: chúng vốn đã là kiểu của tầng application và chữ ký của chúng
 *       không nhắc tới kiểu domain nào.</li>
 * </ul>
 *
 * <h2>Named interface {@code "loc-rieng-tu"} — hồ sơ ĐÃ LỌC, theo đúng người gọi</h2>
 *
 * <p>Cùng một lý do, cho phép lọc riêng tư (BA v2 §10, Nghị định 13/2023). {@code dataimport} sinh
 * mẫu Excel điền sẵn cho Trưởng chi; không gọi được {@code PrivacyTierService} thì nó buộc phải
 * <b>chép lại</b> danh sách trường Tầng 1 vào hằng số của riêng mình. Bản chép ấy <b>đã lệch
 * thật</b> trước khi bị xoá: nó giấu chữ Hán-Nôm của tên chính với người còn sống, trong khi
 * {@code PersonSummaryView} vẫn đưa đúng chữ ấy lên node phả đồ. Một màn hình che, màn hình kia
 * không — và không ai truy ra được vì sao, vì cả hai đều "đúng" theo bản luật của mình.</p>
 *
 * <p><b>Mặt tiền nhận NGƯỜI GỌI, không lọc theo một mức cố định.</b> Đây là điểm thiết kế quan
 * trọng nhất của nhãn này: hiển thị là hàm của <i>cặp</i> (hồ sơ, người gọi), nên một mặt tiền trả
 * "hồ sơ đã lọc ở mức Tầng 1" chỉ là bản chép luật thứ hai đội lốt. Đúng <b>ba kiểu</b> mang nhãn
 * {@code "loc-rieng-tu"}, tất cả ở {@code application}:</p>
 *
 * <ul>
 *   <li>{@code PersonDisclosureService} — cửa duy nhất; không chứa một dòng luật nào, chỉ gọi
 *       {@code PrivacyTierService} rồi dịch kiểu;</li>
 *   <li>{@code DisclosureAudience} — người đang nhận dữ liệu. Constructor thật là package-private:
 *       ngoài context này chỉ dựng được ngữ cảnh <b>Khách</b> ({@code khach()}), tức mức kín nhất,
 *       nên không module nào tự nâng quyền cho mình được;</li>
 *   <li>{@code DisclosedPerson} — hồ sơ đã lọc, chỉ gồm kiểu nguyên thuỷ và VO của shared kernel.
 *       Không chở điện thoại/email/địa chỉ/ảnh: nó phục vụ việc <b>xuất phả</b>, mà một tệp tải về
 *       là dữ liệu rời khỏi hệ thống, không có bộ lọc thứ hai và không có đường thu hồi.</li>
 * </ul>
 *
 * <p><b>Không kiểu {@code domain} nào được gắn nhãn — đây là ranh giới cứng.</b> Domain của context
 * này là POJO thuần: không một annotation Spring hay JPA nào, kể cả siêu dữ liệu chỉ có tác dụng
 * lúc dựng như {@code @NamedInterface}. {@code DomainPurityTest} kiểm điều đó và nó đúng — gắn
 * nhãn lên lớp domain là khoá luật nghiệp vụ vào framework, và phá luôn điều kiện BA v2 §12 đặt ra
 * khi giữ Neo4j làm phương án dự phòng (adapter đồ thị phải thay được mà domain không biết). Một
 * lằn ranh rõ có giá trị chính vì nó không mời gọi ngoại lệ từng ca. Nếu một phép dò mới cần lộ
 * thêm dữ liệu ra ngoài thì cách làm là <b>thêm kiểu ở tầng application</b> và dịch trong facade,
 * chứ không phải gắn nhãn cho lớp domain.</p>
 *
 * <p>Cách làm hiển nhiên hơn là gắn {@code @NamedInterface} lên {@code package-info} của
 * {@code application} — như context {@code audit} vẫn làm. Ở đây thì <b>không</b>: {@code audit} là
 * context nhỏ, còn {@code genealogy} là context lớn nhất hệ thống. Mở cả gói ấy nghĩa là mọi module
 * đều với tới được {@code Person}, {@code PersonRepository}, {@code PrivacyTierService} — tức là
 * <b>xoá gần hết ranh giới</b> mà {@code ModularityTests} sinh ra để giữ. Mở {@code domain} thì còn
 * tệ hơn nữa. Cái giá phải trả cho lựa chọn hẹp: thêm một kiểu vào chữ ký của facade thì phải nhớ
 * gắn nhãn cho kiểu ấy, nếu không {@code ModularityTests} đỏ. Đó là đúng hành vi mong muốn — nó
 * buộc người sửa phải cân nhắc, chứ không lặng lẽ mở rộng bề mặt tiếp xúc.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Genealogy — Phả hệ")
package vn.giapha.genealogy;
