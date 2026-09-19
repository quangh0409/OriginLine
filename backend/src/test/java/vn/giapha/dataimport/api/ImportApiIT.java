package vn.giapha.dataimport.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
import vn.giapha.dataimport.domain.ImportLimits;
import vn.giapha.integration.AbstractIntegrationTest;

/**
 * Tầng REST của đường ống nhập liệu, chạy qua <b>toàn bộ chuỗi HTTP thật</b> (Spring Security +
 * MVC) trên PostgreSQL + Apache AGE thật.
 *
 * <h2>Bốn câu hỏi mà lớp test này trả lời</h2>
 * <ol>
 *   <li><b>Khu vực chờ có rò sang phả không?</b> Đếm {@code person}, {@code relationship} và số
 *       đỉnh AGE trước/sau — không kiểm bằng lời hứa.</li>
 *   <li><b>Cổng duyệt có thật sự chặn không?</b> Bấm duyệt khi còn lỗi chặn phải là {@code 422},
 *       kể cả khi người gọi bỏ qua giao diện và gõ thẳng {@code curl}. Nút bị ẩn không phải một
 *       phép kiểm.</li>
 *   <li><b>Phạm vi chi có phải hạng nhất không?</b> Trưởng chi Ất nộp tệp cho chi Giáp phải là
 *       {@code 403 BRANCH_SCOPE_VIOLATION}, không phải {@code 403 FORBIDDEN} chung chung: hai
 *       tình huống dẫn tới hai việc khác nhau của người dùng.</li>
 *   <li><b>Có mẩu dữ liệu nào của người còn sống rời khỏi tiến trình qua bề mặt nhập liệu
 *       không?</b> Ca cuối soi <b>toàn bộ thân phản hồi</b>, không soi một trường cụ thể — tên,
 *       năm sinh, số điện thoại của người còn sống đều không được xuất hiện ở bất kỳ đâu, kể cả
 *       trong một trường phụ mà người viết code quên mất.</li>
 * </ol>
 */
@DisplayName("REST nhập liệu hàng loạt — cổng duyệt, phạm vi chi, và rò rỉ riêng tư")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class ImportApiIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE = "/api/v1/import";
    private static final String TEN_TRUONG_CHI_AT = "Nguyễn Văn Đức";
    private static final String TEN_TRUONG_CHI_GIAP = "Nguyễn Thị Mai";

    @Autowired
    private MockMvc mockMvc;

    private UUID chiAt;
    private UUID chiGiap;

    @BeforeEach
    void setUpDongHo() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");

        // Truong chi At: co vai BRANCH_HEAD va CO phan cong tren dung chi At. Hai nua cua phan
        // quyen — thieu nua thu hai thi token noi duoc nguoi do la ai nhung khong noi duoc ho quan
        // nhanh nao.
        UUID appUser = insertAppUser("sub-truong-chi-at", null);
        datTen(appUser, TEN_TRUONG_CHI_AT);
        assignBranchRole(appUser, "BRANCH_HEAD", chiAt);

        // Chi Giap CUNG co nguoi phu trach. Khong co thi bai kiem "khong phat coordinatorName cho
        // chi ngoai pham vi" se xanh vi khong co gi de phat, chu khong phai vi phep cat chay.
        UUID truongChiGiap = insertAppUser("sub-truong-chi-giap", null);
        datTen(truongChiGiap, TEN_TRUONG_CHI_GIAP);
        assignBranchRole(truongChiGiap, "BRANCH_HEAD", chiGiap);

        authenticateAs("sub-truong-chi-at", "BRANCH_HEAD");
    }

    /** Tên người phụ trách là tên một người <b>còn sống</b> — đó là lý do nó bị cắt theo phạm vi. */
    private void datTen(UUID appUserId, String hoTen) {
        jdbc.update("UPDATE app_user SET display_name = ? WHERE id = ?", hoTen, appUserId);
    }

    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Tải lên tệp đầy lỗi → xem được lỗi → bấm duyệt bị chặn 422, và phả không đổi")
    void tepDayLoi_xemLoi_roiDuyetBiChan() throws Exception {
        int personTruoc = countRows("person");
        int relTruoc = countRows("relationship");
        int dinhTruoc = graphNodeCount();

        JsonNode lo = taiLen(chiAt, tepDayLoi(), "chi-at-day-loi.xlsx");

        // --- Lo dung lai o FAILED, va chinh backend noi ro nut duyet dong ---
        assertThat(lo.get("status").asText()).isEqualTo("FAILED");
        assertThat(lo.get("blockingCount").asInt()).isPositive();
        assertThat(lo.get("canApprove").asBoolean()).isFalse();
        assertThat(lo.get("branchPath").asText()).isEqualTo("goc.chi_at");

        UUID batchId = UUID.fromString(lo.get("id").asText());

        // --- Danh sach loi chan doc duoc, va mang du du kien de SUA ---
        JsonNode loiChan = doc(get(BASE + "/batches/" + batchId + "/issues")
                .param("severity", "BLOCKING"), 200);
        assertThat(loiChan.isArray()).isTrue();
        assertThat(maLoi(loiChan)).contains("IMP_PARENT_NOT_FOUND", "IMP_CYCLE", "IMP_DUP_CODE");

        // Goi y ma gan giong: chi may chu tinh duoc, vi chi may chu co ca tep lan
        // person_external_ref trong tay.
        JsonNode maChaKhongThay = timTheoMa(loiChan, "IMP_PARENT_NOT_FOUND");
        assertThat(maChaKhongThay.get("context").get("goiY").isArray()).isTrue();
        assertThat(maChaKhongThay.get("externalCode").asText()).isEqualTo("AT-02-001");

        // Chuoi vong lap phai DONG KIN: phan tu dau va phan tu cuoi trung nhau.
        JsonNode vongLap = timTheoMa(loiChan, "IMP_CYCLE").get("context").get("chuoi");
        assertThat(vongLap.size()).isGreaterThanOrEqualTo(3);
        assertThat(vongLap.get(0).asText()).isEqualTo(vongLap.get(vongLap.size() - 1).asText());

        // --- Cong duyet chan lai, du giao dien da an nut ---
        JsonNode problem = doc(post(BASE + "/batches/" + batchId + "/commit"), 422);
        assertThat(problem.get("code").asText()).isEqualTo("IMP_BLOCKING_ISSUES_PRESENT");

        // --- BAT BIEN: ba con so KHONG DOI ---
        assertThat(countRows("person")).isEqualTo(personTruoc);
        assertThat(countRows("relationship")).isEqualTo(relTruoc);
        assertThat(graphNodeCount()).isEqualTo(dinhTruoc);
        // ...nhung du lieu VAN o khu vuc cho, de nguoi nhap doi soat.
        assertThat(countRows("import_person_row")).isPositive();
    }

    @Test
    @DisplayName("Trưởng chi Ất nộp tệp cho chi Giáp → 403 BRANCH_SCOPE_VIOLATION, không phải FORBIDDEN")
    void nopSangChiKhac_403BranchScopeViolation() throws Exception {
        MvcResult ketQua = mockMvc.perform(multipart(BASE + "/batches")
                        .file(new MockMultipartFile("file", "chi-giap.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                tepSach()))
                        .param("branchId", chiGiap.toString())
                        .with(truongChiAt()))
                .andReturn();

        assertThat(ketQua.getResponse().getStatus()).isEqualTo(403);
        JsonNode problem = doc(ketQua);
        assertThat(problem.get("code").asText()).isEqualTo("BRANCH_SCOPE_VIOLATION");
        // Khong mot dong nao cua chi Giap duoc dung: tu choi TRUOC khi doc tep.
        assertThat(countRows("import_batch")).isZero();
    }

    @Test
    @DisplayName("Lô đã bị lô mới thay thế (SUPERSEDED) → duyệt và kiểm lại đều 422 IMP_BATCH_CLOSED")
    void loDaDong_422BatchClosed() throws Exception {
        JsonNode loCu = taiLen(chiAt, tepSach(), "lan-1.xlsx");
        UUID loCuId = UUID.fromString(loCu.get("id").asText());

        // Lan tai thu hai cho CUNG MOT CHI: lo cu chuyen SUPERSEDED ngay trong giao dich ay.
        JsonNode loMoi = taiLen(chiAt, tepSachKhac(), "lan-2.xlsx");
        assertThat(loMoi.get("id").asText()).isNotEqualTo(loCuId.toString());

        JsonNode loCuBayGio = doc(get(BASE + "/batches/" + loCuId), 200);
        assertThat(loCuBayGio.get("status").asText()).isEqualTo("SUPERSEDED");

        assertThat(doc(post(BASE + "/batches/" + loCuId + "/commit"), 422).get("code").asText())
                .isEqualTo("IMP_BATCH_CLOSED");
        assertThat(doc(post(BASE + "/batches/" + loCuId + "/validate"), 422).get("code").asText())
                .isEqualTo("IMP_BATCH_CLOSED");
    }

    /**
     * Ca then chốt về riêng tư.
     *
     * <p>Dựng đúng tình huống nguy hiểm nhất: một người <b>còn sống</b> đã có trong phả, và một
     * dòng trong tệp trùng tên với họ (trùng sau khi bỏ dấu — đúng kiểu người nhập lần hai hay gõ).
     * Bộ dò sẽ kêu, và câu thông báo <i>mà bộ kiểm soạn ra và lưu vào cơ sở dữ liệu</i> có chứa tên
     * thật của người ấy. Bài kiểm này ghim rằng nó <b>không rời khỏi tiến trình</b>.</p>
     */
    @Test
    @DisplayName("Nghi trùng với người CÒN SỐNG: không tên, không năm sinh, không liên hệ nào lọt ra")
    void nghiTrungVoiNguoiConSong_khongRoRiMotTruongNao() throws Exception {
        String tenThatCoDau = "Nguyễn Văn Tuân";
        String tenTrongTep = "Nguyen Van Tuan";
        UUID nguoiConSong = gieoNguoiConSong(tenThatCoDau, 1975, 3, chiAt);

        byte[] tep = ImportWorkbooks.builder()
                .nhanKhau("AT-03-001", tenTrongTep, "", "Nam", "3", "", "", "", "Có", "1975", "")
                .build();
        JsonNode lo = taiLen(chiAt, tep, "nghi-trung.xlsx");
        UUID batchId = UUID.fromString(lo.get("id").asText());

        assertThat(lo.get("suspectDuplicateCount").asInt())
                .as("bo do phai keu, neu khong thi bai kiem nay khong kiem gi ca")
                .isPositive();

        String thanIssues = than(get(BASE + "/batches/" + batchId + "/issues"), 200);
        String thanDuplicates = than(get(BASE + "/batches/" + batchId + "/duplicates"), 200);

        for (String than : new String[] {thanIssues, thanDuplicates}) {
            assertThat(than)
                    .as("ten that co dau cua nguoi con song khong duoc xuat hien o bat ky dau")
                    .doesNotContain(tenThatCoDau);
            assertThat(than).doesNotContain("0900000001");     // so dien thoai Tang 3
            assertThat(than).doesNotContain("nguoi@example.com");
            assertThat(than).doesNotContain("Giao vien");       // nghe nghiep Tang 2
            // Ten trong TEP thi PHAI co: do la thu chinh nguoi nhap vua go.
            assertThat(than).contains(tenTrongTep);
        }

        // --- Cau truc: chi con khoa, evidence rong ---
        JsonNode capNghiTrung = doc(get(BASE + "/batches/" + batchId + "/duplicates"), 200);
        assertThat(capNghiTrung).isNotEmpty();
        JsonNode cap = capNghiTrung.get(0);
        JsonNode benTrongPha = cap.get("existing");
        assertThat(benTrongPha.get("source").asText()).isEqualTo("TREE");
        assertThat(benTrongPha.get("personId").asText()).isEqualTo(nguoiConSong.toString());
        assertThat(benTrongPha.has("displayName")).isFalse();
        assertThat(benTrongPha.has("birthYear")).isFalse();
        assertThat(benTrongPha.has("generation")).isFalse();
        // KHONG gui sang mot o rong: mot o rong vua ro ri su ton tai cua du lieu bi giau, vua dan
        // nguoi doi chieu toi mot quyet dinh gop sai.
        assertThat(cap.get("evidence")).isEmpty();
        assertThat(cap.has("hint")).isFalse();

        // --- Va ngu canh may doc duoc cua canh bao cung chi con khoa ---
        JsonNode canhBao = timTheoMa(doc(get(BASE + "/batches/" + batchId + "/issues")
                .param("severity", "WARNING"), 200), "IMP_SUSPECT_DUPLICATE");
        JsonNode nghiNgo = canhBao.get("context").get("nghiNgo").get(0);
        assertThat(nghiNgo.get("nguon").asText()).isEqualTo("TREE");
        assertThat(nghiNgo.get("personId").asText()).isEqualTo(nguoiConSong.toString());
        assertThat(nghiNgo.has("ten")).isFalse();
        assertThat(nghiNgo.has("giaiThich")).isFalse();
    }

    /**
     * <b>Bài kiểm quan trọng nhất của cả lớp này.</b>
     *
     * <p>Đi <b>trọn</b> đường ống trên một lô <i>có cảnh báo</i> — tức là trên một lô <b>thật</b>,
     * vì mọi cuốn gia phả thật đều có cảnh báo: thuỷ tổ thì lần nào cũng sinh
     * {@code IMP_LONE_NODE}. Không một bước nào được đi tắt bằng {@code UPDATE} thẳng vào bảng.
     * Chừng nào bài này còn xanh thì đường ống <i>chạy được thật</i>, chứ không phải chạy được
     * trên một tệp mẫu không tồn tại ngoài đời.</p>
     */
    @Test
    @DisplayName("TRỌN ĐƯỜNG: lô có cảnh báo → xác nhận qua API thật → duyệt → vào phả")
    void loCoCanhBao_xacNhanQuaApiThat_roiVaoPha() throws Exception {
        JsonNode lo = taiLen(chiAt, tepSach(), "sach.xlsx");
        UUID batchId = UUID.fromString(lo.get("id").asText());

        assertThat(lo.get("status").asText()).isEqualTo("VALIDATED");
        assertThat(lo.get("blockingCount").asInt()).isZero();
        assertThat(lo.get("warningCount").asInt())
                .as("mot lo THAT luon co canh bao; neu con so nay bang 0 thi bai kiem khong con"
                        + " kiem dung thu no dinh kiem")
                .isPositive();
        assertThat(lo.get("canApprove").asBoolean()).isFalse();
        assertThat(lo.has("warningsAcknowledgedAt")).isFalse();

        // --- Cong duyet dong lai, va noi dung ly do ---
        assertThat(doc(post(BASE + "/batches/" + batchId + "/commit"), 422).get("code").asText())
                .isEqualTo("IMP_WARNINGS_NOT_ACKNOWLEDGED");

        // --- Xac nhan qua API THAT, khong phai UPDATE thang vao bang ---
        JsonNode daXacNhan = doc(post(BASE + "/batches/" + batchId + "/acknowledge-warnings")
                .contentType(MediaType.APPLICATION_JSON).content("{\"acknowledged\":true}"), 200);
        assertThat(daXacNhan.get("warningsAcknowledgedAt").asText()).isNotBlank();
        assertThat(daXacNhan.get("warningsAcknowledgedBy").asText())
                .as("tick nay mo khoa nut ghi ca tram nguoi vao pha, nen no phai co CHU")
                .isEqualTo(appUserCua("sub-truong-chi-at").toString());
        assertThat(daXacNhan.get("canApprove").asBoolean()).isTrue();

        // --- Roi moi duyet ---
        int personTruoc = countRows("person");
        JsonNode chapNhan = doc(post(BASE + "/batches/" + batchId + "/commit"), 202);
        // Than phan hoi la trang thai TAI THOI DIEM BAN GIAO, chua phai ket qua ghi.
        assertThat(chapNhan.get("id").asText()).isEqualTo(batchId.toString());

        JsonNode tienDo = doiGhiXong(batchId);
        assertThat(tienDo.get("estimated").asBoolean())
                .as("con so nay dem ngoai giao dich ghi nen no la uoc luong THEO BAN CHAT")
                .isTrue();
        assertThat(tienDo.get("phase").asText()).isEqualTo("DONE");
        assertThat(tienDo.get("status").asText()).isEqualTo("COMMITTED");
        assertThat(countRows("person")).isEqualTo(personTruoc + 1);

        JsonNode sauKhiGhi = doc(get(BASE + "/batches/" + batchId), 200);
        assertThat(sauKhiGhi.get("committedBy").asText())
                .isEqualTo(appUserCua("sub-truong-chi-at").toString());
        assertThat(sauKhiGhi.get("version").asLong()).isPositive();
    }

    /**
     * Xác nhận cũ <b>không</b> được sống sót qua một lần kiểm sinh cảnh báo mới.
     *
     * <p>Nếu nó sống sót thì người duyệt đang xác nhận những dòng họ chưa từng nhìn thấy — và tệ
     * hơn cả, hệ thống ghi điều đó <i>nhân danh họ</i>. Đây là loại sai lệch không bao giờ tự lộ
     * ra, vì mọi thứ trên màn hình vẫn xanh.</p>
     */
    @Test
    @DisplayName("Kiểm lại sinh cảnh báo MỚI → xác nhận cũ mất hiệu lực, nút duyệt đóng lại")
    void kiemLaiSinhCanhBaoMoi_xacNhanCuMatHieuLuc() throws Exception {
        byte[] tep = ImportWorkbooks.builder()
                .nhanKhau("AT-03-001", "Nguyen Van Tuan", "", "Nam", "3", "", "", "", "Không",
                        "1900", "10/5/1960")
                .build();
        JsonNode lo = taiLen(chiAt, tep, "lan-mot.xlsx");
        UUID batchId = UUID.fromString(lo.get("id").asText());
        int canhBaoTruoc = lo.get("warningCount").asInt();
        assertThat(lo.get("suspectDuplicateCount").asInt()).isZero();

        JsonNode daXacNhan = doc(post(BASE + "/batches/" + batchId + "/acknowledge-warnings"), 200);
        assertThat(daXacNhan.get("canApprove").asBoolean()).isTrue();

        // --- Mat kia cua cung mot luat: kiem lai ma canh bao Y NGUYEN thi KHONG bat tick lai ---
        // Bat tick lai mot danh sach khong doi la cach chac chan de lan thu ba nguoi ta tick ma
        // khong doc, va tu luc do moi canh bao — ke ca canh bao dung — deu vo gia tri.
        assertThat(doc(post(BASE + "/batches/" + batchId + "/validate"), 200)
                .get("canApprove").asBoolean())
                .as("cung mot tap canh bao thi xac nhan cu van con hieu luc")
                .isTrue();

        // --- Giua hai lan kiem, mot nguoi KHAC them mot ho so vao pha ---
        gieoNguoiDaKhuat("Nguyễn Văn Tuân", 1900, 3, chiAt);

        JsonNode kiemLai = doc(post(BASE + "/batches/" + batchId + "/validate"), 200);
        assertThat(kiemLai.get("warningCount").asInt())
                .as("lan kiem nay phai sinh them canh bao, neu khong thi ca test chua dung vao"
                        + " dung tinh huong no dinh dung")
                .isGreaterThan(canhBaoTruoc);

        // Dau vet lich su VAN con — da co mot nguoi bam xac nhan, do la su that. Nhung no khong
        // con HIEU LUC, va do la hai chuyen khac nhau.
        assertThat(kiemLai.get("warningsAcknowledgedAt").asText()).isNotBlank();
        assertThat(kiemLai.get("canApprove").asBoolean()).isFalse();

        assertThat(doc(post(BASE + "/batches/" + batchId + "/commit"), 422).get("code").asText())
                .as("xac nhan cu KHONG duoc tinh cho tap canh bao moi")
                .isEqualTo("IMP_WARNINGS_NOT_ACKNOWLEDGED");

        // --- Xac nhan lai thi cong duyet di tiep, va dung lai o mot ly do KHAC ---
        doc(post(BASE + "/batches/" + batchId + "/acknowledge-warnings"), 200);
        assertThat(doc(post(BASE + "/batches/" + batchId + "/commit"), 422).get("code").asText())
                .as("lan xac nhan moi DA co hieu luc: cong duyet da di qua muc canh bao")
                .isEqualTo("IMP_DUPLICATES_UNDECIDED");
    }

    // -------------------------------------------------------------------------------------
    // Trần tải lên
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Tệp .xlsx hợp lệ nặng hơn 2 MB đi qua được — trần thật là 10 MB, không phải 1 MB")
    void tepHaiMegaDiQuaDuoc() throws Exception {
        byte[] tep = ImportWorkbooks.builder()
                .nhanKhau("AT-01-001", "Nguyễn Phúc Thuỷ Tổ", "", "Nam", "1", "", "", "", "Không",
                        "1880", "15/8/1950")
                .demChoNang(1_800)
                .build();

        assertThat((long) tep.length)
                .as("tep dem phai that su vuot 2 MB, neu khong thi bai kiem nay khong kiem gi ca")
                .isGreaterThan(2L * 1024 * 1024)
                .isLessThan(ImportLimits.MAX_FILE_BYTES);

        JsonNode lo = taiLen(chiAt, tep, "chi-at-nang.xlsx");
        assertThat(lo.get("fileSizeBytes").asLong()).isEqualTo(tep.length);
        assertThat(lo.get("personRowCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("Tệp vượt ImportLimits.MAX_FILE_BYTES → 413 IMP_FILE_TOO_LARGE, không phải 500")
    void tepVuotTran_413() throws Exception {
        byte[] quaNang = new byte[(int) ImportLimits.MAX_FILE_BYTES + 1];
        // Chu ky PK de no la mot .xlsx "that" o lop nhan dang — de bai kiem bat dung phep kiem
        // KICH THUOC chu khong vo tinh bat phep kiem dinh dang.
        quaNang[0] = 'P';
        quaNang[1] = 'K';
        quaNang[2] = 3;
        quaNang[3] = 4;

        MvcResult ketQua = mockMvc.perform(multipart(BASE + "/batches")
                        .file(new MockMultipartFile("file", "qua-nang.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                quaNang))
                        .param("branchId", chiAt.toString())
                        .with(truongChiAt()))
                .andReturn();

        assertThat(ketQua.getResponse().getStatus()).isEqualTo(413);
        assertThat(doc(ketQua).get("code").asText()).isEqualTo("IMP_FILE_TOO_LARGE");
        assertThat(countRows("import_batch")).isZero();
    }

    // -------------------------------------------------------------------------------------
    // Danh sách lô và màn tiến độ
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("GET /batches trả các lô trong phạm vi, mới nhất trước; chi khác thì 403")
    void danhSachLoTheoPhamVi() throws Exception {
        taiLen(chiAt, tepSach(), "lan-1.xlsx");
        JsonNode moiNhat = taiLen(chiAt, tepSachKhac(), "lan-2.xlsx");

        JsonNode danhSach = doc(get(BASE + "/batches"), 200);
        assertThat(danhSach.size()).isEqualTo(2);
        assertThat(danhSach.get(0).get("id").asText())
                .as("moi nhat truoc: nguoi nhap quay lai hom sau can thay dung lo dang lam do")
                .isEqualTo(moiNhat.get("id").asText());
        assertThat(danhSach.get(0).get("branchPath").asText()).isEqualTo("goc.chi_at");

        assertThat(doc(get(BASE + "/batches").param("status", "SUPERSEDED"), 200).size())
                .isEqualTo(1);
        assertThat(doc(get(BASE + "/batches").param("branchId", chiAt.toString()), 200).size())
                .isEqualTo(2);

        // Chi ngoai pham vi: 403, KHONG phai mot danh sach rong. "Khong co lo nao" va "chi nay
        // khong phai cua ban" la hai cau tra loi khac nhau han.
        MvcResult chiKhac = mockMvc.perform(get(BASE + "/batches")
                .param("branchId", chiGiap.toString()).with(truongChiAt())).andReturn();
        assertThat(chiKhac.getResponse().getStatus()).isEqualTo(403);
        assertThat(doc(chiKhac).get("code").asText()).isEqualTo("BRANCH_SCOPE_VIOLATION");
    }

    @Test
    @DisplayName("/progress: chi ngoài phạm vi KHÔNG mang coordinatorName — bỏ hẳn, không null hoá")
    void tienDoCatDungTruongVoiChiNgoaiPhamVi() throws Exception {
        taiLen(chiAt, tepSach(), "chi-at.xlsx");

        String than = than(get(BASE + "/progress"), 200);
        assertThat(than)
                .as("ten cua Truong chi Giap la ten mot NGUOI CON SONG; no khong duoc roi khoi"
                        + " tien trinh qua man tien do")
                .doesNotContain(TEN_TRUONG_CHI_GIAP);

        JsonNode tienDo = doc(get(BASE + "/progress"), 200);
        assertThat(tienDo.size()).as("tra MOI chi, ke ca chi ngoai pham vi").isEqualTo(3);

        JsonNode cuaMinh = timTheoPath(tienDo, "goc.chi_at");
        assertThat(cuaMinh.get("coordinatorName").asText()).isEqualTo(TEN_TRUONG_CHI_AT);
        assertThat(cuaMinh.get("stage").asText()).isEqualTo("RECONCILING");
        assertThat(cuaMinh.has("openBatch")).isTrue();
        assertThat(cuaMinh.get("blockingCount").asInt()).isZero();
        assertThat(cuaMinh.get("warningCount").asInt()).isPositive();

        JsonNode chiKhac = timTheoPath(tienDo, "goc.chi_giap");
        // BO HAN khoi JSON, khong null hoa: mot khoa null van noi cho nguoi doc biet truong ay ton
        // tai va o dau do co gia tri.
        for (String truong : new String[] {"coordinatorName", "openBatch", "blockingCount",
                "warningCount", "undecidedDuplicateCount"}) {
            assertThat(chiKhac.has(truong)).as("truong %s cua chi ngoai pham vi", truong).isFalse();
        }
        // ...nhung so lieu tong hop thi VAN co: man nay ton tai de ca ho biet con thieu gi.
        assertThat(chiKhac.get("branchName").asText()).isEqualTo("Chi Giáp");
        assertThat(chiKhac.get("stage").asText()).isEqualTo("NOT_STARTED");
        assertThat(chiKhac.get("personsInTree").asInt()).isZero();
        assertThat(chiKhac.get("missingGioCount").asInt()).isZero();
    }

    @Test
    @DisplayName("/progress: tài khoản chưa khởi tạo nhận 403, khách nhận 401 — không ai nhận danh sách rỗng")
    void tienDoKhongBaoGioTraDanhSachRong() throws Exception {
        MvcResult chuaKhoiTao = mockMvc.perform(get(BASE + "/progress")
                .with(jwt().jwt(b -> b.subject("sub-chua-co-app-user"))
                        .authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))).andReturn();
        assertThat(chuaKhoiTao.getResponse().getStatus()).isEqualTo(403);
        assertThat(doc(chuaKhoiTao).get("code").asText()).isEqualTo("ACCOUNT_NOT_PROVISIONED");

        // Khach vang lai khong co token thi dung o chuoi filter bao mat. Diem chung voi ca tren
        // moi la dieu dang ghim: KHONG AI nhan ve mot mang rong — mang rong noi doi rang dong ho
        // nay khong co chi nao, va dong thoi xac nhan rang man hinh nay co that.
        assertThat(mockMvc.perform(get(BASE + "/progress")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("Mẫu Excel của chi: tải được với chi mình, 403 với chi khác, tên tệp giữ nguyên dấu")
    void mauExcelTheoChi() throws Exception {
        MvcResult cuaMinh = mockMvc.perform(get("/api/v1/admin/branches/" + chiAt
                + "/import-template.xlsx").with(truongChiAt())).andReturn();
        assertThat(cuaMinh.getResponse().getStatus()).isEqualTo(200);
        assertThat(cuaMinh.getResponse().getContentAsByteArray().length).isPositive();
        // Dang RFC 5987 la dang DUY NHAT cho ten chi tieng Viet; thieu no thi ten tep ve tay
        // Truong chi la ban da rung dau.
        assertThat(cuaMinh.getResponse().getHeader("Content-Disposition"))
                .contains("filename*=UTF-8''");

        MvcResult cuaChiKhac = mockMvc.perform(get("/api/v1/admin/branches/" + chiGiap
                + "/import-template.xlsx").with(truongChiAt())).andReturn();
        assertThat(cuaChiKhac.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Ngưỡng nghi trùng do máy chủ công bố, và máy không bao giờ tự gộp")
    void mayChuCongBoNguong() throws Exception {
        JsonNode policy = doc(get(BASE + "/duplicate-policy"), 200);
        assertThat(policy.get("suspectThreshold").asInt()).isEqualTo(70);
        assertThat(policy.get("preselectMergeThreshold").asInt()).isEqualTo(85);
        assertThat(policy.get("autoMerge").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Danh sách chi trả cả chi ngoài phạm vi, nhưng canImport chỉ bật đúng chi được giao")
    void danhSachChi_coCoCanImport() throws Exception {
        JsonNode chis = doc(get(BASE + "/branches"), 200);
        assertThat(chis.size()).isEqualTo(3);
        for (JsonNode chi : chis) {
            boolean laChiAt = "goc.chi_at".equals(chi.get("path").asText());
            assertThat(chi.get("canImport").asBoolean())
                    .as("chi %s", chi.get("path").asText())
                    .isEqualTo(laChiAt);
            // Chua ai tai gi len: khong chi nao co lo dang do.
            assertThat(chi.has("openBatchId")).isFalse();
        }
    }

    @Test
    @DisplayName("Lô đang dở hiện ngay ở /branches — và chỉ hiện cho chi người gọi quản")
    void chiCuaMinhMangTheoLoDangDo() throws Exception {
        JsonNode lo = taiLen(chiAt, tepSach(), "dang-do.xlsx");

        JsonNode chis = doc(get(BASE + "/branches"), 200);
        JsonNode cuaMinh = timTheoPath2(chis, "goc.chi_at");
        assertThat(cuaMinh.get("openBatchId").asText())
                .as("Truong chi quay lai sau hai hom phai thay ngay lo dang doi soat, chu khong"
                        + " phai mot nut 'Tai tep len' sach tron dan ho di nop lo thu hai")
                .isEqualTo(lo.get("id").asText());

        // Chi ngoai pham vi khong mang ma lo nao: mot ma lo la mot lieu tro thang vao khu vuc cho.
        assertThat(timTheoPath2(chis, "goc.chi_giap").has("openBatchId")).isFalse();
    }

    // -------------------------------------------------------------------------------------
    // Tiện ích
    // -------------------------------------------------------------------------------------

    /**
     * Token của Trưởng chi Ất.
     *
     * <p><b>Bẫy đã tốn thời gian một lần rồi:</b> {@code SecurityContextHolder} do
     * {@code authenticateAs} đặt <b>không</b> đi theo request của MockMvc — chuỗi filter bảo mật
     * dựng lại ngữ cảnh cho từng lượt gọi, nên mọi thứ không mang {@code jwt()} đều là 401. Tiền
     * tố {@code ROLE_} là cố ý: nó mô phỏng đúng thứ {@code KeycloakRealmRoleConverter} sinh ra.</p>
     */
    private static RequestPostProcessor truongChiAt() {
        return jwt().jwt(builder -> builder.subject("sub-truong-chi-at")
                        .claim("preferred_username", "sub-truong-chi-at"))
                .authorities(new SimpleGrantedAuthority("ROLE_BRANCH_HEAD"));
    }

    /**
     * Chờ luồng nền ghi xong.
     *
     * <p>Bước ghi cố ý <b>không</b> chạy trên luồng web, nên bài kiểm phải hỏi lại đúng như giao
     * diện hỏi. Vòng lặp có trần thời gian chứ không chờ vô hạn: một bài test treo là bài test
     * không ai đọc kết quả.</p>
     */
    private JsonNode doiGhiXong(UUID batchId) throws Exception {
        long han = System.currentTimeMillis() + 60_000;
        JsonNode tienDo = null;
        while (System.currentTimeMillis() < han) {
            tienDo = doc(get(BASE + "/batches/" + batchId + "/commit-progress"), 200);
            String phase = tienDo.get("phase").asText();
            if ("DONE".equals(phase) || "FAILED".equals(phase)) {
                return tienDo;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Ghi lo " + batchId + " khong xong trong 60 giay; tien do cuoi: "
                + tienDo);
    }

    private JsonNode taiLen(UUID branchId, byte[] noiDung, String tenTep) throws Exception {
        MvcResult ketQua = mockMvc.perform(multipart(BASE + "/batches")
                        .file(new MockMultipartFile("file", tenTep,
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                noiDung))
                        .param("branchId", branchId.toString())
                        .with(truongChiAt()))
                .andReturn();
        assertThat(ketQua.getResponse().getStatus())
                .as("than phan hoi: %s", ketQua.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(201);
        return doc(ketQua);
    }

    private JsonNode doc(MockHttpServletRequestBuilder request, int mongDoi) throws Exception {
        MvcResult ketQua = mockMvc.perform(request.with(truongChiAt())).andReturn();
        assertThat(ketQua.getResponse().getStatus())
                .as("than phan hoi: %s", ketQua.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(mongDoi);
        return doc(ketQua);
    }

    private String than(MockHttpServletRequestBuilder request, int mongDoi) throws Exception {
        MvcResult ketQua = mockMvc.perform(request.with(truongChiAt())).andReturn();
        assertThat(ketQua.getResponse().getStatus()).isEqualTo(mongDoi);
        return ketQua.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static JsonNode doc(MvcResult ketQua) throws Exception {
        return JSON.readTree(ketQua.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static java.util.List<String> maLoi(JsonNode danhSach) {
        java.util.List<String> ma = new java.util.ArrayList<>();
        danhSach.forEach(n -> ma.add(n.get("code").asText()));
        return ma;
    }

    private UUID appUserCua(String keycloakSub) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE keycloak_sub = ?", UUID.class,
                keycloakSub);
    }

    /** Như {@link #timTheoPath} nhưng cho {@code /branches}, nơi khoá đường dẫn tên là {@code path}. */
    private static JsonNode timTheoPath2(JsonNode danhSach, String path) {
        for (JsonNode n : danhSach) {
            if (path.equals(n.get("path").asText())) {
                return n;
            }
        }
        throw new AssertionError("Khong tim thay chi " + path + " trong " + danhSach);
    }

    private static JsonNode timTheoPath(JsonNode danhSach, String path) {
        for (JsonNode n : danhSach) {
            if (path.equals(n.get("branchPath").asText())) {
                return n;
            }
        }
        throw new AssertionError("Khong tim thay chi " + path + " trong " + danhSach);
    }

    /**
     * Gieo một nhân khẩu <b>đã khuất</b> làm mồi cho bộ dò trùng.
     *
     * <p>Đã khuất chứ không còn sống là cố ý: ca dùng nó chỉ cần một cảnh báo <i>mới</i> xuất hiện
     * ở lần kiểm thứ hai, không cần kéo cả bộ lọc phân tầng riêng tư vào đây. Việc ấy đã có ca
     * riêng canh.</p>
     */
    private UUID gieoNguoiDaKhuat(String hoTen, int namSinh, int doi, UUID branchId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO person (id, gender, generation, birth_solar, is_alive,"
                        + " primary_branch_id, lineage_status, attributes)"
                        + " VALUES (?, 'MALE', ?, ?, FALSE, ?, 'NORMAL', '{}'::jsonb)",
                id, doi, java.sql.Date.valueOf(java.time.LocalDate.of(namSinh, 3, 20)), branchId);
        jdbc.update("INSERT INTO person_name (id, person_id, name_type, full_name, is_primary)"
                + " VALUES (?, ?, 'THUONG_GOI', ?, TRUE)", UUID.randomUUID(), id, hoTen);
        graph.createPersonNode(id, "MALE", doi);
        return id;
    }

    private static JsonNode timTheoMa(JsonNode danhSach, String code) {
        for (JsonNode n : danhSach) {
            if (code.equals(n.get("code").asText())) {
                return n;
            }
        }
        throw new AssertionError("Khong tim thay ma " + code + " trong " + danhSach);
    }

    /**
     * Gieo một nhân khẩu <b>còn sống</b> có đủ dữ liệu Tầng 2 và Tầng 3 để bộ lọc có gì mà giấu.
     *
     * <p>Cố ý dựng bằng SQL thay vì qua use case: ca test này cần một hồ sơ <i>đã nằm sẵn trong
     * phả</i> làm mồi cho bộ dò trùng, chứ không kiểm luồng thêm người.</p>
     */
    private UUID gieoNguoiConSong(String hoTen, int namSinh, int doi, UUID branchId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO person (id, gender, generation, birth_solar, is_alive,"
                        + " native_place, primary_branch_id, lineage_status, attributes)"
                        + " VALUES (?, 'MALE', ?, ?, TRUE, 'Bac Ninh', ?, 'NORMAL', CAST(? AS jsonb))",
                id, doi, java.sql.Date.valueOf(java.time.LocalDate.of(namSinh, 3, 20)), branchId,
                "{\"_profile\":{\"occupation\":\"Giao vien\",\"contact\":{\"phone\":\"0900000001\","
                        + "\"email\":\"nguoi@example.com\"}}}");
        jdbc.update("INSERT INTO person_name (id, person_id, name_type, full_name, is_primary)"
                + " VALUES (?, ?, 'THUONG_GOI', ?, TRUE)", UUID.randomUUID(), id, hoTen);
        graph.createPersonNode(id, "MALE", doi);
        return id;
    }

    /** Tệp mang đủ ba nhóm lỗi chặn hay gặp nhất: mã cha sai, vòng lặp, trùng mã. */
    private static byte[] tepDayLoi() {
        return ImportWorkbooks.builder()
                .nhanKhau("AT-02-001", "Nguyễn Văn Đức", "", "Nam", "2", "AT-01-OO3", "", "",
                        "Không", "1915", "15/8/1945")
                .nhanKhau("AT-01-003", "Nguyễn Văn Gốc", "", "Nam", "1", "", "", "", "Không",
                        "1880", "3/2/1940")
                .nhanKhau("AT-05-012", "Nguyễn Văn E", "", "Nam", "5", "AT-04-003", "", "",
                        "Không", "1950", "2/3/1990")
                .nhanKhau("AT-04-003", "Nguyễn Văn F", "", "Nam", "4", "AT-05-012", "", "",
                        "Không", "1920", "5/6/1980")
                .nhanKhau("AT-09-001", "Nguyễn Văn G", "", "Nam", "9", "", "", "", "Không",
                        "1970", "1/1/2000")
                .nhanKhau("AT-09-001", "Nguyễn Văn H", "", "Nam", "9", "", "", "", "Không",
                        "1971", "2/2/2001")
                .build();
    }

    /** Tệp không có lỗi chặn nào — chỉ một thuỷ tổ đã khuất, có ngày giỗ. */
    private static byte[] tepSach() {
        return ImportWorkbooks.builder()
                .nhanKhau("AT-01-001", "Nguyễn Phúc Thuỷ Tổ", "", "Nam", "1", "", "", "", "Không",
                        "1880", "15/8/1950")
                .build();
    }

    private static byte[] tepSachKhac() {
        return ImportWorkbooks.builder()
                .nhanKhau("AT-01-002", "Nguyễn Phúc Đệ Nhị", "", "Nam", "1", "", "", "", "Không",
                        "1885", "3/9/1955")
                .build();
    }
}
