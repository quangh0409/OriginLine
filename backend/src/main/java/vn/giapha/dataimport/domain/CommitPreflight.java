package vn.giapha.dataimport.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phép soát <b>ngay trước khi ghi</b>: ba ca dữ liệu gia phả Việt mà <b>Hội đồng Tộc biểu chưa
 * chốt</b>, cộng phép kiểm vòng lặp lần cuối.
 *
 * <h2>Đây không phải một bộ kiểm thứ hai</h2>
 * Bộ kiểm ({@code ImportRule}) bắt <b>mâu thuẫn nội tại</b>: vòng lặp, con sinh trước cha, ngày âm
 * không tồn tại. Nó luôn chỉ ra được "sửa ô nào". Lớp này bắt một loại khác hẳn: dữ liệu
 * <b>hoàn toàn hợp lệ</b>, không vi phạm ràng buộc nào, nhưng chở một câu hỏi mà dòng họ chưa trả
 * lời. Người nhập không sửa được bằng cách gõ lại một ô — cần một quyết định của Hội đồng rồi cả
 * bốn chi làm theo cùng một cách.
 *
 * <h2>Ba ca, và vì sao máy không được tự chọn đáp án</h2>
 * <ol>
 *   <li><b>Đời của dâu/rể</b> (kế hoạch §14 ca 2). Người lấy về từ họ khác cần một Mã ở trang Nhân
 *       khẩu, nhưng <b>Đời</b> thì mơ hồ: đời của chồng, hay đời trong họ gốc của bà? Lược đồ nhận
 *       số nào cũng được, nên bốn chi sẽ trả lời bốn kiểu và không cách nào hoà giải về sau.</li>
 *   <li><b>Con nuôi từ trong họ</b> (ca 5). Đứa trẻ có cha ruột ở chi bên cạnh và cha nuôi ở chi
 *       này; cả hai đều thật, cả hai đều cần ghi. Lược đồ đỡ được (hai cạnh {@code PARENT_BIO} và
 *       {@code PARENT_ADOPT} cùng tồn tại), nhưng mẫu Excel chỉ có <b>một</b> cặp Mã cha/Mã mẹ.
 *       Ghi một người là âm thầm xoá người kia khỏi phả.</li>
 *   <li><b>Không rõ sống hay đã mất</b> (ca 9). Người tha hương, đi Nam 1954, mất liên lạc — có
 *       thật ở gần như mọi dòng họ. {@code person.is_alive} là {@code BOOLEAN NOT NULL} và không
 *       có giá trị thứ ba. Đoán "còn sống" sinh ra một phả đồ toàn người sống từ đời thứ ba; đoán
 *       "đã mất" sinh giỗ cho một người chưa mất — và cả họ đi cúng nhầm.</li>
 * </ol>
 *
 * <h2>Vì sao dừng lại rẻ hơn đoán</h2>
 * Cái sai đắt nhất của nhập liệu gia phả là loại không bộ kiểm nào bắt được, vì dữ liệu nhất quán;
 * nó chỉ sai so với sự thật. Hậu quả không dừng ở một dòng: đặt sai đời một bà dâu thì danh xưng
 * của cả một cành tính sai và giỗ được nhắc cho nhầm nhóm người. Dừng lại mất một buổi họp.
 */
public final class CommitPreflight {

    private CommitPreflight() {
    }

    /**
     * Soát toàn bộ lô. Trả về danh sách <b>rỗng</b> nghĩa là được phép ghi.
     *
     * @param rows các dòng Nhân khẩu đã đối soát
     * @param marriages các dòng Hôn phối — cần để nhận ra ai là dâu/rể
     * @return các vấn đề chặn, đã sắp tất định để hai lần chạy cho cùng một danh sách
     */
    public static List<ImportIssue> soat(List<PersonRow> rows, List<MarriageRow> marriages) {
        Set<String> coTrongHonPhoi = new LinkedHashSet<>();
        for (MarriageRow m : marriages) {
            themNeuCo(coTrongHonPhoi, m.husbandCode());
            themNeuCo(coTrongHonPhoi, m.wifeCode());
        }

        List<ImportIssue> issues = new ArrayList<>();
        for (PersonRow row : rows) {
            if (row.plannedAction() == PlannedAction.SKIP) {
                continue;
            }
            khongRoSongChet(row, issues);
            conNuoiTrongHo(row, issues);
            doiCuaDauRe(row, coTrongHonPhoi, issues);
        }
        issues.sort(ImportIssue.TAT_DINH);
        return issues;
    }

    /**
     * Ô "Còn sống" trống <b>và</b> không có ngày giỗ — {@code PersonRow.daMat()} trả {@code null}.
     *
     * <p>Ô trống không có nghĩa là còn sống, và cũng không có nghĩa là đã mất. Với một cuốn gia phả
     * thì mặc định nào cũng sai ở hàng trăm dòng cùng lúc.</p>
     */
    private static void khongRoSongChet(PersonRow row, List<ImportIssue> issues) {
        if (row.daMat() != null) {
            return;
        }
        issues.add(ImportIssue.nhanKhau(IssueCode.IMP_UNDECIDED_LIFE_STATUS, row.rowNo(),
                "Còn sống",
                "Dòng " + row.rowNo() + " (" + row.nhan() + ") bỏ trống cả ô Còn sống lẫn ô Ngày"
                        + " mất âm, nên không biết người này còn sống hay đã mất. Hệ thống không"
                        + " đoán: đoán còn sống thì phả đồ đầy người sống từ đời thứ ba, đoán đã"
                        + " mất thì cả họ được nhắc giỗ cho một người chưa mất. Hội đồng Tộc biểu"
                        + " cần chốt quy ước ghi người mất liên lạc trước khi ghi lô này.",
                Map.of("externalCode", row.externalCode())));
    }

    /**
     * Con nuôi mà cha/mẹ nuôi nằm trong chính lô hoặc trong phả.
     *
     * <p>Mọi Mã cha/Mã mẹ trong tệp đều trỏ tới người <b>trong họ</b> — đó là ý nghĩa của cột Mã.
     * Nên một dòng khai {@code Quan hệ = nuôi} chính là ca 5: chỉ ghi được một trong hai người
     * cha.</p>
     */
    private static void conNuoiTrongHo(PersonRow row, List<ImportIssue> issues) {
        if (row.parentRel() != CellCodec.ParentRel.ADOPT || (!row.coCha() && !row.coMe())) {
            return;
        }
        issues.add(ImportIssue.nhanKhau(IssueCode.IMP_UNDECIDED_ADOPTION, row.rowNo(), "Quan hệ",
                "Dòng " + row.rowNo() + " (" + row.nhan() + ") khai là con nuôi của một người"
                        + " trong họ. Mẫu Excel chỉ chở được một cặp Mã cha / Mã mẹ, nên ghi cha"
                        + " nuôi là mất cha ruột khỏi phả, và ngược lại — trong khi cả hai đều"
                        + " thật và cả hai đều cần ghi. Hội đồng Tộc biểu cần chốt: ghi cả hai"
                        + " người cha hay chỉ cha nuôi.",
                Map.of("externalCode", row.externalCode(),
                        "fatherCode", row.coCha() ? row.fatherCode() : "",
                        "motherCode", row.coMe() ? row.motherCode() : "")));
    }

    /**
     * Dâu/rể có khai <b>Đời</b>.
     *
     * <h2>Cách nhận ra dâu/rể, và một ngoại lệ duy nhất</h2>
     * Dấu hiệu là <b>không có Mã cha lẫn Mã mẹ</b> (không sinh ra trong họ) nhưng <b>có mặt ở
     * trang Hôn phối</b> (lấy người trong họ). Ngoại lệ duy nhất là <b>đời 1</b>: thuỷ tổ và vợ
     * thuỷ tổ đứng ở gốc, và số 1 mang cùng một nghĩa dưới cả hai quy ước — không có gì để hoà
     * giải. Từ đời 2 trở đi thì con số ấy hoặc là đời của chồng, hoặc là đời trong họ gốc, và máy
     * không có cách nào biết người nhập đã dùng quy ước nào.
     *
     * <p>Bỏ trống ô Đời thì <b>không</b> chặn: khi đó không có khẳng định nào để hiểu nhầm, và đời
     * thứ được suy ra từ cạnh hôn phối đúng như phần còn lại của hệ thống vẫn làm.</p>
     */
    private static void doiCuaDauRe(PersonRow row, Set<String> coTrongHonPhoi,
                                    List<ImportIssue> issues) {
        if (row.coCha() || row.coMe() || row.generation() == null || row.generation() <= 1
                || !coTrongHonPhoi.contains(row.externalCode())) {
            return;
        }
        issues.add(ImportIssue.nhanKhau(IssueCode.IMP_UNDECIDED_INLAW_DOI, row.rowNo(), "Đời",
                "Dòng " + row.rowNo() + " (" + row.nhan() + ") là dâu/rể — không có Mã cha, không"
                        + " có Mã mẹ, nhưng có mặt ở trang Hôn phối — mà vẫn ghi Đời "
                        + row.generation() + ". Chưa rõ đó là đời của chồng hay đời trong họ gốc,"
                        + " và Hội đồng Tộc biểu chưa chốt quy ước. Cách gỡ ngay: để trống ô Đời"
                        + " cho các dòng dâu/rể, đời thứ sẽ suy theo vợ/chồng.",
                Map.of("externalCode", row.externalCode(), "doiDaKhai", row.generation())));
    }

    private static void themNeuCo(Set<String> dich, String ma) {
        if (ma != null && !ma.isBlank()) {
            dich.add(ma);
        }
    }
}
