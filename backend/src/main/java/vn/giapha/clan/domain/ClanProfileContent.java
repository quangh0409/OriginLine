package vn.giapha.clan.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Phần <b>ghi được</b> của hồ sơ dòng họ — đúng những gì {@code PUT /admin/clan-profile} nhận.
 *
 * <p>Tách khỏi {@link ClanProfile} (vốn thêm id, thời điểm, phiên bản) để phép so "đổi những trường
 * nào" — thứ đi vào {@code audit_log.changed_fields} — là phép so giữa hai giá trị bất biến, không
 * phải giữa một thực thể và một lệnh.</p>
 *
 * <p>Ngữ nghĩa <b>thay thế trọn vẹn</b> (PUT), không phải vá: một trường tuỳ chọn vắng mặt nghĩa là
 * <i>xoá</i> giá trị đang có. Chọn như vậy vì màn hình luôn gửi cả biểu mẫu, và vì "PUT thiếu
 * trường thì giữ giá trị cũ" là thứ ngữ nghĩa không ai đoán đúng khi đọc hợp đồng.</p>
 *
 * @param name         tên dòng họ, bắt buộc, tối đa 120 ký tự — ví dụ "Họ Nguyễn Đình làng Đông Ngạc"
 * @param shortName    tên ngắn cho tiêu đề hẹp, tối đa 60 ký tự
 * @param root         nhãn {@code ltree} gốc
 * @param region       vùng xưng hô, quyết định cha kế thừa của bộ luật tầng {@code CLAN}
 * @param hall         từ đường, có thể {@code null}
 * @param ancestorName tên thuỷ tổ, tối đa 200 ký tự
 * @param foundedNote  ghi chú lập họ / khai canh, tối đa 2000 ký tự
 * @param gioTo        ngày giỗ tổ (âm lịch), có thể {@code null}
 */
public record ClanProfileContent(String name, String shortName, RootBranchLabel root,
                                 ClanRegion region, AncestralHall hall, String ancestorName,
                                 String foundedNote, GioTo gioTo) {

    public static final int NAME_MAX = 120;
    public static final int SHORT_NAME_MAX = 60;
    public static final int ANCESTOR_NAME_MAX = 200;
    public static final int FOUNDED_NOTE_MAX = 2000;

    public ClanProfileContent {
        name = Texts.required(name, NAME_MAX, "name");
        shortName = Texts.optional(shortName, SHORT_NAME_MAX, "shortName");
        root = Objects.requireNonNull(root, "Thieu rootBranchPath");
        if (region == null) {
            throw new IllegalArgumentException("Thiếu region (BAC/TRUNG/NAM)");
        }
        ancestorName = Texts.optional(ancestorName, ANCESTOR_NAME_MAX, "ancestorName");
        foundedNote = Texts.optional(foundedNote, FOUNDED_NOTE_MAX, "foundedNote");
    }

    /**
     * Tên các trường khác nhau giữa {@code this} (bản mới) và {@code before} (bản cũ), theo tên
     * trường của hợp đồng API — để một người đọc {@code audit_log} đối chiếu được với màn hình.
     */
    public List<String> changedFrom(ClanProfileContent before) {
        List<String> changed = new ArrayList<>();
        if (before == null) {
            changed.addAll(snapshot().keySet());
            return changed;
        }
        Map<String, Object> now = snapshot();
        Map<String, Object> old = before.snapshot();
        for (Map.Entry<String, Object> entry : now.entrySet()) {
            if (!Objects.equals(entry.getValue(), old.get(entry.getKey()))) {
                changed.add(entry.getKey());
            }
        }
        return changed;
    }

    public boolean rootDiffersFrom(ClanProfileContent before) {
        return before != null && !before.root().equals(root);
    }

    /**
     * Ảnh chụp cho {@code audit_log}. Không có trường Tầng 3 nào: hồ sơ dòng họ là dữ liệu công
     * khai của cả họ, không phải của một người đang sống.
     *
     * <p>{@link LinkedHashMap} chứ không {@code Map.of}: giá trị {@code null} là hợp lệ ở đây
     * ("chưa có từ đường"), và {@code Map.of}/{@code Map.copyOf} ném NPE trên {@code null} — cái bẫy
     * đã từng làm hỏng cả luồng đính chính.</p>
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("shortName", shortName);
        map.put("rootBranchPath", root.value());
        map.put("region", region.name());
        map.put("ancestralHall.name", hall == null ? null : hall.name());
        map.put("ancestralHall.address", hall == null ? null : hall.address());
        map.put("ancestorName", ancestorName);
        map.put("foundedNote", foundedNote);
        map.put("gioTo.lunarDay", gioTo == null ? null : gioTo.lunarDay());
        map.put("gioTo.lunarMonth", gioTo == null ? null : gioTo.lunarMonth());
        return map;
    }
}
