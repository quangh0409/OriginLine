package vn.giapha.genealogy.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.TabooConflict;
import vn.giapha.genealogy.domain.port.TabooNamePort;

/**
 * Kiểm tra <b>kỵ húy</b> (FR-1.6) cho một lô tên trước khi ghi.
 *
 * <p>Kỵ húy là tục kiêng gọi tên thật của bậc trên; đặt trùng tên húy của một cụ bị xem là thất
 * kính. Hệ thống <b>cảnh báo chứ không cấm</b> - thẩm quyền quyết định thuộc về dòng họ, nên luồng
 * là hai bước: lần gọi đầu ném {@link TabooNameConflictException} và <b>không ghi gì cả</b>, lần
 * gọi sau kèm cờ xác nhận thì ghi và lưu lý do ghi đè vào {@code audit_log}.</p>
 *
 * <p>Chỉ soi tên lớp {@code HUY}. Các lớp tên khác (tự, hiệu, thụy, thường gọi, pháp danh) trùng
 * nhau không phạm huý, và quét cả chúng chỉ tạo ra một biển cảnh báo giả khiến người dùng bấm
 * "vẫn ghi" theo phản xạ - lúc đó cảnh báo mất sạch giá trị.</p>
 */
@Component
public class TabooNameChecker {

    private static final Logger log = LoggerFactory.getLogger(TabooNameChecker.class);

    private final TabooNamePort tabooNames;

    public TabooNameChecker(TabooNamePort tabooNames) {
        this.tabooNames = tabooNames;
    }

    /**
     * @param names       danh sách tên định ghi
     * @param generation  đời thứ dự kiến; {@code null} khi nhân khẩu chưa nối vào cây, khi đó phạm
     *                    vi quét là toàn dòng họ vì không xác định được ai là bậc trên
     * @param excludeId   nhân khẩu đang sửa, để không tự báo trùng với chính mình
     * @param confirmed   người dùng đã xác nhận ghi đè
     * @return chuỗi mô tả các va chạm để ghi vào {@code audit_log}; {@code null} khi không có gì
     * @throws TabooNameConflictException khi có va chạm mà chưa được xác nhận
     */
    public String check(List<PersonName> names, Integer generation, UUID excludeId, boolean confirmed) {
        List<TabooConflict> conflicts = findConflicts(names, generation, excludeId);
        if (conflicts.isEmpty()) {
            return null;
        }
        if (!confirmed) {
            throw new TabooNameConflictException(conflicts);
        }
        String summary = conflicts.stream()
                .map(c -> c.tabooName() + " (" + c.ancestorPersonId() + ")")
                .collect(Collectors.joining("; "));
        log.info("Ghi de canh bao ky huy cho {} va cham, da co xac nhan cua nguoi dung", conflicts.size());
        return "Ghi de canh bao ky huy: " + summary;
    }

    private List<TabooConflict> findConflicts(List<PersonName> names, Integer generation, UUID excludeId) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        List<TabooConflict> all = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (PersonName name : names) {
            if (!name.isTabooName() || !seen.add(name.fullName())) {
                continue;
            }
            all.addAll(tabooNames.findConflicts(name.fullName(), generation, excludeId));
        }
        return all;
    }
}
