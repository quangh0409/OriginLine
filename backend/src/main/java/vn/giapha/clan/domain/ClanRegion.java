package vn.giapha.clan.domain;

import java.util.Locale;

/**
 * Vùng xưng hô của dòng họ — Bắc / Trung / Nam.
 *
 * <p>Không chỉ là nhãn hiển thị: nó quyết định bộ luật danh xưng tầng {@code CLAN} <b>kế thừa từ
 * đâu</b> (bộ {@code REGION} cùng vùng, không có thì bộ {@code DEFAULT} — vốn là lối miền Bắc).
 * Khớp {@code ck_clan_profile_region} ở V23 và {@code ck_rule_set_region} ở V3.</p>
 */
public enum ClanRegion {

    BAC,
    TRUNG,
    NAM;

    /**
     * @throws IllegalArgumentException khi chuỗi không phải một vùng đã biết — tầng application
     *         dịch thành {@code VALIDATION_FAILED}
     */
    public static ClanRegion parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Thiếu vùng miền (BAC/TRUNG/NAM)");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Vùng miền không hợp lệ: " + raw
                    + " (chỉ nhận BAC/TRUNG/NAM)", ex);
        }
    }
}
