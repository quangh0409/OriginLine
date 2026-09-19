package vn.giapha.dataimport.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.giapha.dataimport.ImportWorkbooks;
import vn.giapha.integration.AbstractIntegrationTest;

/**
 * <b>Lỗ hổng chặn nặng nhất của đường ống, và bài kiểm chứng minh nó đã được gỡ.</b>
 *
 * <p>Trước V14 không có chỗ nào ghi quyết định của người cho một cặp nghi trùng, nên mọi cặp vĩnh
 * viễn {@code PENDING} và cổng duyệt chặn <b>bất kỳ lô nào có một người nghi trùng</b>. Nói cách
 * khác: đường ống chỉ chạy được trên tệp không có ai nghi trùng — mà một dòng họ chép lại từ nhiều
 * cuốn sổ thì gần như luôn có, và đó chính là lý do bộ dò trùng tồn tại.</p>
 *
 * <h2>Bốn câu hỏi lớp này trả lời, bằng con số chứ không bằng lời hứa</h2>
 * <ol>
 *   <li><b>"Gộp" với một hồ sơ ĐÃ CÓ TRONG PHẢ có thật sự cập nhật hồ sơ ấy không</b> — hay lặng
 *       lẽ sinh ra người thứ hai? Đo bằng {@code count(person)} trước/sau và bằng
 *       {@code person_external_ref}.</li>
 *   <li><b>"Gộp" hai dòng trong cùng tệp có sinh người mồ côi không?</b> Đo bằng số cạnh cha–con
 *       trỏ vào từng đứa con của dòng bị bỏ — đây là chỗ dễ hỏng nhất trong cả đường ống, và nó
 *       hỏng trong im lặng.</li>
 *   <li><b>"Để riêng" có giữ được cả hai người không?</b></li>
 *   <li><b>Kiểm lại sinh cặp mới có huỷ mất quyết định cũ không?</b> Bắt quyết lại 40 cặp vì có
 *       thêm cặp thứ 41 là cách huấn luyện người ta bấm bừa.</li>
 * </ol>
 *
 * <p>Mọi bước của quy trình đi qua <b>HTTP thật</b> (Spring Security + MVC) trên PostgreSQL +
 * Apache AGE thật. Không bước nào của đường ống được đi tắt bằng một câu {@code UPDATE} thẳng vào
 * bảng; chỉ dữ liệu <i>mồi</i> (một cụ đã có sẵn trong phả từ trước đợt nhập) mới được gieo bằng
 * SQL, vì nó là bối cảnh chứ không phải một bước của đường ống.</p>
 */
@DisplayName("Quyết định nghi trùng: gộp · để riêng · hoãn, đi trọn đường qua API thật")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class ImportDuplicateDecisionIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE = "/api/v1/import";

    @Autowired
    private MockMvc mockMvc;

    private UUID chiAt;

    @BeforeEach
    void setUpDongHo() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");
        UUID appUser = insertAppUser("sub-truong-chi-at", null);
        assignBranchRole(appUser, "BRANCH_HEAD", chiAt);
        authenticateAs("sub-truong-chi-at", "BRANCH_HEAD");
    }

    // =====================================================================================
    // 1. Gộp với một hồ sơ đã có trong phả — BÀI KIỂM CHÍNH
    // =====================================================================================

    /**
     * <b>Bài kiểm quan trọng nhất của cả tệp này.</b>
     *
     * <p>Cụ Cẩn đã nằm trong phả từ trước (nhập tay, không qua đường Excel, nên không có mã nào
     * trỏ tới cụ). Trưởng chi nộp một cuốn sổ khác, trong đó cụ mang mã <i>AT-01-777</i> và có một
     * người con treo vào mã ấy. Bộ dò kêu; người đối chiếu quyết "gộp"; lô được duyệt.</p>
     *
     * <p>Ba điều phải đúng sau đó, và thiếu một điều nào cũng là hỏng nặng:</p>
     * <ul>
     *   <li><b>Không sinh người mới</b> cho cụ — nếu sinh thì phả có hai cụ Cẩn và hai nhánh con
     *       cháu treo vào hai node khác nhau;</li>
     *   <li><b>hồ sơ cũ được cập nhật</b> bằng dữ kiện của cuốn sổ mới — nếu không thì phép "gộp"
     *       chỉ là một phép bỏ dòng;</li>
     *   <li><b>{@code person_external_ref} trỏ mã mới sang cụ cũ</b> — nếu không thì lần tải lại
     *       kế tiếp lại thấy một mã lạ, lại sinh cặp nghi trùng, và cả thao tác gộp trở thành việc
     *       phải làm lại mỗi lần.</li>
     * </ul>
     */
    @Test
    @DisplayName("TRỌN ĐƯỜNG: gộp với hồ sơ đã có trong phả → cập nhật, KHÔNG sinh người mới, mã trỏ đúng")
    void gopVoiHoSoDaCoTrongPha() throws Exception {
        UUID cuCan = gieoCuDaKhuat("Nguyễn Văn Cẩn", 1880, 1, 20, 11, 1945);
        assertThat(countRows("person")).isEqualTo(1);

        byte[] tep = ImportWorkbooks.builder()
                // Cung mot con nguoi, mot cuon so khac, mot ma khac.
                .nhanKhau("AT-01-777", "Nguyễn Văn Cẩn", "", "Nam", "1", "", "", "", "Không",
                        "1880", "20/11/1945", "Làng Đông Ngạc", "")
                // Mot dua con treo vao ma ay: sau khi gop, no PHAI treo vao dung cu cu.
                .nhanKhau("AT-02-010", "Nguyễn Văn Trung", "", "Nam", "2", "AT-01-777", "", "",
                        "Không", "1910", "5/5/1970")
                .build();
        JsonNode lo = taiLen(tep, "so-chi-at-ban-1998.xlsx");
        UUID batchId = UUID.fromString(lo.get("id").asText());

        assertThat(lo.get("status").asText()).isEqualTo("VALIDATED");
        assertThat(lo.get("suspectDuplicateCount").asInt())
                .as("bo do phai keu, neu khong thi ca bai kiem nay khong kiem gi ca")
                .isEqualTo(1);
        assertThat(lo.get("undecidedDuplicateCount").asInt()).isEqualTo(1);
        assertThat(lo.get("plannedCreateCount").asInt()).isEqualTo(2);

        // --- Cap doc duoc, va no CHI mang khoa cua nguoi trong pha ---
        JsonNode caps = doc(get(BASE + "/batches/" + batchId + "/duplicates"), 200);
        assertThat(caps).hasSize(1);
        JsonNode cap = caps.get(0);
        assertThat(cap.get("status").asText()).isEqualTo("PENDING");
        assertThat(cap.get("existing").get("source").asText()).isEqualTo("TREE");
        assertThat(cap.get("existing").get("personId").asText()).isEqualTo(cuCan.toString());
        assertThat(cap.get("evidence")).isEmpty();
        UUID capId = UUID.fromString(cap.get("id").asText());

        // --- Cong duyet chan lai o dung ly do, sau khi canh bao da duoc xac nhan ---
        doc(post(BASE + "/batches/" + batchId + "/acknowledge-warnings"), 200);
        assertThat(doc(post(BASE + "/batches/" + batchId + "/commit"), 422).get("code").asText())
                .isEqualTo("IMP_DUPLICATES_UNDECIDED");

        // --- Quyet "gop" qua API THAT ---
        JsonNode ketQua = quyet(batchId, capId, "MERGED", "đối chiếu với sổ chi Ất bản 1998");
        assertThat(ketQua.get("pair").get("status").asText()).isEqualTo("MERGED");
        assertThat(ketQua.get("pair").get("decidedBy").asText())
                .as("gop hai ho so la thao tac co he qua VINH VIEN len pha; no phai co chu")
                .isEqualTo(appUserCua("sub-truong-chi-at").toString());
        assertThat(ketQua.get("pair").get("decidedAt").asText()).isNotBlank();
        assertThat(ketQua.get("pair").get("note").asText()).contains("1998");

        JsonNode sauQuyet = ketQua.get("batch");
        assertThat(sauQuyet.get("undecidedDuplicateCount").asInt()).isZero();
        assertThat(sauQuyet.get("suspectDuplicateCount").asInt())
                .as("cap van con do — no da duoc QUYET, khong phai da bien mat")
                .isEqualTo(1);
        // CREATE da doi thanh UPDATE: day la he qua bat buoc cua chu "gop".
        assertThat(sauQuyet.get("plannedCreateCount").asInt()).isEqualTo(1);
        assertThat(sauQuyet.get("plannedUpdateCount").asInt()).isEqualTo(1);
        assertThat(sauQuyet.get("canApprove").asBoolean()).isTrue();

        // --- Duyet, cung qua API that ---
        doc(post(BASE + "/batches/" + batchId + "/commit"), 202);
        assertThat(doiGhiXong(batchId).get("status").asText()).isEqualTo("COMMITTED");

        // --- KHONG SINH NGUOI MOI: cu Can + mot nguoi con, la HAI, khong phai ba ---
        assertThat(countRows("person"))
                .as("gop ma van sinh nguoi moi thi pha co hai cu Can, va hai nhanh con chau treo"
                        + " vao hai node khac nhau")
                .isEqualTo(2);
        assertThat(graphNodeCount()).isEqualTo(2);

        // --- HO SO CU DUOC CAP NHAT, va van la dung cai id cu ---
        assertThat(jdbc.queryForObject(
                "SELECT native_place FROM person WHERE id = ?", String.class, cuCan))
                .as("gop ma khong cap nhat thi no chi la mot phep bo dong")
                .isEqualTo("Làng Đông Ngạc");
        assertThat(jdbc.queryForObject(
                "SELECT is_deleted FROM person WHERE id = ?", Boolean.class, cuCan)).isFalse();

        // --- person_external_ref TRO DUNG: ma moi -> cu cu ---
        assertThat(personCua("AT-01-777"))
                .as("khong ghi khoa bat bien thi lan tai lai sau lai hoi lai dung cau vua hoi")
                .isEqualTo(cuCan);

        // --- Va dua con treo vao DUNG cu cu, khong treo lo lung ---
        UUID con = personCua("AT-02-010");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM relationship
                 WHERE to_person_id = ? AND rel_type LIKE 'PARENT%' AND is_deleted = FALSE
                """, Integer.class, con)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT from_person_id FROM relationship
                 WHERE to_person_id = ? AND rel_type = 'PARENT_BIO' AND is_deleted = FALSE
                """, UUID.class, con)).isEqualTo(cuCan);
        assertThat(graphEdgeCount("PARENT", cuCan, con)).isEqualTo(1);

        // --- Lan tai lai KE TIEP khong hoi lai nua: do la toan bo diem cua person_external_ref ---
        JsonNode lanHai = taiLen(tep, "so-chi-at-ban-1998-tai-lai.xlsx", true);
        assertThat(lanHai.get("plannedCreateCount").asInt()).isZero();
        assertThat(lanHai.get("plannedUpdateCount").asInt()).isEqualTo(2);
        assertThat(lanHai.get("suspectDuplicateCount").asInt())
                .as("ma da co chu thi bo do loai chinh nguoi ay ra — khong con gi de hoi")
                .isZero();
    }

    // =====================================================================================
    // 2. Gộp hai dòng trong cùng tệp — KHÔNG SINH NGƯỜI MỒ CÔI
    // =====================================================================================

    @Test
    @DisplayName("Gộp hai dòng trong cùng tệp: dòng bị bỏ biến mất, con cháu KHÔNG mồ côi")
    void gopHaiDongTrongCungTep() throws Exception {
        JsonNode lo = taiLen(tepCoHaiDongTrungNhau(), "so-chep-hai-lan.xlsx");
        UUID batchId = UUID.fromString(lo.get("id").asText());

        JsonNode cap = capDuyNhat(batchId);
        assertThat(cap.get("existing").get("source").asText()).isEqualTo("FILE");
        // Hai ben deu la thu nguoi nhap vua go nen khong co gi de giau.
        assertThat(cap.get("evidence")).isNotEmpty();

        doc(post(BASE + "/batches/" + batchId + "/acknowledge-warnings"), 200);
        quyet(batchId, UUID.fromString(cap.get("id").asText()), "MERGED", null);
        doc(post(BASE + "/batches/" + batchId + "/commit"), 202);
        assertThat(doiGhiXong(batchId).get("status").asText()).isEqualTo("COMMITTED");

        // --- Bon nguoi, khong phai nam: hai dong da thanh mot ---
        assertThat(countRows("person")).isEqualTo(4);
        assertThat(graphNodeCount()).isEqualTo(4);
        // Ma bi bo khong duoc cap phat cho ai ca.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM person_external_ref WHERE external_code = 'AT-02-900'
                """, Integer.class)).isZero();

        UUID ongHai = personCua("AT-02-001");
        UUID chau = personCua("AT-03-001");
        UUID baDau = personCua("AT-02-101");

        // --- KHONG MOT NGUOI MO COI NAO ---
        // Dua chau von tro vao AT-02-900 (dong bi bo); no phai tro sang dong o lai, dung mot lan.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM relationship
                 WHERE to_person_id = ? AND rel_type LIKE 'PARENT%' AND is_deleted = FALSE
                """, Integer.class, chau))
                .as("bo mot dong ma quen tro lai ma cha thi dua chau mat cha TRONG IM LANG")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT from_person_id FROM relationship
                 WHERE to_person_id = ? AND rel_type = 'PARENT_BIO' AND is_deleted = FALSE
                """, UUID.class, chau)).isEqualTo(ongHai);
        assertThat(graphEdgeCount("PARENT", ongHai, chau)).isEqualTo(1);

        // --- Hon phoi cung phai tro lai: mot cap vo chong bien mat la mat mat khong tu lo ra ---
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM relationship
                 WHERE rel_type = 'SPOUSE' AND from_person_id = ? AND to_person_id = ?
                   AND is_deleted = FALSE
                """, Integer.class, ongHai, baDau)).isEqualTo(1);
        assertThat(graphEdgeCount("SPOUSE", ongHai, baDau)).isEqualTo(1);

        // --- Va khong con canh nao tro toi mot node khong ton tai ---
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM relationship r WHERE r.is_deleted = FALSE
                   AND (NOT EXISTS (SELECT 1 FROM person p WHERE p.id = r.from_person_id)
                     OR NOT EXISTS (SELECT 1 FROM person p WHERE p.id = r.to_person_id))
                """, Integer.class)).isZero();

        // --- Du kien cua dong bi bo KHONG mat: dong o lai hut o trong cua no ---
        assertThat(jdbc.queryForObject(
                "SELECT native_place FROM person WHERE id = ?", String.class, ongHai))
                .as("gop mà mất một nửa dữ kiện thì người ta sẽ thôi dùng nút gộp")
                .isEqualTo("Làng Đông Ngạc");
    }

    // =====================================================================================
    // 3. "Để riêng" — cả hai cùng vào phả
    // =====================================================================================

    @Test
    @DisplayName("Để riêng: cả hai dòng cùng vào phả, không ai bị bỏ")
    void deRiengThiCaHaiCungVaoPha() throws Exception {
        JsonNode lo = taiLen(tepHaiDongTrungNhauCungCha(), "hai-anh-em-trung-ten.xlsx");
        UUID batchId = UUID.fromString(lo.get("id").asText());

        doc(post(BASE + "/batches/" + batchId + "/acknowledge-warnings"), 200);
        JsonNode ketQua = quyet(batchId, UUID.fromString(capDuyNhat(batchId).get("id").asText()),
                "DISTINCT", "hai anh em ruột cùng tên đệm theo đời");
        assertThat(ketQua.get("batch").get("undecidedDuplicateCount").asInt()).isZero();
        assertThat(ketQua.get("batch").get("plannedCreateCount").asInt())
                .as("de rieng thi khong dong nao bi bo")
                .isEqualTo(3);

        doc(post(BASE + "/batches/" + batchId + "/commit"), 202);
        assertThat(doiGhiXong(batchId).get("status").asText()).isEqualTo("COMMITTED");

        assertThat(countRows("person")).isEqualTo(3);
        assertThat(personCua("AT-02-001")).isNotNull();
        assertThat(personCua("AT-02-900"))
                .as("de rieng nghia la CA HAI vao pha")
                .isNotNull()
                .isNotEqualTo(personCua("AT-02-001"));
    }

    // =====================================================================================
    // 4. "Hoãn" không phải "đã quyết"
    // =====================================================================================

    @Test
    @DisplayName("Hoãn vẫn chặn cổng duyệt — nếu không, nó thành nút 'cho tôi qua'")
    void hoanVanChan() throws Exception {
        JsonNode lo = taiLen(tepCoHaiDongTrungNhau(), "chua-chac.xlsx");
        UUID batchId = UUID.fromString(lo.get("id").asText());

        doc(post(BASE + "/batches/" + batchId + "/acknowledge-warnings"), 200);
        JsonNode ketQua = quyet(batchId, UUID.fromString(capDuyNhat(batchId).get("id").asText()),
                "DEFERRED", "chờ hỏi cụ trưởng tộc");

        // Loi khai VAN duoc ghi: ai hoan, luc nao. "Hoan" la mot quyet dinh co trach nhiem,
        // no chi khong phai mot quyet dinh MO KHOA.
        assertThat(ketQua.get("pair").get("status").asText()).isEqualTo("DEFERRED");
        assertThat(ketQua.get("pair").get("decidedBy").asText()).isNotBlank();
        assertThat(ketQua.get("pair").get("decidedAt").asText()).isNotBlank();

        assertThat(ketQua.get("batch").get("undecidedDuplicateCount").asInt()).isEqualTo(1);
        assertThat(ketQua.get("batch").get("canApprove").asBoolean()).isFalse();
        assertThat(doc(post(BASE + "/batches/" + batchId + "/commit"), 422).get("code").asText())
                .isEqualTo("IMP_DUPLICATES_UNDECIDED");
        assertThat(countRows("person")).isZero();
    }

    // =====================================================================================
    // 5. Kiểm lại sinh cặp MỚI: quyết định cũ cho cặp KHÔNG ĐỔI vẫn còn giá trị
    // =====================================================================================

    /**
     * Nguyên tắc của V13 (vân tay tập cảnh báo) áp ở đây, nhưng ở <b>độ phân giải từng cặp</b>.
     *
     * <p>Huỷ hết quyết định mỗi lần kiểm lại thì một lô 40 cặp sẽ bắt người ta quyết lại 40 lần vì
     * cặp thứ 41 xuất hiện — và đó là cách chắc chắn nhất để lần thứ ba họ bấm mà không đọc. Nhưng
     * cặp <b>mới</b> thì phải chặn: nó là một câu hỏi chưa ai trả lời.</p>
     */
    @Test
    @DisplayName("Kiểm lại sinh cặp MỚI: quyết định cũ còn nguyên, lô vẫn bị chặn vì cặp mới")
    void kiemLaiSinhCapMoi_quyetDinhCuVanCon() throws Exception {
        JsonNode lo = taiLen(tepCoHaiDongTrungNhau(), "lan-mot.xlsx");
        UUID batchId = UUID.fromString(lo.get("id").asText());

        JsonNode capCu = capDuyNhat(batchId);
        UUID capCuId = UUID.fromString(capCu.get("id").asText());
        doc(post(BASE + "/batches/" + batchId + "/acknowledge-warnings"), 200);
        String quyetLuc = quyet(batchId, capCuId, "DISTINCT", null)
                .get("pair").get("decidedAt").asText();

        // --- Giua hai lan kiem, mot nguoi KHAC nhap tay mot cu vao pha ---
        UUID cuMoiVaoPha = gieoCuDaKhuat("Nguyễn Văn Cẩn", 1880, 1, 20, 11, 1945);

        JsonNode kiemLai = doc(post(BASE + "/batches/" + batchId + "/validate"), 200);
        assertThat(kiemLai.get("suspectDuplicateCount").asInt())
                .as("lan kiem nay phai sinh them cap, neu khong thi test chua dung vao tinh huong"
                        + " no dinh dung")
                .isEqualTo(2);

        List<JsonNode> caps = list(doc(get(BASE + "/batches/" + batchId + "/duplicates"), 200));
        JsonNode conLai = caps.stream()
                .filter(c -> c.get("id").asText().equals(capCuId.toString()))
                .findFirst().orElseThrow(() -> new AssertionError("cap cu da bien mat: " + caps));
        assertThat(conLai.get("status").asText())
                .as("cap KHONG DOI thi quyet dinh cu van con gia tri")
                .isEqualTo("DISTINCT");
        assertThat(conLai.get("decidedAt").asText())
                .as("va no van la dung lan quyet ay, khong bi ghi de")
                .isEqualTo(quyetLuc);

        JsonNode capMoi = caps.stream()
                .filter(c -> !c.get("id").asText().equals(capCuId.toString()))
                .findFirst().orElseThrow();
        assertThat(capMoi.get("status").asText()).isEqualTo("PENDING");
        assertThat(capMoi.get("existing").get("personId").asText())
                .isEqualTo(cuMoiVaoPha.toString());

        // --- Lo VAN bi chan, vi cap moi chua ai quyet ---
        assertThat(kiemLai.get("undecidedDuplicateCount").asInt()).isEqualTo(1);
        // Tap canh bao da doi nen phai xac nhan lai truoc — roi cong duyet dung lai o cap moi.
        doc(post(BASE + "/batches/" + batchId + "/acknowledge-warnings"), 200);
        assertThat(doc(post(BASE + "/batches/" + batchId + "/commit"), 422).get("code").asText())
                .isEqualTo("IMP_DUPLICATES_UNDECIDED");
        assertThat(countRows("person"))
                .as("chi co dung cu vua duoc gieo tay, khong mot dong nao cua lo lot vao")
                .isEqualTo(1);
    }

    // =====================================================================================
    // 6. Không rò rỉ
    // =====================================================================================

    /**
     * Đường nhập liệu <b>không phát một trường nhân khẩu nào</b> của người đã có trong phả, và lối
     * gọi quyết định không được mở bề mặt thứ năm sau bốn bề mặt vừa bị bịt.
     *
     * <p>Ca dựng đúng tình huống nguy hiểm nhất: người bị nghi <b>còn sống</b> và có đủ dữ liệu
     * Tầng 2 / Tầng 3. Phép khẳng định soi <b>toàn bộ thân phản hồi</b> chứ không soi một trường
     * cụ thể — kể cả một trường phụ mà người viết code quên mất.</p>
     */
    @Test
    @DisplayName("Phản hồi của lối gọi quyết định KHÔNG chứa một trường nhân khẩu nào của người trong phả")
    void phanHoiQuyetDinhKhongRoRi() throws Exception {
        String tenThatCoDau = "Nguyễn Văn Tuân";
        UUID nguoiConSong = gieoNguoiConSong(tenThatCoDau, 1975, 3);

        byte[] tep = ImportWorkbooks.builder()
                .nhanKhau("AT-03-001", "Nguyen Van Tuan", "", "Nam", "3", "", "", "", "Có",
                        "1975", "")
                .build();
        JsonNode lo = taiLen(tep, "nghi-trung-voi-nguoi-con-song.xlsx");
        UUID batchId = UUID.fromString(lo.get("id").asText());

        JsonNode cap = capDuyNhat(batchId);
        assertThat(cap.get("existing").get("personId").asText()).isEqualTo(nguoiConSong.toString());

        String than = thanQuyet(batchId, UUID.fromString(cap.get("id").asText()), "DISTINCT");

        assertThat(than)
                .as("ten that co dau cua nguoi con song khong duoc xuat hien o bat ky dau")
                .doesNotContain(tenThatCoDau);
        assertThat(than).doesNotContain("0900000001");      // số điện thoại Tầng 3
        assertThat(than).doesNotContain("nguoi@example.com");
        assertThat(than).doesNotContain("Giao vien");        // nghề nghiệp Tầng 2
        assertThat(than).doesNotContain("Bac Ninh");         // nguyên quán Tầng 2
        // Khoa thi PHAI co — giao dien cam no goi GET /persons/{id}, noi bo loc phan tang chay that.
        assertThat(than).contains(nguoiConSong.toString());
        // Va du lieu cua CHINH nguoi nhap thi van day du, neu khong thi man doi chieu vo dung.
        assertThat(than).contains("Nguyen Van Tuan");

        JsonNode benTrongPha = JSON.readTree(than).get("pair").get("existing");
        for (String truong : new String[] {"displayName", "tabooName", "generation", "birthYear",
                "deathLunar", "fatherCode", "nativePlace", "externalCode", "rowNo"}) {
            assertThat(benTrongPha.has(truong))
                    .as("truong %s cua nguoi trong pha", truong)
                    .isFalse();
        }
    }

    // =====================================================================================
    // 7. Phạm vi và tính toàn vẹn của lối gọi
    // =====================================================================================

    @Test
    @DisplayName("Cặp của lô khác → 404; quyết định không hợp lệ → 400; cả hai đều không ghi gì")
    void loiGoiSaiThiKhongGhiGi() throws Exception {
        JsonNode loA = taiLen(tepCoHaiDongTrungNhau(), "lo-a.xlsx");
        UUID loAId = UUID.fromString(loA.get("id").asText());
        UUID capId = UUID.fromString(capDuyNhat(loAId).get("id").asText());

        // Cap co that, nhung duoi mot ma lo khong phai cua no. URL la thu duy nhat da qua phep
        // kiem pham vi chi, nen coi cap ay nhu khong ton tai la cau tra loi dung.
        UUID loKhac = UUID.randomUUID();
        MvcResult khongThay = mockMvc.perform(post(BASE + "/batches/" + loKhac + "/duplicates/"
                        + capId + "/decision")
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"MERGED\"}")
                .with(truongChiAt())).andReturn();
        assertThat(khongThay.getResponse().getStatus()).isEqualTo(404);

        // PENDING khong phai mot quyet dinh gui len duoc: rut lai mot loi khai da ghi khong phai
        // la xoa no di.
        MvcResult khongHopLe = mockMvc.perform(post(BASE + "/batches/" + loAId + "/duplicates/"
                        + capId + "/decision")
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"PENDING\"}")
                .with(truongChiAt())).andReturn();
        assertThat(khongHopLe.getResponse().getStatus()).isEqualTo(400);

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM import_duplicate_pair WHERE status <> 'PENDING'
                """, Integer.class))
                .as("khong loi goi sai nao duoc de lai mot quyet dinh")
                .isZero();
    }

    // =====================================================================================
    // Tiện ích
    // =====================================================================================

    /**
     * Một tệp trong đó <b>cùng một người đàn ông được chép hai lần</b> với hai mã khác nhau — ca
     * rất thường gặp khi ông xuất hiện ở cả trang đời cha lẫn trang đời con trong sổ.
     *
     * <p>Dòng bị nghi ({@code AT-02-900}) cố ý là dòng <b>có con và có vợ</b>: nếu phép gộp quên
     * trỏ lại thì đứa cháu và bà dâu đều rơi ra khỏi cây, và rơi trong im lặng.</p>
     */
    private static byte[] tepCoHaiDongTrungNhau() {
        return ImportWorkbooks.builder()
                .nhanKhau("AT-01-001", "Nguyễn Văn Cẩn", "", "Nam", "1", "", "", "", "Không",
                        "1880", "20/11/1945")
                // Ban chep o trang doi cha: co ma cha, khong co nguyen quan.
                .nhanKhau("AT-02-001", "Nguyễn Văn Hai", "", "Nam", "2", "AT-01-001", "", "",
                        "Không", "1910", "12/4/1975")
                // Ban chep o trang doi con: KHONG co ma cha, nhung co nguyen quan, co vo, co con.
                .nhanKhau("AT-02-900", "Nguyễn Văn Hai", "", "Nam", "2", "", "", "", "Không",
                        "1910", "12/4/1975", "Làng Đông Ngạc", "")
                .nhanKhau("AT-03-001", "Nguyễn Văn Ba", "", "Nam", "3", "AT-02-900", "", "",
                        "Không", "1940", "3/10/1995")
                // Ba dau: DE TRONG o Doi, dung quy uoc tam thoi cho toi khi Hoi dong chot.
                .nhanKhau("AT-02-101", "Lê Thị Dâu", "", "Nữ", "", "", "", "", "Không",
                        "1912", "6/6/1980")
                .honPhoi("AT-02-900", "AT-02-101", "1")
                .build();
    }

    /**
     * Hai dòng trùng tên, trùng ngày giỗ, <b>cùng một người cha</b>.
     *
     * <p>Khác {@link #tepCoHaiDongTrungNhau()} ở đúng một điểm có ý nghĩa: cả hai dòng đều có mã
     * cha, nên không dòng nào bị bộ soát trước-khi-ghi coi là dâu/rể có khai Đời
     * ({@code IMP_UNDECIDED_INLAW_DOI}). Nhờ vậy ca "để riêng" đo được đúng thứ nó định đo — cả
     * hai cùng vào phả — thay vì dừng ở một câu hỏi Hội đồng chưa chốt.</p>
     */
    private static byte[] tepHaiDongTrungNhauCungCha() {
        return ImportWorkbooks.builder()
                .nhanKhau("AT-01-001", "Nguyễn Văn Cẩn", "", "Nam", "1", "", "", "", "Không",
                        "1880", "20/11/1945")
                .nhanKhau("AT-02-001", "Nguyễn Văn Hai", "", "Nam", "2", "AT-01-001", "", "",
                        "Không", "1910", "12/4/1975")
                .nhanKhau("AT-02-900", "Nguyễn Văn Hai", "", "Nam", "2", "AT-01-001", "", "",
                        "Không", "1910", "12/4/1975")
                .build();
    }

    /** Cặp duy nhất của lô — mọi ca ở đây cố ý dựng đúng một cặp để phép khẳng định đọc được. */
    private JsonNode capDuyNhat(UUID batchId) throws Exception {
        JsonNode caps = doc(get(BASE + "/batches/" + batchId + "/duplicates"), 200);
        assertThat(caps).as("lo %s phai co dung mot cap nghi trung", batchId).hasSize(1);
        return caps.get(0);
    }

    private JsonNode quyet(UUID batchId, UUID pairId, String quyetDinh, String ghiChu)
            throws Exception {
        return JSON.readTree(thanQuyet(batchId, pairId, quyetDinh, ghiChu));
    }

    private String thanQuyet(UUID batchId, UUID pairId, String quyetDinh) throws Exception {
        return thanQuyet(batchId, pairId, quyetDinh, null);
    }

    private String thanQuyet(UUID batchId, UUID pairId, String quyetDinh, String ghiChu)
            throws Exception {
        String than = ghiChu == null
                ? "{\"decision\":\"" + quyetDinh + "\"}"
                : "{\"decision\":\"" + quyetDinh + "\",\"note\":\"" + ghiChu + "\"}";
        MvcResult ketQua = mockMvc.perform(post(BASE + "/batches/" + batchId + "/duplicates/"
                        + pairId + "/decision")
                .contentType(MediaType.APPLICATION_JSON).content(than)
                .with(truongChiAt())).andReturn();
        String phanHoi = ketQua.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(ketQua.getResponse().getStatus()).as("than phan hoi: %s", phanHoi).isEqualTo(200);
        return phanHoi;
    }

    /**
     * Gieo một cụ <b>đã có sẵn trong phả</b>, nhập tay từ trước đợt nhập liệu.
     *
     * <p>Cố ý không qua đường Excel: cụ vì thế <b>không có mã ngoài nào</b>, đúng như phần lớn
     * nhân khẩu được nhập tay trước khi Trưởng chi nộp cuốn sổ đầu tiên — và đó chính là tình
     * huống sinh ra cặp nghi trùng với phả.</p>
     */
    private UUID gieoCuDaKhuat(String hoTen, int namSinh, int doi, int ngayAm, int thangAm,
                               int namAm) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO person (id, gender, generation, birth_solar, is_alive,"
                        + " primary_branch_id, lineage_status, attributes, death_lunar)"
                        + " VALUES (?, 'MALE', ?, ?, FALSE, ?, 'NORMAL', '{}'::jsonb,"
                        + " CAST(? AS jsonb))",
                id, doi, java.sql.Date.valueOf(java.time.LocalDate.of(namSinh, 3, 20)), chiAt,
                "{\"day\":" + ngayAm + ",\"month\":" + thangAm + ",\"year\":" + namAm
                        + ",\"leap\":false}");
        jdbc.update("INSERT INTO person_name (id, person_id, name_type, full_name, is_primary)"
                + " VALUES (?, ?, 'THUONG_GOI', ?, TRUE)", UUID.randomUUID(), id, hoTen);
        graph.createPersonNode(id, "MALE", doi);
        return id;
    }

    /** Một người <b>còn sống</b> có đủ dữ liệu Tầng 2 và Tầng 3 để bộ lọc có gì mà giấu. */
    private UUID gieoNguoiConSong(String hoTen, int namSinh, int doi) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO person (id, gender, generation, birth_solar, is_alive,"
                        + " native_place, primary_branch_id, lineage_status, attributes)"
                        + " VALUES (?, 'MALE', ?, ?, TRUE, 'Bac Ninh', ?, 'NORMAL', CAST(? AS jsonb))",
                id, doi, java.sql.Date.valueOf(java.time.LocalDate.of(namSinh, 3, 20)), chiAt,
                "{\"_profile\":{\"occupation\":\"Giao vien\",\"contact\":{\"phone\":\"0900000001\","
                        + "\"email\":\"nguoi@example.com\"}}}");
        jdbc.update("INSERT INTO person_name (id, person_id, name_type, full_name, is_primary)"
                + " VALUES (?, ?, 'THUONG_GOI', ?, TRUE)", UUID.randomUUID(), id, hoTen);
        graph.createPersonNode(id, "MALE", doi);
        return id;
    }

    private UUID personCua(String externalCode) {
        return jdbc.queryForObject("""
                SELECT person_id FROM person_external_ref
                 WHERE code_system = 'EXCEL_MA' AND branch_id = ? AND external_code = ?
                """, UUID.class, chiAt, externalCode);
    }

    private UUID appUserCua(String keycloakSub) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE keycloak_sub = ?", UUID.class,
                keycloakSub);
    }

    private static List<JsonNode> list(JsonNode array) {
        java.util.List<JsonNode> ketQua = new java.util.ArrayList<>();
        array.forEach(ketQua::add);
        return ketQua;
    }

    private static RequestPostProcessor truongChiAt() {
        return jwt().jwt(builder -> builder.subject("sub-truong-chi-at")
                        .claim("preferred_username", "sub-truong-chi-at"))
                .authorities(new SimpleGrantedAuthority("ROLE_BRANCH_HEAD"));
    }

    /** Chờ luồng nền ghi xong — bước ghi cố ý không chạy trên luồng web. */
    private JsonNode doiGhiXong(UUID batchId) throws Exception {
        long han = System.currentTimeMillis() + 60_000;
        JsonNode tienDo = null;
        while (System.currentTimeMillis() < han) {
            tienDo = doc(get(BASE + "/batches/" + batchId + "/commit-progress"), 200);
            String phase = tienDo.get("phase").asText();
            if ("DONE".equals(phase) || "FAILED".equals(phase)) {
                assertThat(phase).as("ly do: %s", tienDo.path("failureMessage").asText())
                        .isEqualTo("DONE");
                return tienDo;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Ghi lo " + batchId + " khong xong trong 60 giay: " + tienDo);
    }

    private JsonNode taiLen(byte[] noiDung, String tenTep) throws Exception {
        return taiLen(noiDung, tenTep, false);
    }

    /**
     * @param force bỏ qua chốt bấm-hai-lần. Chốt ấy chặn việc ghi lại <b>đúng một tệp đã ghi</b>,
     *        nên nó chặn cả phép thử "tải lại nguyên tệp cũ xem có sinh người trùng không" — đúng
     *        phép thử mà khoá bất biến sinh ra để vượt qua.
     */
    private JsonNode taiLen(byte[] noiDung, String tenTep, boolean force) throws Exception {
        MvcResult ketQua = mockMvc.perform(multipart(BASE + "/batches")
                        .file(new MockMultipartFile("file", tenTep,
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                noiDung))
                        .param("branchId", chiAt.toString())
                        .param("force", String.valueOf(force))
                        .with(truongChiAt()))
                .andReturn();
        assertThat(ketQua.getResponse().getStatus())
                .as("than phan hoi: %s",
                        ketQua.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(201);
        return doc(ketQua);
    }

    private JsonNode doc(MockHttpServletRequestBuilder request, int mongDoi) throws Exception {
        MvcResult ketQua = mockMvc.perform(request.with(truongChiAt())).andReturn();
        assertThat(ketQua.getResponse().getStatus())
                .as("than phan hoi: %s",
                        ketQua.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(mongDoi);
        return doc(ketQua);
    }

    private static JsonNode doc(MvcResult ketQua) throws Exception {
        return JSON.readTree(ketQua.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
