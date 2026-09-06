/** Shared sizing so every layout algorithm (dagre/d3/matrix) positions nodes
 * the same custom PersonNode component expects. Keep in sync with the actual
 * rendered card size in components/tree/person-node.tsx. */
export const NODE_WIDTH = 208;
export const NODE_HEIGHT = 96;

export const HIERARCHICAL_RANK_SEP = 96;
export const HIERARCHICAL_NODE_SEP = 32;

export const MATRIX_COL_GAP = 32;
export const MATRIX_ROW_HEIGHT = NODE_HEIGHT + 96;

export const RADIAL_RADIUS_STEP = 190;

/* --------------------------------------------------------------------------
   Bố cục theo ĐƠN VỊ GIA ĐÌNH (phả đồ dựng lại — Hội đồng đã duyệt).
   Các số này quyết định phả đồ có "thở" được hay không; xem bản thiết kế để
   biết vì sao chọn từng số.
--------------------------------------------------------------------------- */

/**
 * Khe giữa hai vợ chồng. Vừa là chiều dài thanh hôn phối, vừa là HÀNH LANG cho đường rơi xuống
 * con đi qua — nhờ nó mà đường nối không bao giờ gặp một tấm thẻ nào. Thu hẹp số này là làm hỏng
 * chính cơ chế đã sửa được lỗi "đường nối chui dưới thẻ".
 */
export const COUPLE_GAP = 48;

/**
 * Khe giữa hai gia đình khác nhau. PHẢI lớn hơn hẳn {@link HIERARCHICAL_NODE_SEP} (khe giữa anh em
 * ruột), nếu không các nhóm con dính vào nhau thành một dải liền và mất hẳn ranh giới gia đình.
 */
export const FAMILY_GAP = 80;

/**
 * Khoảng cách giữa hai đời. Tăng từ 96 vì nay phải chứa ba thứ chồng lên nhau: đoạn rơi từ thanh
 * hôn phối, thanh anh em, rồi đoạn rơi xuống thẻ con.
 */
export const FAMILY_RANK_SEP = 128;

/** Độ lệch tầng giữa các thanh anh em của những bà vợ khác nhau — để nhìn là biết ai con bà nào. */
export const SIBLING_BAR_STAGGER = 36;
