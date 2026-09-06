package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import vn.giapha.genealogy.application.AddPersonService;
import vn.giapha.genealogy.application.command.AddPersonCommand;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.shared.vo.Gender;

/**
 * <h1>LỖI MAIN CODE ĐANG MỞ — test này cố ý bị {@code @Disabled}, đừng xoá</h1>
 *
 * <p><b>Triệu chứng:</b> {@code AddPersonService.add(...)} — tức {@code POST /api/v1/persons} —
 * <b>không thêm được nhân khẩu còn sống nào</b>. Giao dịch đổ ngay ở lệnh flush đầu tiên:</p>
 *
 * <pre>
 *   ERROR: new row for relation "person" violates check constraint "ck_person_alive_vs_death"
 *   ERROR: new row for relation "person" violates check constraint "ck_person_birth_lunar"
 * </pre>
 *
 * <p><b>Nguyên nhân:</b>
 * {@code backend/src/main/java/vn/giapha/genealogy/infrastructure/jpa/JsonbAttributeConverter.java:26}</p>
 *
 * <pre>
 *   return MAPPER.writeValueAsString(attribute == null ? Map.of() : attribute);
 * </pre>
 *
 * <p>Converter được dùng chung cho <b>ba</b> cột, nhưng ba cột đó có ràng buộc khác hẳn nhau:</p>
 * <ul>
 *   <li>{@code person.attributes} — {@code NOT NULL DEFAULT '{}'}: đổi {@code null} thành
 *       {@code "{}"} là <b>đúng</b>;</li>
 *   <li>{@code person.birth_lunar} — nullable, và {@code ck_person_birth_lunar} đòi khi khác
 *       {@code NULL} thì phải có {@code day} và {@code month}. {@code "{}"} vi phạm ngay;</li>
 *   <li>{@code person.death_lunar} — nullable, và {@code ck_person_alive_vs_death} đòi người còn
 *       sống phải có {@code death_lunar IS NULL}. {@code "{}"} khác {@code NULL}, nên
 *       <b>mọi</b> nhân khẩu còn sống đều bị CSDL từ chối.</li>
 * </ul>
 *
 * <p>{@code PersonMapper.applyToEntity} truyền đúng {@code null} (dòng 103 và 105); chỗ hỏng nằm
 * hoàn toàn trong converter.</p>
 *
 * <p><b>Cách sửa gợi ý</b> (thuộc sở hữu của W2, không sửa từ vùng test):
 * để converter trả {@code null} khi giá trị vào là {@code null}, rồi bảo đảm mặc định
 * {@code '{}'} cho riêng {@code attributes} ở phía entity (khởi tạo trường là map rỗng —
 * {@code PersonJpaEntity} đã làm sẵn). Hoặc tách một converter riêng cho hai cột âm lịch.</p>
 *
 * <p><b>Vì sao lỗi này chưa lộ ra:</b> {@code demo/writer/DemoRelationalWriter} gieo dữ liệu bằng
 * SQL thuần, frontend chạy trên MSW, và trước bộ test này chưa có một integration test nào —
 * đường ghi JPA của {@code person} chưa từng được chạy thật.</p>
 *
 * <p><b>Bỏ {@code @Disabled} ngay sau khi sửa.</b> Khi đó cũng nên chuyển {@link PersonFixtures}
 * sang gieo dữ liệu qua {@code AddPersonService} để phần suy luận đời thứ / chi kế thừa / thứ tự
 * sinh được kiểm chứng luôn.</p>
 */
@DisplayName("Đường ghi JPA của person")
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class PersonJpaWriteIT extends AbstractIntegrationTest {

    @Autowired
    private AddPersonService addPerson;

    private UUID chiGiap;

    @BeforeEach
    void setUpClan() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");
        authenticateAs("sub-admin", "ADMIN");
    }

    @Test
    @DisplayName("thêm nhân khẩu còn sống, không có ngày âm lịch nào, phải ghi được")
    void themNhanKhauConSongKhongCoNgayAmLich_ghiDuoc() {
        AddPersonCommand cmd = new AddPersonCommand(
                List.of(PersonName.of(NameType.THUONG_GOI, "Nguyễn Văn Sống", true)),
                Gender.MALE, true, null, null, "Bac Ninh", "Ha Noi", null, null, null,
                chiGiap, null, null, null, List.of(), false, false, "test lỗi converter");

        PersonView view = addPerson.add(cmd);

        assertThat(view.id()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT death_lunar IS NULL FROM person WHERE id = ?",
                Boolean.class, view.id()))
                .as("người còn sống bắt buộc có death_lunar = NULL (ck_person_alive_vs_death)")
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT birth_lunar IS NULL FROM person WHERE id = ?",
                Boolean.class, view.id()))
                .as("không nhập ngày sinh âm thì birth_lunar phải là NULL, không phải '{}'")
                .isTrue();
        assertThat(graphNodeExists(view.id())).isTrue();
    }
}
