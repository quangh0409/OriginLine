package vn.giapha.kinship.domain;

/**
 * Chiều của quan hệ hôn nhân gián tiếp ({@code kinship_rule.in_law_direction}).
 *
 * <p>Quan hệ dâu/rể không tính thẳng bằng LCA mà đi vòng qua <b>NGƯỜI NỐI</b> — người vừa có huyết
 * thống với một trong hai đầu, vừa là vợ/chồng của đầu kia.</p>
 */
public enum InLawDirection {

    /**
     * Alter (B) là vợ/chồng của một người ruột thịt của ego (A) — thím, mợ, chị dâu, con dâu,
     * con rể... Người nối là người ruột thịt đó của A.
     */
    ALTER_IS_SPOUSE,

    /**
     * Ego (A) là dâu/rể; alter (B) là người ruột thịt của vợ/chồng A — bố chồng, mẹ vợ...
     * Người nối là vợ/chồng của A.
     */
    EGO_IS_SPOUSE;

    public static InLawDirection fromDb(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
