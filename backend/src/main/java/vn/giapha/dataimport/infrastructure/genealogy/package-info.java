/**
 * Adapter cho hai phép dò mà {@code dataimport} mượn của context {@code genealogy}.
 *
 * <h2>Hai lớp ở đây là bộ DỊCH KIỂU, không phải bộ dò</h2>
 * Cả {@code DuplicateScanAdapter} lẫn {@code TabooScanAdapter} đều không chứa một dòng logic
 * nghiệp vụ nào. Chúng dựng tham số, gọi facade {@code PersonScreeningService}, rồi ánh xạ kết quả
 * về kiểu của {@code dataimport}. Nhờ thế cả {@code domain} lẫn {@code application} của context
 * này <b>không biết gì</b> về {@code genealogy} — bề mặt tiếp xúc gói gọn trong đúng hai tệp.
 *
 * <h2>Đường vào: facade {@code PersonScreeningService}, named interface {@code "do-trung"}</h2>
 * {@code genealogy} <b>không</b> mở cả gói {@code application}, càng không mở {@code domain}. Thay
 * vào đó chủ sở hữu context ấy dựng một facade ở tầng {@code application} và gắn
 * {@code @NamedInterface("do-trung")} lên <b>đúng năm kiểu</b>, tất cả đều ở tầng
 * {@code application}: {@code PersonScreeningService} (cửa duy nhất), {@code ScreeningSubject}
 * (đầu vào), {@code TabooHit}, {@code DuplicateReport}, {@code DuplicateMatch} (đầu ra).
 *
 * <p><b>Không có kiểu {@code domain} nào trong danh sách ấy, và đó là điểm mấu chốt.</b> Value
 * object của domain ({@code PersonName}, {@code LifeDate}, {@code NameType},
 * {@code DatePrecision}, {@code TabooConflict}) <b>không đi qua ranh giới context</b>. Đưa chúng ra
 * ngoài thì phải gắn siêu dữ liệu framework lên chính lớp domain, mà {@code DomainPurityTest} cấm
 * điều đó: domain là POJO thuần, và đó cũng là điều kiện BA v2 §12 đặt ra khi giữ Neo4j làm phương
 * án dự phòng. Hai adapter ở đây vì thế <b>không nhìn thấy</b> {@code PersonName} hay
 * {@code LifeDate}; việc xếp một chuỗi tên vào lớp tên nào, và việc dựng mốc sinh–mất song lịch,
 * đều là kiến thức của {@code genealogy} và nằm trong facade.</p>
 *
 * <p>Lý do mở hẹp thay vì mở cả gói nằm ở {@code vn.giapha.genealogy.package-info}:
 * {@code genealogy} là context lớn nhất hệ thống, mở cả {@code application} là để mọi module với
 * tới {@code Person}, {@code PersonRepository}, {@code PrivacyTierService} — tức xoá gần hết ranh
 * giới mà {@code ModularityTests} sinh ra để giữ.</p>
 *
 * <p><b>Hệ quả khi sửa:</b> nếu chữ ký của facade sau này nhận thêm một kiểu mới, kiểu ấy phải là
 * kiểu của tầng {@code application} bên {@code genealogy} và phải được gắn nhãn ở đó, nếu không
 * {@code ModularityTests} đỏ. Đó là hành vi mong muốn — nó buộc người sửa cân nhắc, chứ không lặng
 * lẽ nới bề mặt tiếp xúc. Tuyệt đối đừng "chữa" bằng cách chép kiểu đó sang đây, và càng đừng chữa
 * bằng cách gắn nhãn lên một lớp domain.</p>
 *
 * <h2>Điều tuyệt đối KHÔNG được làm ở đây</h2>
 * Viết một bộ chấm điểm nghi trùng thứ hai bằng SQL cho nhanh. Bộ dò đã có mang một bộ trọng số
 * nhiều tín hiệu, với ngưỡng được chọn để chịu được hai tập quán đặt tên của người Việt: cả một
 * đời mang chung chữ đệm (trùng tên và trùng đời là <b>bình thường</b>, không đủ để kêu), và tục
 * đặt tên con theo tên người anh đã mất (cùng cha cùng tên thì <b>có</b> kêu). Một bản thứ hai sẽ
 * lệch, và triệu chứng là màn nhập liệu bảo trùng còn màn thêm người bảo không — không ai truy ra
 * được vì sao. Thà không có cảnh báo còn hơn có hai cảnh báo mâu thuẫn nhau.
 */
package vn.giapha.dataimport.infrastructure.genealogy;
