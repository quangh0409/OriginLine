package vn.giapha.dataimport.application.rule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.port.DuplicateScanPort;

/**
 * Nghi trùng người — <b>cảnh báo, không bao giờ chặn, và không bao giờ tự gộp</b>.
 *
 * <h2>Máy nghi ngờ, người quyết định</h2>
 * Không có ngưỡng điểm nào dẫn tới việc tự gộp hai hồ sơ. Gộp nhầm hai người là loại hỏng nặng
 * nhất trong cả đường ống: nó hợp nhất hai nhánh con cháu, và sau ba mươi ngày thì không hoàn tác
 * được nữa.
 *
 * <h2>Không viết bộ chấm điểm thứ hai</h2>
 * Luật này <b>không chứa một dòng logic chấm điểm nào</b>. Bộ dò đã tồn tại, đã được hiệu chỉnh
 * trên dữ liệu mô phỏng, và có bộ trọng số được chọn để chịu được hai tập quán của dòng họ Việt:
 * cả một đời mang chung một chữ đệm (trùng tên + trùng đời là <b>bình thường</b>, không đủ để kêu),
 * và tục đặt tên con theo tên người anh đã mất (cùng cha cùng tên thì <b>có</b> kêu — một dương
 * tính giả đã biết trước và chấp nhận, vì thà để người đọc bác bỏ một cảnh báo còn hơn để hai
 * người thành một).
 *
 * <p>Bộ dò tự soi cả <b>trong nội bộ tệp</b>: cùng một người được ghi hai lần với hai mã khác nhau
 * là chuyện rất hay xảy ra khi một người đàn ông xuất hiện ở cả trang đời cha lẫn trang đời con
 * trong sổ.</p>
 *
 * <h2>Thông báo soạn bằng KHOÁ, không bằng trường — và vì sao đây là chỗ sửa đúng</h2>
 * Bộ dò quét <b>toàn dòng họ</b>, không chỉ chi đang nhập. Hồ sơ bị nghi hoàn toàn có thể là một
 * người <b>còn sống ở một chi khác</b> mà người nhập liệu của chi này không có quyền biết là tồn
 * tại. Câu thông báo luật này soạn ra <b>được ghi thẳng vào {@code import_issue.message}</b>, nên
 * nhét tên hay năm sinh vào đó là ghi vĩnh viễn dữ liệu người khác vào một bảng mà cả bộ lọc phân
 * tầng riêng tư lẫn phạm vi chi đều không canh. Cắt ở tầng api là muộn: dữ liệu đã nằm trong CSDL
 * rồi, ai truy vấn thẳng bảng vẫn đọc được.
 *
 * <p>Ranh giới:</p>
 * <ul>
 *   <li><b>Dòng trong tệp của chính người nhập</b> — nêu đích danh thoải mái: mã, họ tên, đời. Đó
 *       là thứ họ vừa gõ, giấu đi chỉ làm họ không đối chiếu được.</li>
 *   <li><b>Hồ sơ đã có trong phả</b> — <b>chỉ {@code personId}</b>. Giao diện cầm khoá gọi
 *       {@code GET /api/v1/persons/&#123;id&#125;}, nơi {@code PrivacyTierService} chạy thật và trả
 *       {@code 404} khi người gọi không được biết bản ghi tồn tại. Nhờ vậy luật lọc chỉ có
 *       <b>một bản</b>, ở đúng chỗ của nó.</li>
 * </ul>
 *
 * <h2>Vẫn phải dùng được — giữ mọi thứ KHÔNG thuộc về người kia</h2>
 * "Dòng 12 nghi trùng với một người" là đúng về riêng tư và vô dụng với người nhập liệu. Câu
 * thông báo vì vậy giữ lại: số dòng và họ tên trong tệp của chính họ, <b>điểm nghi ngờ</b>,
 * <b>loại tín hiệu đã khớp</b> (trùng ngày giỗ / trùng họ tên khi bỏ dấu / cùng chi…) và
 * <b>phải làm gì tiếp</b>. Tín hiệu trả lời "vì sao nghi" mà không tiết lộ "người ấy là ai".
 *
 * <p>Nhãn tín hiệu lấy từ {@link DuplicateScanPort.NghiNgo#hint()}. Hợp đồng của trường đó là
 * <b>chỉ nhãn, không giá trị trường</b>, và hợp đồng ấy được ghim ở phía sinh ra nó
 * ({@code DuplicateScorerTest}) chứ không phải phân tích lại chuỗi ở đây.</p>
 */
@Component
@Order(110)
public class SuspectDuplicateRule implements ImportRule {

    /** Bên bị nghi đã có trong phả — chỉ khoá. */
    static final String NGUON_TREE = "TREE";

    /** Bên bị nghi là một dòng khác trong chính tệp này — dữ liệu của chính người nhập. */
    static final String NGUON_FILE = "FILE";

    private final DuplicateScanPort duplicates;

    public SuspectDuplicateRule(DuplicateScanPort duplicates) {
        this.duplicates = duplicates;
    }

    @Override
    public void apply(ValidationContext ctx) {
        List<PersonRow> rows = ctx.personRows().stream()
                .filter(r -> r.externalCode() != null && r.fullName() != null)
                .toList();
        if (rows.isEmpty()) {
            return;
        }
        List<DuplicateScanPort.UngVien> ungVien = new ArrayList<>(rows.size());
        Map<String, PersonRow> theoRef = new LinkedHashMap<>();
        for (PersonRow row : rows) {
            theoRef.put(row.externalCode(), row);
            ungVien.add(new DuplicateScanPort.UngVien(
                    row.externalCode(), row.fullName(), row.tabooName(), row.posthumousName(),
                    row.gender(), row.generation(), ctx.branchId(), row.birthYear(), row.death(),
                    row.nativePlace(),
                    // Dong dang cap nhat mot nguoi da co thi phai loai chinh nguoi ay ra, neu
                    // khong moi lan tai lai se bao "trung voi chinh minh" cho ca 400 dong.
                    row.resolvedPersonId()));
        }

        for (DuplicateScanPort.KetQua ketQua : duplicates.scan(ungVien)) {
            if (!ketQua.co()) {
                continue;
            }
            PersonRow row = theoRef.get(ketQua.ref());
            if (row == null) {
                continue;
            }
            ctx.add(canhBao(row, ketQua));
        }
    }

    // -----------------------------------------------------------------------------------------
    // Soạn cảnh báo
    // -----------------------------------------------------------------------------------------

    private static ImportIssue canhBao(PersonRow row, DuplicateScanPort.KetQua ketQua) {
        List<Map<String, Object>> chiTiet = new ArrayList<>(ketQua.nghiNgo().size());
        List<String> veTrongTep = new ArrayList<>();
        int soTrongPha = 0;
        DuplicateScanPort.NghiNgo manhNhatTrongPha = null;

        for (DuplicateScanPort.NghiNgo n : ketQua.nghiNgo()) {
            if (n.personId() == null) {
                chiTiet.add(moTaTrongTep(n));
                veTrongTep.add(veDongTrongTep(n));
            } else {
                chiTiet.add(moTaTrongPha(n));
                soTrongPha++;
                if (manhNhatTrongPha == null || n.score() > manhNhatTrongPha.score()) {
                    manhNhatTrongPha = n;
                }
            }
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("nghiNgo", List.copyOf(chiTiet));
        return ImportIssue.nhanKhau(IssueCode.IMP_SUSPECT_DUPLICATE, row.rowNo(),
                ImportColumn.HO_TEN.tieuDe(), cauThongBao(row, veTrongTep, soTrongPha,
                        manhNhatTrongPha), context);
    }

    private static String cauThongBao(PersonRow row, List<String> veTrongTep, int soTrongPha,
                                      DuplicateScanPort.NghiNgo manhNhatTrongPha) {
        List<String> ve = new ArrayList<>(2);
        if (!veTrongTep.isEmpty()) {
            ve.add(String.join("; ", veTrongTep));
        }
        if (manhNhatTrongPha != null) {
            ve.add(soTrongPha + " hồ sơ đã có trong phả — điểm cao nhất " + manhNhatTrongPha.score()
                    + tinHieuTrongNgoac(manhNhatTrongPha.hint()));
        }
        StringBuilder sb = new StringBuilder()
                .append("Dòng ").append(row.nhan())
                .append(" có thể trùng với ").append(String.join(", và ", ve)).append(".");
        if (soTrongPha > 0) {
            // Phai noi ro PHAI LAM GI TIEP, neu khong nguoi nhap chi thay mot canh bao cut duoi.
            sb.append(" Vì lý do riêng tư, bảng lỗi không nêu danh tính hồ sơ đã có trong phả:")
                    .append(" mở hồ sơ ấy từ màn đối chiếu để xem phần mình được phép xem, và nếu")
                    .append(" nó nằm ngoài phạm vi của mình thì nhờ Hội đồng Tộc biểu đối chiếu hộ.");
        }
        sb.append(" Máy chỉ nghi ngờ — anh em ruột trùng tên đệm theo đời là chuyện thường, nên")
                .append(" hãy tự đối chiếu rồi bỏ qua nếu là hai người khác nhau.");
        return sb.toString();
    }

    /** Vế "dòng kia cũng nằm trong tệp này": nêu đủ mã, tên và điểm — dữ liệu của chính người nhập. */
    private static String veDongTrongTep(DuplicateScanPort.NghiNgo n) {
        return "dòng " + (n.ref() == null ? "?" : n.ref())
                + (n.displayName() == null ? "" : " (" + n.displayName() + ")")
                + " trong chính tệp này — " + n.score() + " điểm" + tinHieuTrongNgoac(n.hint());
    }

    private static String tinHieuTrongNgoac(String hint) {
        return hint == null || hint.isBlank() ? "" : ", khớp ở: " + hint;
    }

    /**
     * Hồ sơ đã có trong phả: <b>khoá, điểm, nhãn tín hiệu</b> — hết.
     *
     * <p>Không {@code ten}, không {@code doi}. Khoá {@code tinHieu} cố ý <b>khác tên</b> với
     * {@code giaiThich} của vế trong tệp: hai vế mang hai mức dữ liệu khác nhau, nên để giao diện
     * hay một bài kiểm nào đó lỡ đọc nhầm khoá thì nó nhận được {@code null} chứ không nhận được
     * dữ liệu của mức kia.</p>
     */
    private static Map<String, Object> moTaTrongPha(DuplicateScanPort.NghiNgo n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("personId", n.personId().toString());
        m.put("diem", n.score());
        m.put("nguon", NGUON_TREE);
        dat(m, "tinHieu", n.hint());
        return m;
    }

    /** Dòng khác trong chính tệp này: giữ đủ, vì toàn bộ là thứ người nhập vừa gõ. */
    private static Map<String, Object> moTaTrongTep(DuplicateScanPort.NghiNgo n) {
        Map<String, Object> m = new LinkedHashMap<>();
        dat(m, "ref", n.ref());
        dat(m, "ten", n.displayName());
        dat(m, "doi", n.generation());
        m.put("diem", n.score());
        dat(m, "giaiThich", n.hint());
        m.put("nguon", NGUON_FILE);
        return m;
    }

    /**
     * Đặt khoá và bỏ qua giá trị {@code null}.
     *
     * <p>{@code Map.copyOf} ném NPE khi có giá trị {@code null}, và các map này đi tiếp vào
     * {@code List.copyOf} rồi vào {@code ImportIssue} — nơi phép lọc null chỉ chạy ở tầng ngoài
     * cùng, không đệ quy xuống đây.</p>
     */
    private static void dat(Map<String, Object> m, String khoa, Object giaTri) {
        if (giaTri != null) {
            m.put(khoa, giaTri);
        }
    }
}
