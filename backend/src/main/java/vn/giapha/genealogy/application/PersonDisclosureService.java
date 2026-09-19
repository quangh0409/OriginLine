package vn.giapha.genealogy.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.port.BranchRepository;
import vn.giapha.genealogy.domain.port.PersonRepository;

/**
 * <b>Cửa duy nhất</b> để context khác lấy hồ sơ nhân khẩu đã đi qua bộ lọc riêng tư của
 * {@code genealogy} ({@link PrivacyTierService}, BA v2 §10 / Nghị định 13/2023).
 *
 * <h2>Vì sao phải có lớp này</h2>
 * Trước nó, {@code genealogy.application} không mang {@code @NamedInterface} nên không module nào
 * gọi được {@code PrivacyTierService} mà {@code ModularityTests} còn xanh. Hệ quả có thật:
 * {@code dataimport} — bộ sinh mẫu Excel cho Trưởng chi — phải <b>chép lại</b> danh sách trường
 * Tầng 1 vào một hằng số của riêng nó và canh bằng test. Hai bản luật riêng tư thì sớm muộn cũng
 * lệch, và triệu chứng là <b>một màn hình che, màn hình kia không</b> — không ai truy ra được vì
 * sao, vì mỗi bên đều "đúng" theo bản luật của mình.
 *
 * <p>Bản chép ấy trên thực tế <b>đã lệch trước khi bị xoá</b>: nó giấu chữ Hán-Nôm của tên chính
 * với người còn sống, trong khi {@code PersonSummaryView} vẫn đưa đúng chữ ấy lên node phả đồ cho
 * mọi thành viên đã đăng nhập. Đó chính là loại lệch mà lớp này chấm dứt.</p>
 *
 * <h2>Lọc theo NGƯỜI GỌI, không theo một mức cố định</h2>
 * {@link #hoSo} bắt buộc nhận {@link DisclosureAudience}. Cùng một chi: Hội đồng Tộc biểu tải mẫu
 * về thì thấy năm sinh và nguyên quán của người còn sống; Trưởng chi tải đúng chi ấy chỉ nhận tên,
 * giới, đời; Khách thì không nhận được cả sự tồn tại của họ. Một mặt tiền "lọc sẵn ở mức Tầng 1"
 * nghe an toàn hơn, nhưng nó chính là bản chép luật thứ hai đội lốt.
 *
 * <h2>Lớp này KHÔNG quyết định gì</h2>
 * Không một dòng luật riêng tư nào ở đây: không ngưỡng, không danh sách trường, không "che thêm
 * cho chắc". Mọi quyết định đến từ {@link PrivacyTierService#visibility} và
 * {@link PrivacyTierService#toView}; lớp này chỉ <b>dịch kiểu</b> từ {@link PersonView} (vốn chở
 * value object của {@code domain}) sang {@link DisclosedPerson} (chỉ kiểu nguyên thuỷ và VO của
 * shared kernel). Nếu nó tự thêm một phép che thì tệp Excel và màn hình lại lệch nhau — đúng cái
 * nó vừa dẹp. Cần che chặt hơn thì sửa {@code PrivacyTierService}: một chỗ, cho cả hệ thống.
 *
 * <h2>Vì sao value object của domain không đi kèm ra ngoài</h2>
 * {@code PersonName}, {@code LifeDate}, {@code PrivacyFieldGroup} ở lại trong context. Đưa chúng
 * qua ranh giới thì phải gắn siêu dữ liệu framework lên chính lớp domain, mà
 * {@code DomainPurityTest} cấm — domain là POJO thuần, và đó cũng là điều kiện BA v2 §12 đặt ra
 * khi giữ Neo4j làm phương án dự phòng. Giống hệt {@link PersonScreeningService} và
 * {@link GenealogyBulkWriter}.
 */
@Service
@org.springframework.modulith.NamedInterface("loc-rieng-tu")
public class PersonDisclosureService {

    private static final Logger log = LoggerFactory.getLogger(PersonDisclosureService.class);

    private final PersonRepository persons;
    private final BranchRepository branches;
    private final PrivacyTierService privacy;

    public PersonDisclosureService(PersonRepository persons, BranchRepository branches,
                                   PrivacyTierService privacy) {
        this.persons = persons;
        this.branches = branches;
        this.privacy = privacy;
    }

    /**
     * Danh tính của request đang chạy, dựng <b>một lần</b> rồi truyền vào mọi lời gọi
     * {@link #hoSo} của cùng lượt xử lý.
     *
     * <p>Gọi lại giữa chừng không sai kết quả nhưng tốn thêm vài câu SELECT sang
     * {@code membership} mỗi lần, và mở đường cho hai nhân khẩu trong cùng một tệp bị xét theo hai
     * ngữ cảnh khác nhau.</p>
     */
    public DisclosureAudience nguoiGoiHienTai() {
        return new DisclosureAudience(privacy.caller());
    }

    /**
     * Hồ sơ đã lọc của một lô nhân khẩu.
     *
     * <p><b>Id vắng mặt trong kết quả nghĩa là người gọi không được biết người ấy tồn tại</b> —
     * Khách nhìn người còn sống, hoặc bản ghi đã xoá mềm với vai không có phạm vi toàn dòng họ.
     * Bên gọi phải hành xử như thể bản ghi không có: bỏ khỏi tệp, bỏ khỏi danh sách, trả
     * {@code 404} chứ không {@code 403} ở endpoint đơn lẻ — trả 403 là tự xác nhận người đó có
     * thật.</p>
     *
     * <p>Thứ tự lặp của {@code Map} trả về theo đúng thứ tự {@code personIds} truyền vào, để bên
     * gọi không phải sắp lại. Kết quả <b>không bao giờ được cache</b>: nội dung phụ thuộc người
     * gọi, một bản cache của vai này rơi vào tay vai khác là rò rỉ không để lại dấu trong log.</p>
     *
     * @param personIds nhân khẩu cần hỏi; {@code null} và trùng lặp bị bỏ qua
     * @param audience  người đang nhận dữ liệu; {@code null} được hiểu là Khách (fail-closed)
     */
    @Transactional(readOnly = true)
    public Map<UUID, DisclosedPerson> hoSo(Collection<UUID> personIds, DisclosureAudience audience) {
        DisclosureAudience nguoiGoi = audience == null ? DisclosureAudience.khach() : audience;
        if (audience == null) {
            log.warn("hoSo() duoc goi khong kem nguoi goi — ha ve muc Khach. Day gan nhu chac chan"
                    + " la mot cho quen truyen ngu canh, khong phai y dinh that");
        }
        Set<UUID> ids = new LinkedHashSet<>();
        if (personIds != null) {
            personIds.stream().filter(Objects::nonNull).forEach(ids::add);
        }
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Person> daNap = new LinkedHashMap<>();
        for (Person person : persons.byIds(ids)) {
            daNap.put(person.rawId(), person);
        }
        BranchDirectory thuMucChi = BranchDirectory.load(branches,
                daNap.values().stream().map(Person::primaryBranchId).toList());

        Map<UUID, DisclosedPerson> ket = new LinkedHashMap<>();
        for (UUID id : ids) {
            Person person = daNap.get(id);
            if (person == null) {
                continue;
            }
            Optional<PersonVisibility> vis = privacy.visibility(person, nguoiGoi.caller(),
                    thuMucChi.pathOf(person.primaryBranchId()));
            if (vis.isEmpty()) {
                // Khong duoc biet la ton tai — va "khong duoc biet" phai giong het "khong co".
                continue;
            }
            PersonView view = privacy.toView(person, nguoiGoi.caller(), thuMucChi);
            ket.put(id, dich(view, vis.get()));
        }

        if (ket.size() < ids.size()) {
            // Chi dem, khong ghi id: mot dong log liet ke nhan khau bi giau cung la mot ro ri.
            log.debug("Loc rieng tu (vai {}): {}/{} nhan khau hien duoc", nguoiGoi.vai(),
                    ket.size(), ids.size());
        }
        return Map.copyOf(ket);
    }

    // -------------------------------------------------------------------------------------
    // Nội bộ — dịch kiểu, không quyết định gì
    // -------------------------------------------------------------------------------------

    /**
     * {@link PersonView} → {@link DisclosedPerson}.
     *
     * <p>Mọi trường đọc ở đây <b>đã</b> được {@code PrivacyTierService} lọc: {@code view.names()}
     * chỉ còn tên chính khi người gọi không được xem các lớp tên phụ, {@code view.birth()} đã bị
     * làm thô về năm, {@code view.nativePlace()} đã là {@code null} nếu bị giấu. Vì thế ở đây
     * không có một câu {@code if} nào về quyền — và đó là điều kiện để tệp Excel không bao giờ nói
     * khác màn hình.</p>
     */
    private static DisclosedPerson dich(PersonView view, PersonVisibility vis) {
        PersonName chinh = view.names().stream()
                .filter(PersonName::primary)
                .findFirst()
                .orElse(null);
        return new DisclosedPerson(
                view.id(),
                view.displayName(),
                tenTheoLop(view, NameType.HUY),
                tenTheoLop(view, NameType.THUY),
                chinh == null ? null : chinh.hanNom(),
                view.gender(),
                view.generation(),
                view.alive(),
                view.birth() == null ? null : view.birth().year().orElse(null),
                // Nguoi con song khong co ngay mat; nguoi da khuat ma so khong chep am lich thi
                // ngay gio khong tinh duoc — ca hai deu la null, va khong phan biet duoc.
                view.death() == null ? null : view.death().lunar(),
                view.nativePlace(),
                vis.ungroupedFieldsVisible());
    }

    /** Lớp tên phụ đầu tiên thuộc loại này trong danh sách <b>đã lọc</b>; {@code null} nếu không có. */
    private static String tenTheoLop(PersonView view, NameType loai) {
        return view.names().stream()
                .filter(name -> name.type() == loai)
                .map(PersonName::fullName)
                .findFirst()
                .orElse(null);
    }
}
