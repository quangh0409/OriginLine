package vn.giapha.genealogy.domain;

/**
 * Vùng miền của một chi — đầu vào chọn bộ quy tắc danh xưng cấp {@code REGION} (FR-1.3a).
 *
 * <p>Giai đoạn 1 chỉ có bộ luật miền Bắc; {@link #TRUNG} và {@link #NAM} để trống, bổ sung ở
 * GĐ2–3. Context {@code kinship} là nơi dùng giá trị này; {@code genealogy} chỉ lưu trữ.</p>
 */
public enum Region {
    BAC,
    TRUNG,
    NAM
}
