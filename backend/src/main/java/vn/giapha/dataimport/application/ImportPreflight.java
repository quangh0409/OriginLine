package vn.giapha.dataimport.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.CommitOrder;
import vn.giapha.dataimport.domain.CommitPreflight;
import vn.giapha.dataimport.domain.DuplicateMergePlan;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.MarriageColumn;
import vn.giapha.dataimport.domain.MarriageRow;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.port.ExternalRefPort;

/**
 * <b>Lượt soát trước khi ghi</b> — một bộ duy nhất, chạy ở <b>ba</b> chỗ: cuối bước kiểm, sau mỗi
 * quyết định nghi trùng, và ngay trước khi ghi.
 *
 * <h2>Vì sao lớp này tồn tại (bấm thật 24/09, NT-TC-15)</h2>
 * Trước đây lượt soát này chỉ chạy trong {@link CommitImportBatchService}. Hai dòng dâu/rể có khai
 * Đời đi qua bộ kiểm sạch sẽ, màn đối soát báo "Không còn lỗi phải sửa", người dùng bấm "Ghi vào
 * phả", lô nhận {@code 202} rồi mới {@code FAILED} với {@code IMP_UNDECIDED_INLAW_DOI}. Nghĩa là
 * màn đối soát <b>nói dối</b>: nó hứa một điều mà bước ghi không giữ. Người nhập không có lỗi gì
 * cả — họ làm đúng như màn hình bảo.
 *
 * <p>Cách sửa là cho bước kiểm hỏi <b>đúng cùng những câu hỏi</b> mà bước ghi sẽ hỏi, bằng
 * <b>đúng cùng một đoạn mã</b>. Hai bản chép tay của cùng một phép soát sẽ lệch nhau ở lần sửa kế
 * tiếp, và lỗi này quay lại dưới một mã khác.</p>
 *
 * <h2>Sáu mã, và chỉ lớp này sinh ra chúng</h2>
 * {@link #MA_CUA_LUOT_SOAT}. Nhờ bất biến "chỉ lớp này sinh" mà lượt soát chạy lại được sau mỗi
 * quyết định nghi trùng mà không phải chạy lại cả bộ luật: gỡ đúng sáu mã ấy khỏi danh sách đã
 * lưu, soát lại, ghép vào. Nếu một luật của bộ kiểm cũng sinh một trong sáu mã này thì phép gỡ sẽ
 * xoá luôn lỗi của luật ấy — vì vậy đừng dùng lại chúng ở chỗ khác.
 *
 * <h2>Vì sao chạy trên dữ liệu SAU KHI GỘP, không phải dữ liệu thô</h2>
 * Bước ghi soát trên tập dòng đã áp kế hoạch gộp ({@link DuplicateMergePlan#apDung}). Bước kiểm
 * phải soát trên đúng tập ấy, nếu không nó hoặc kêu oan (một dòng chép trùng không mã cha mà người
 * ta đã quyết gộp vẫn bị gọi là "dâu có khai Đời") hoặc im lặng sai (một phép gộp tạo vòng lặp mà
 * bộ luật, vốn chạy trên dữ liệu thô, không thấy). Đó cũng là lý do lượt soát phải chạy lại sau
 * <b>mỗi</b> quyết định: kế hoạch gộp đổi thì câu trả lời đổi.
 *
 * <h2>Bước ghi vẫn giữ lượt soát này làm lưới thứ hai</h2>
 * Giữa lần kiểm và lần bấm ghi có thể trôi qua vài ngày, và phả thì không đứng yên: người được kế
 * tự có thể đã bị gộp đi, mã có thể đã được trả lại. Một lưới ở bước kiểm không miễn cho bước ghi
 * khỏi hỏi lại.
 */
@Component
public class ImportPreflight {

    /**
     * Mọi mã lượt soát này có thể sinh ra — <b>đủ cả sáu</b>, và không luật nào khác được sinh chúng.
     *
     * <ul>
     *   <li>{@link IssueCode#IMP_UNDECIDED_INLAW_DOI}, {@link IssueCode#IMP_UNDECIDED_ADOPTION},
     *       {@link IssueCode#IMP_UNDECIDED_LIFE_STATUS} — ba ca Hội đồng Tộc biểu chưa chốt, xem
     *       {@link CommitPreflight};</li>
     *   <li>{@link IssueCode#IMP_COMMIT_CYCLE} — còn vòng lặp trong thứ tự ghi (kể cả vòng lặp đi
     *       qua cạnh hôn phối, thứ {@code CycleRule} không nhìn);</li>
     *   <li>{@link IssueCode#IMP_COMMIT_REF_NOT_FOUND} — mã ở trang Hôn phối hoặc mã kế tự không
     *       có trong tệp, cũng không có trong phả của chi;</li>
     *   <li>{@link IssueCode#IMP_MERGE_NOT_APPLICABLE} — một nhóm quyết "gộp" dính tới hai hồ sơ
     *       khác nhau đã có trong phả.</li>
     * </ul>
     */
    public static final Set<IssueCode> MA_CUA_LUOT_SOAT = Collections.unmodifiableSet(EnumSet.of(
            IssueCode.IMP_UNDECIDED_INLAW_DOI,
            IssueCode.IMP_UNDECIDED_ADOPTION,
            IssueCode.IMP_UNDECIDED_LIFE_STATUS,
            IssueCode.IMP_COMMIT_CYCLE,
            IssueCode.IMP_COMMIT_REF_NOT_FOUND,
            IssueCode.IMP_MERGE_NOT_APPLICABLE));

    private final ExternalRefPort externalRefs;

    public ImportPreflight(ExternalRefPort externalRefs) {
        this.externalRefs = externalRefs;
    }

    /**
     * Kết quả lượt soát.
     *
     * @param chan các vấn đề chặn, đã sắp tất định
     * @param thuTu thứ tự ghi — bước ghi dùng lại nó thay vì sắp lần thứ hai
     */
    public record KetQua(List<ImportIssue> chan, CommitOrder.KetQua thuTu) {

        public KetQua {
            chan = List.copyOf(chan);
        }

        public boolean sach() {
            return chan.isEmpty();
        }
    }

    /**
     * Soát một tập dòng đã áp kế hoạch gộp. Đây là lõi — bước ghi gọi thẳng nó.
     *
     * @param branchId chi của lô; mã tham chiếu được tra trong phả của <b>chi này</b>
     * @param keHoachGop kế hoạch gộp hiện hành — nguồn của {@code IMP_MERGE_NOT_APPLICABLE}
     * @param sauGop tập dòng sau khi áp chính kế hoạch ấy
     */
    public KetQua soat(UUID branchId, DuplicateMergePlan keHoachGop,
                       DuplicateMergePlan.SauGop sauGop) {
        List<PersonRow> personRows = sauGop.personRows();
        List<MarriageRow> marriageRows = sauGop.marriageRows();

        List<ImportIssue> chan = new ArrayList<>(gopKhongApDungDuoc(keHoachGop));
        chan.addAll(CommitPreflight.soat(personRows, marriageRows));
        CommitOrder.KetQua thuTu = CommitOrder.sap(personRows, marriageRows);
        if (thuTu.coVongLap()) {
            chan.add(vongLap(thuTu.dongKetVong()));
        }
        chan.addAll(maThamChieuKhongPhanGiaiDuoc(branchId, personRows, marriageRows));
        chan.sort(ImportIssue.TAT_DINH);
        return new KetQua(chan, thuTu);
    }

    /**
     * Soát rồi <b>ghép</b> vào danh sách của bộ kiểm — thứ bước kiểm, bước quyết nghi trùng và cả
     * bước ghi (khi nó dừng) lưu vào {@code import_issue}.
     *
     * <h2>Ba việc ngoài phép cộng hai danh sách</h2>
     * <ol>
     *   <li><b>Gỡ kết quả soát cũ</b> (sáu mã {@link #MA_CUA_LUOT_SOAT}) trước khi ghép kết quả
     *       mới. Không gỡ thì sau một quyết định "gộp" gỡ được vấn đề, lỗi cũ vẫn nằm đó và lô bị
     *       chặn vì một điều không còn đúng.</li>
     *   <li><b>Không nói một chuyện hai lần</b>: bộ luật đã báo {@code IMP_CYCLE} thì bỏ
     *       {@code IMP_COMMIT_CYCLE} — cùng một vòng lặp, và câu của {@code CycleRule} (có đường
     *       vòng đầy đủ) có ích hơn.</li>
     *   <li><b>Kế tự không tìm thấy</b>: xem {@link #nangCanhBaoKeTu}.</li>
     * </ol>
     *
     * @param daCo danh sách hiện có — của bộ luật vừa chạy, hoặc đọc lại từ {@code import_issue}
     * @return danh sách đầy đủ, đã sắp tất định
     */
    public List<ImportIssue> ghepVao(List<ImportIssue> daCo, UUID branchId,
                                     DuplicateMergePlan keHoachGop,
                                     DuplicateMergePlan.SauGop sauGop) {
        return ghepVao(daCo, soat(branchId, keHoachGop, sauGop));
    }

    /** Như trên, khi bên gọi đã có sẵn kết quả soát (bước ghi cần cả {@link KetQua#thuTu()}). */
    public List<ImportIssue> ghepVao(List<ImportIssue> daCo, KetQua ketQua) {
        List<ImportIssue> nen = new ArrayList<>(daCo.size());
        boolean boLuatDaBaoVongLap = false;
        for (ImportIssue issue : daCo) {
            if (MA_CUA_LUOT_SOAT.contains(issue.code())) {
                continue;
            }
            boLuatDaBaoVongLap |= issue.code() == IssueCode.IMP_CYCLE;
            nen.add(issue);
        }

        List<ImportIssue> moi = new ArrayList<>(ketQua.chan().size());
        for (ImportIssue issue : ketQua.chan()) {
            if (issue.code() == IssueCode.IMP_COMMIT_CYCLE && boLuatDaBaoVongLap) {
                continue;
            }
            moi.add(nangCanhBaoKeTu(issue, nen));
        }

        List<ImportIssue> tatCa = new ArrayList<>(nen);
        tatCa.addAll(moi);
        tatCa.sort(ImportIssue.TAT_DINH);
        return List.copyOf(tatCa);
    }

    /**
     * Mã kế tự không tìm thấy ở đâu cả: <b>lỗi chặn</b>, không còn là cảnh báo.
     *
     * <h2>Một quyết định bị đảo — và lý do của bên thua</h2>
     * {@code ParentReferenceRule} xếp {@code IMP_HEIR_TARGET_NOT_FOUND} là <b>cảnh báo</b>, với lý
     * do: kế tự là quan hệ thêm, không phải cạnh giữ cây — thiếu nó thì người ấy vẫn đứng đúng chỗ
     * trong phả. Lý do ấy đúng về cây, nhưng bước ghi từ lâu đã <b>chặn</b> đúng ca này bằng
     * {@code IMP_COMMIT_REF_NOT_FOUND} (ghi lô mà lặng lẽ bỏ quan hệ kế tự thì cụ tuyệt tự vẫn tuyệt
     * tự trong phả). Hai nơi bất đồng, và người chịu là người nhập: tick "đã xem" một cảnh báo, bấm
     * ghi, rồi thất bại. Bên thắng là bên đang thực sự được thi hành — bước ghi.
     *
     * <p>Chỉ đổi đúng phần giao nhau: mã <b>không</b> có trong tệp <b>và không</b> có trong phả
     * của chi. Mã có trong phả mà không có trong tệp thì bộ luật vẫn cảnh báo (nó chỉ nhìn tệp)
     * và bước ghi cho qua — giữ nguyên là cảnh báo.</p>
     *
     * <p>Cảnh báo cũ bị <b>thay</b> chứ không đứng cạnh: cùng một ô vừa "nên xem" vừa "phải sửa"
     * là hai câu trả lời khác nhau cho cùng một câu hỏi. Phần gợi ý mã gần giống của nó — thứ biến
     * lỗi thành một cú sửa ba giây — được mang sang lỗi chặn.</p>
     */
    private static ImportIssue nangCanhBaoKeTu(ImportIssue chan, List<ImportIssue> nen) {
        if (chan.code() != IssueCode.IMP_COMMIT_REF_NOT_FOUND
                || !ImportIssue.SHEET_NHAN_KHAU.equals(chan.sheet()) || chan.rowNo() == null) {
            return chan;
        }
        ImportIssue canhBao = null;
        for (ImportIssue i : nen) {
            if (i.code() == IssueCode.IMP_HEIR_TARGET_NOT_FOUND
                    && chan.rowNo().equals(i.rowNo()) && ImportIssue.SHEET_NHAN_KHAU.equals(i.sheet())) {
                canhBao = i;
                break;
            }
        }
        if (canhBao == null) {
            return chan;
        }
        nen.remove(canhBao);
        Object goiY = canhBao.context().get("goiY");
        if (!(goiY instanceof List<?> ds) || ds.isEmpty()) {
            return chan;
        }
        Map<String, Object> context = new LinkedHashMap<>(chan.context());
        context.put("goiY", goiY);
        StringBuilder msg = new StringBuilder(chan.message()).append(" Có phải anh/chị định gõ ");
        for (int k = 0; k < ds.size(); k++) {
            msg.append(k == 0 ? "" : ", ").append(ds.get(k));
        }
        msg.append(" không?");
        return new ImportIssue(chan.code(), chan.sheet(), chan.rowNo(), chan.field(),
                msg.toString(), context);
    }

    // ---------------------------------------------------------------------------------------
    // Ba phép soát ngoài CommitPreflight — chuyển nguyên văn từ CommitImportBatchService
    // ---------------------------------------------------------------------------------------

    /**
     * Nhóm gộp không áp dụng được → lỗi chặn, kèm nguyên văn câu giải thích của kế hoạch.
     *
     * <p>Dừng có giải thích còn hơn ghi một nửa: một phép gộp áp dụng dở nghĩa là vài dòng đã bị
     * trỏ lại còn vài dòng thì chưa, và cái nửa vời ấy nằm trong phả chứ không nằm trong log.</p>
     */
    private static List<ImportIssue> gopKhongApDungDuoc(DuplicateMergePlan keHoach) {
        if (!keHoach.coVuongMac()) {
            return List.of();
        }
        List<ImportIssue> found = new ArrayList<>(keHoach.vuongMac().size());
        for (String lyDo : keHoach.vuongMac()) {
            found.add(ImportIssue.caLo(IssueCode.IMP_MERGE_NOT_APPLICABLE, lyDo, Map.of()));
        }
        return found;
    }

    /**
     * Vòng lặp trong thứ tự ghi.
     *
     * <p>Câu chữ không được giả định bộ kiểm đã chạy trước: ở bước kiểm, lỗi này đến từ chính lượt
     * soát (thường là vòng lặp đi qua cạnh hôn phối, hoặc do một phép gộp tạo ra), còn ở bước ghi
     * thì nó nghĩa là dữ liệu đã đổi giữa hai bước.</p>
     */
    private static ImportIssue vongLap(List<String> ketVong) {
        return ImportIssue.caLo(IssueCode.IMP_COMMIT_CYCLE,
                "Không sắp được thứ tự ghi vì còn vòng lặp trên các mã: " + String.join(", ", ketVong)
                        + ". Cha mẹ phải được ghi trước con, và dâu/rể ghi sau vợ/chồng; ở đây"
                        + " một mã cha/mẹ hoặc một dòng Hôn phối khiến các mã trên chờ lẫn nhau."
                        + " Mở sổ tra lại Mã cha, Mã mẹ và trang Hôn phối của các mã đó, hoặc xem"
                        + " lại quyết định gộp vừa bấm nếu lỗi xuất hiện sau một quyết định gộp.",
                Map.of("ma", ketVong));
    }

    /**
     * Mã tham chiếu không phân giải được.
     *
     * <p>Bộ kiểm đã soát mã cha/mẹ, nhưng mã ở trang Hôn phối thì chưa luật nào soát, còn mã kế tự
     * chỉ được cảnh báo (xem {@link #nangCanhBaoKeTu}). Bỏ qua lặng lẽ một dòng hôn phối không
     * phân giải được nghĩa là một cặp vợ chồng biến mất khỏi phả mà không ai biết — và đó chính là
     * loại mất mát không bao giờ tự lộ ra.</p>
     */
    private List<ImportIssue> maThamChieuKhongPhanGiaiDuoc(UUID branchId,
                                                           List<PersonRow> personRows,
                                                           List<MarriageRow> marriageRows) {
        Set<String> trongLo = new LinkedHashSet<>();
        for (PersonRow row : personRows) {
            if (row.externalCode() != null && !row.externalCode().isBlank()) {
                trongLo.add(row.externalCode());
            }
        }
        Set<String> canTra = new LinkedHashSet<>();
        for (MarriageRow m : marriageRows) {
            themNeuLa(canTra, trongLo, m.husbandCode());
            themNeuLa(canTra, trongLo, m.wifeCode());
        }
        for (PersonRow row : personRows) {
            themNeuLa(canTra, trongLo, row.heirOfCode());
        }
        if (canTra.isEmpty()) {
            return List.of();
        }
        Set<String> daBiet = externalRefs.resolve(branchId, canTra).keySet();

        List<ImportIssue> found = new ArrayList<>();
        for (MarriageRow m : marriageRows) {
            if (!m.duCap()) {
                continue;
            }
            themHonPhoi(found, m, m.husbandCode(), MarriageColumn.MA_CHONG, trongLo, daBiet);
            themHonPhoi(found, m, m.wifeCode(), MarriageColumn.MA_VO, trongLo, daBiet);
        }
        for (PersonRow row : personRows) {
            String ma = row.heirOfCode();
            if (ma == null || ma.isBlank() || trongLo.contains(ma) || daBiet.contains(ma.trim())) {
                continue;
            }
            found.add(ImportIssue.nhanKhau(IssueCode.IMP_COMMIT_REF_NOT_FOUND, row.rowNo(),
                    ImportColumn.KE_TU_CHO_AI.tieuDe(),
                    "Dòng " + row.rowNo() + " (" + row.nhan() + ") khai kế tự cho mã " + ma
                            + " nhưng mã đó không có trong tệp, cũng chưa có trong phả của chi này."
                            + " Quan hệ kế tự là thứ dòng họ coi trọng bậc nhất; ghi lô mà bỏ qua"
                            + " nó thì cụ tuyệt tự vẫn tuyệt tự trong phả. Sửa ô Kế tự cho ai (Mã)"
                            + " ở dòng " + row.rowNo() + ", hoặc để trống nếu chưa rõ.",
                    Map.of("maKhongTimThay", ma)));
        }
        return found;
    }

    private static void themHonPhoi(List<ImportIssue> found, MarriageRow m, String ma,
                                    MarriageColumn cot, Set<String> trongLo, Set<String> daBiet) {
        if (trongLo.contains(ma) || daBiet.contains(ma.trim())) {
            return;
        }
        found.add(ImportIssue.honPhoi(IssueCode.IMP_COMMIT_REF_NOT_FOUND, m.rowNo(), cot.tieuDe(),
                "Trang Hôn phối dòng " + m.rowNo() + " có " + cot.tieuDe().toLowerCase() + " là "
                        + ma + " nhưng mã đó không có ở trang Nhân khẩu, cũng chưa có trong phả của"
                        + " chi này. Bỏ qua dòng này thì một cặp vợ chồng biến mất khỏi phả mà"
                        + " không ai hay. Thêm người ấy vào trang Nhân khẩu, hoặc sửa lại mã.",
                Map.of("maKhongTimThay", ma)));
    }

    private static void themNeuLa(Set<String> dich, Set<String> trongLo, String ma) {
        if (ma != null && !ma.isBlank() && !trongLo.contains(ma)) {
            dich.add(ma.trim());
        }
    }
}
