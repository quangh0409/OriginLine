package vn.giapha.genealogy.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng đọc/ghi <b>chi gốc</b> ({@code branch_kind = DONG_HO}, {@code nlevel(path) = 1}) và hai phép
 * đếm mà lối "Khởi tạo dòng họ" cần.
 *
 * <h2>Vì sao là một cổng riêng, không phải thêm {@code save} vào {@code BranchRepository}</h2>
 * {@code BranchRepository} cố ý chỉ đọc: chi được tạo bằng migration và bộ nạp dữ liệu qua JDBC
 * (xem javadoc trường {@code path} của {@code BranchJpaEntity}). Lối ghi DUY NHẤT được mở ở đây là
 * lối cho <b>một</b> chi — chi gốc — và mở nó trên cổng đọc chung sẽ mời mọi use case khác ghi chi
 * tuỳ ý. Cổng nằm ở {@code application} (không ở {@code domain.port}) theo đúng khuôn của
 * {@link GenealogyRosterPort}: nó là cổng truy vấn/điều phối, không nói bằng aggregate nào.
 */
public interface ClanRootPort {

    /** Mọi chi cấp 1 chưa xoá mềm, sắp theo nhãn. */
    List<RootBranch> roots();

    Optional<RootBranch> rootByLabel(String label);

    /** Có chi nào (bất kể cấp, kể cả đã xoá mềm) đang dùng đúng {@code path} này không. */
    boolean pathTaken(String path);

    RootBranch createRoot(UUID id, String label, String name, String region);

    /** Đổi tên và vùng của chi gốc. Trả {@code true} nếu có dòng nào thực sự đổi. */
    boolean updateRoot(UUID id, String name, String region);

    /**
     * Đổi nhãn gốc của <b>cả cây con</b>: {@code old.x.y → new.x.y}, và {@code slug} của chính chi
     * gốc. Một câu {@code UPDATE}, cùng transaction với bên gọi.
     *
     * @return số chi đã đổi path
     */
    int relabelRoot(String oldLabel, String newLabel);

    /** Số nhân khẩu; {@code includeDeleted} = đếm cả người đã xoá mềm (node vẫn còn trong cây). */
    long countPersons(boolean includeDeleted);

    /** Số chi/ngành chưa xoá mềm nằm <b>dưới</b> gốc {@code label} — không tính chính chi gốc. */
    long countBranchesUnder(String label);

    /**
     * Một chi gốc.
     *
     * @param region {@code BAC/TRUNG/NAM} hoặc {@code null}
     */
    record RootBranch(UUID id, String label, String name, String region) {
    }
}
