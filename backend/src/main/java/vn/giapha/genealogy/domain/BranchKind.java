package vn.giapha.genealogy.domain;

/**
 * Bốn cấp phân nhánh của gia phả Việt, cộng cấp gốc.
 *
 * <p>Cố ý không dịch sang "branch / sub-branch": thứ bậc chi → ngành → cành → nhánh không có
 * tương đương tiếng Anh, dịch ra là mất thông tin.</p>
 */
public enum BranchKind {
    DONG_HO,
    CHI,
    NGANH,
    CANH,
    NHANH
}
