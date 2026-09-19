package vn.giapha.dataimport.application.rule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.MarriageRow;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.PlannedAction;

/**
 * Trạng thái chung mà cả bộ luật đọc, và nơi chúng gom lỗi về.
 *
 * <h2>Vì sao dựng sẵn các bảng tra thay vì để mỗi luật tự quét</h2>
 * Mười ba luật, mỗi luật quét 400 dòng để tìm "dòng nào mang mã này" là 5.200 lượt quét tuyến tính
 * cho một việc mà một {@code Map} làm xong một lần. Nhưng lý do chính không phải hiệu năng: nếu
 * mỗi luật tự dựng bảng tra của nó thì mười ba bản sẽ dần lệch nhau về cách xử lý mã rỗng, mã
 * trùng, hoa thường — và một luật sẽ thấy dòng mà luật khác không thấy.
 *
 * <h2>Bối cảnh đã được đối soát trước khi luật chạy</h2>
 * {@link #resolved()} và {@link #plannedActionOf(String)} đã có sẵn: việc tra
 * {@code person_external_ref} làm <b>một lần</b> ở service, không phải trong từng luật. Đây cũng
 * là chỗ tính bất biến khi tải lại được quyết định.
 */
public final class ValidationContext {

    private final UUID batchId;
    private final UUID branchId;
    private final List<PersonRow> personRows;
    private final List<MarriageRow> marriageRows;

    /** Mã ngoài đã chuẩn hoá tới dòng đầu tiên mang mã ấy. Dòng trùng mã do luật riêng bắt. */
    private final Map<String, PersonRow> theoMa = new LinkedHashMap<>();

    /** Mã ngoài tới nhân khẩu đã có trong phả. Vắng mặt nghĩa là dòng đó sẽ tạo mới. */
    private final Map<String, UUID> resolved;

    /** Toàn bộ mã đã đăng ký của chi này, kể cả mã không xuất hiện trong tệp lần này. */
    private final Set<String> maDaCoCuaChi;

    /** Mã tới chi sở hữu nó — dùng để bắt việc nộp tệp chứa mã của chi khác. */
    private final Map<String, UUID> chiSoHuuMa;

    private final List<ImportIssue> issues = new ArrayList<>();

    public ValidationContext(UUID batchId,
                             UUID branchId,
                             List<PersonRow> personRows,
                             List<MarriageRow> marriageRows,
                             Map<String, UUID> resolved,
                             Set<String> maDaCoCuaChi,
                             Map<String, UUID> chiSoHuuMa) {
        this.batchId = batchId;
        this.branchId = branchId;
        this.personRows = List.copyOf(personRows);
        this.marriageRows = List.copyOf(marriageRows);
        this.resolved = Map.copyOf(resolved);
        this.maDaCoCuaChi = Set.copyOf(maDaCoCuaChi);
        this.chiSoHuuMa = Map.copyOf(chiSoHuuMa);
        for (PersonRow row : this.personRows) {
            if (row.externalCode() != null) {
                this.theoMa.putIfAbsent(row.externalCode(), row);
            }
        }
    }

    public UUID batchId() {
        return batchId;
    }

    public UUID branchId() {
        return branchId;
    }

    public List<PersonRow> personRows() {
        return personRows;
    }

    public List<MarriageRow> marriageRows() {
        return marriageRows;
    }

    public Map<String, UUID> resolved() {
        return resolved;
    }

    public Set<String> maDaCoCuaChi() {
        return maDaCoCuaChi;
    }

    public Map<String, UUID> chiSoHuuMa() {
        return chiSoHuuMa;
    }

    /** Dòng trong tệp mang mã này; {@code null} khi mã chỉ tồn tại trong phả chứ không trong tệp. */
    public PersonRow rowOf(String externalCode) {
        return externalCode == null ? null : theoMa.get(externalCode);
    }

    /** Mã này phân giải được không: có trong tệp, hoặc đã có trong phả. */
    public boolean phanGiaiDuoc(String externalCode) {
        return externalCode != null
                && (theoMa.containsKey(externalCode) || resolved.containsKey(externalCode));
    }

    /**
     * Mọi mã người nhập có thể đã định gõ — nguồn cho phép gợi ý mã gần giống.
     *
     * <p>Gộp cả mã trong tệp lẫn mã đã đăng ký của chi: người nhập gõ nhầm một ký tự của một mã
     * nằm ở trang khác, hoặc của một người đã nhập từ năm ngoái, đều là chuyện thường.</p>
     */
    public Set<String> moiMaBietDuoc() {
        Set<String> all = new LinkedHashSet<>(theoMa.keySet());
        all.addAll(maDaCoCuaChi);
        return all;
    }

    public PlannedAction plannedActionOf(String externalCode) {
        return resolved.containsKey(externalCode) ? PlannedAction.UPDATE : PlannedAction.CREATE;
    }

    public void add(ImportIssue issue) {
        if (issue != null) {
            issues.add(issue);
        }
    }

    /** Danh sách lỗi đã sắp xếp tất định — hai lần chạy cho kết quả giống hệt nhau. */
    public List<ImportIssue> issues() {
        List<ImportIssue> sorted = new ArrayList<>(issues);
        sorted.sort(ImportIssue.TAT_DINH);
        return List.copyOf(sorted);
    }
}
