package vn.giapha.genealogy.application;

import java.util.UUID;

/**
 * Chi gốc của dòng họ nhìn từ ngoài context — named interface {@code "goc-dong-ho"}.
 *
 * <p>Chỉ gồm kiểu nguyên thuỷ: không {@code Branch}, không {@code BranchKind}, không
 * {@code Region} của domain. Xem javadoc {@link ClanRootService} về vì sao mặt tiền này tồn tại.</p>
 *
 * @param id     {@code branch.id} của chi gốc
 * @param label  nhãn {@code ltree} (= {@code path} = {@code slug})
 * @param name   tên hiển thị có dấu
 * @param region {@code BAC/TRUNG/NAM} hoặc {@code null}
 */
@org.springframework.modulith.NamedInterface("goc-dong-ho")
public record ClanRootView(UUID id, String label, String name, String region) {

    static ClanRootView of(ClanRootPort.RootBranch root) {
        return new ClanRootView(root.id(), root.label(), root.name(), root.region());
    }
}
