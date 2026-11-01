package vn.giapha.genealogy.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.domain.port.AuditPort;
import vn.giapha.genealogy.domain.port.TreeCachePort;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;

/**
 * <b>Chi gốc của dòng họ</b> — named interface {@code "goc-dong-ho"}, mặt tiền thứ tư của context.
 *
 * <h2>Lý do mở thêm một mặt tiền (CLAUDE.md đòi phải nói được)</h2>
 * Trước lớp này <b>không có đường nào</b> trong ứng dụng tạo được một chi: chi sinh ra từ bộ nạp
 * dữ liệu mẫu và từ SQL gõ tay. Nhập liệu Excel cần chi đã có sẵn (mẫu phát theo từng chi), nên
 * một dòng họ mới triển khai bằng màn hình sẽ kẹt ngay ở bước đầu. Context {@code clan} cần đúng
 * một năng lực ghi — <b>dựng/đổi tên/đổi nhãn chi gốc {@code DONG_HO}</b> — cộng hai phép đếm cho
 * bảng "còn thiếu bước nào". Mở cả {@code genealogy.application} để lấy chừng ấy là xoá ranh giới;
 * đọc/ghi thẳng bảng {@code branch} từ {@code clan} là hai context cùng ghi một bảng. Nên: một
 * mặt tiền hẹp, nói bằng kiểu nguyên thuỷ ({@link ClanRootView}), đúng hai kiểu mang nhãn.
 *
 * <h2>Không mở lối tạo chi con</h2>
 * Chỉ chi gốc. Chi/ngành bên dưới vẫn đi đường cũ; mở rộng lớp này thành "tạo chi bất kỳ" là một
 * quyết định riêng, có luật phạm vi riêng (Trưởng chi tạo ngành trong chi mình?), không phải một
 * phương thức thêm vào cho tiện.
 *
 * <h2>Quyền: chỉ vai kỹ thuật {@code ADMIN}</h2>
 * Kiểm lại ở đây dù {@code clan} đã kiểm: một mặt tiền ghi mà tin bên gọi là một lỗ chờ bên gọi
 * thứ hai. Không chạy {@code GenealogyAccessGuard.requireWriteAccess} — phép ấy hỏi phạm vi
 * {@code ltree} của một đối tượng, mà chi gốc thì <i>là</i> phạm vi.
 *
 * <p>Mọi phương thức ghi chạy trong transaction của bên gọi ({@code REQUIRED}): lưu hồ sơ dòng họ,
 * dựng chi gốc và dựng bộ luật danh xưng là <b>một</b> đơn vị — hỏng một thì không cái nào vào.</p>
 */
@org.springframework.modulith.NamedInterface("goc-dong-ho")
@Service
public class ClanRootService {

    private static final Logger log = LoggerFactory.getLogger(ClanRootService.class);

    private static final String ENTITY = "Branch";

    private final ClanRootPort roots;
    private final PrivacyTierService privacy;
    private final AuditPort audit;
    private final TreeCachePort treeCache;

    public ClanRootService(ClanRootPort roots, PrivacyTierService privacy, AuditPort audit,
                           TreeCachePort treeCache) {
        this.roots = roots;
        this.privacy = privacy;
        this.audit = audit;
        this.treeCache = treeCache;
    }

    /** Mọi chi cấp 1 chưa xoá mềm. Một bản cài lành mạnh hôm nay có đúng một. */
    @Transactional(readOnly = true)
    public List<ClanRootView> roots() {
        return roots.roots().stream().map(ClanRootView::of).toList();
    }

    @Transactional(readOnly = true)
    public Optional<ClanRootView> root(String label) {
        return label == null ? Optional.empty() : roots.rootByLabel(label).map(ClanRootView::of);
    }

    /** Số nhân khẩu; {@code includeDeleted} = đếm cả người đã xoá mềm. */
    @Transactional(readOnly = true)
    public long countPersons(boolean includeDeleted) {
        return roots.countPersons(includeDeleted);
    }

    /** Số chi/ngành dưới gốc {@code label}, không tính chính chi gốc. */
    @Transactional(readOnly = true)
    public long countBranchesUnder(String label) {
        return label == null ? 0L : roots.countBranchesUnder(label);
    }

    /**
     * Đảm bảo chi gốc {@code label} tồn tại, mang đúng tên và vùng.
     *
     * <ul>
     *   <li>{@code previousLabel} khác {@code label} ⇒ <b>đổi nhãn</b> cả cây con (chỉ khi phả chưa
     *       có nhân khẩu nào, kể cả đã xoá mềm — xem {@code ClanProfile} bên {@code clan});</li>
     *   <li>chưa có chi gốc {@code label} ⇒ tạo mới ({@code DONG_HO}, không cha);</li>
     *   <li>đã có ⇒ đồng bộ tên và vùng nếu khác.</li>
     * </ul>
     *
     * <p><b>Vì sao đồng bộ tên chi gốc theo tên dòng họ.</b> Chi gốc <i>là</i> dòng họ trên phả đồ
     * (nút trên cùng), và các lối đọc cũ — tên dòng họ trong thư mời, mẫu Excel — đọc tên ấy từ
     * đây. Để hai tên lệch nhau là để một màn hình nói "Họ Nguyễn Đình" còn màn kia nói tên cũ.</p>
     *
     * <p><b>Vì sao đồng bộ cả vùng.</b> {@code branch.region} của chi gốc là thứ
     * {@code RuleSetJpaRepository} đọc khi một chi không có bộ luật riêng; để nó lệch với vùng của
     * hồ sơ là có hai câu trả lời cho "dòng họ này xưng hô theo miền nào".</p>
     *
     * @param region {@code BAC/TRUNG/NAM}
     */
    @Transactional
    public ClanRootView establish(String label, String name, String region, String previousLabel) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(name, "name");
        CallerContext caller = privacy.caller();
        if (!caller.isAdmin()) {
            throw new ForbiddenException(GenealogyProblemCodes.FORBIDDEN,
                    "Chỉ Quản trị hệ thống được khởi tạo hoặc đổi chi gốc của dòng họ");
        }

        boolean changed = false;
        if (previousLabel != null && !previousLabel.equals(label)
                && roots.rootByLabel(previousLabel).isPresent()) {
            relabel(previousLabel, label);
            changed = true;
        }

        Optional<ClanRootPort.RootBranch> existing = roots.rootByLabel(label);
        ClanRootPort.RootBranch result;
        if (existing.isEmpty()) {
            if (roots.pathTaken(label)) {
                // Nhan da thuoc ve mot chi da xoa mem (xoa mem giu dong) — khong "hoi sinh" ngam.
                throw new DomainException(GenealogyProblemCodes.VALIDATION_FAILED,
                        "Nhãn gốc '" + label + "' đã được một chi khác dùng (kể cả chi đã xoá);"
                                + " hãy chọn nhãn khác");
            }
            result = roots.createRoot(UUID.randomUUID(), label, name, region);
            audit.record(ENTITY, result.id().toString(), AuditPort.Action.CREATE, null,
                    snapshot(result), List.of("name", "path", "branchKind", "region"),
                    "Khoi tao chi goc cua dong ho");
            log.info("Tao chi goc DONG_HO {} ({})", label, result.id());
            changed = true;
        } else {
            ClanRootPort.RootBranch before = existing.get();
            if (!Objects.equals(before.name(), name) || !Objects.equals(before.region(), region)) {
                roots.updateRoot(before.id(), name, region);
                result = new ClanRootPort.RootBranch(before.id(), label, name, region);
                audit.record(ENTITY, before.id().toString(), AuditPort.Action.UPDATE,
                        snapshot(before), snapshot(result), List.of("name", "region"),
                        "Dong bo chi goc theo ho so dong ho");
                changed = true;
            } else {
                result = before;
            }
        }

        if (changed) {
            // Ten/nhan cua chi goc nam trong khung xuong phả đồ da cache — bo het, re hon doan.
            treeCache.evictAll();
        }
        return ClanRootView.of(result);
    }

    private void relabel(String previousLabel, String label) {
        long persons = roots.countPersons(true);
        if (persons > 0) {
            throw new GenealogyConflictException(GenealogyProblemCodes.CLAN_ROOT_LOCKED,
                    "Phả đã có " + persons + " nhân khẩu; không đổi nhãn gốc được");
        }
        if (roots.pathTaken(label)) {
            throw new DomainException(GenealogyProblemCodes.VALIDATION_FAILED,
                    "Nhãn gốc '" + label + "' đã được một chi khác dùng; hãy chọn nhãn khác");
        }
        int moved = roots.relabelRoot(previousLabel, label);
        audit.record(ENTITY, previousLabel, AuditPort.Action.MOVE_BRANCH,
                Map.of("rootLabel", previousLabel), Map.of("rootLabel", label),
                List.of("path"), "Doi nhan goc dong ho: " + moved + " chi doi path");
        log.info("Doi nhan goc {} -> {}: {} chi", previousLabel, label, moved);
    }

    private static Map<String, Object> snapshot(ClanRootPort.RootBranch root) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", root.name());
        map.put("path", root.label());
        map.put("branchKind", "DONG_HO");
        map.put("region", root.region());
        return map;
    }
}
