package vn.giapha.clan.domain;

import java.util.regex.Pattern;

/**
 * Nhãn {@code ltree} gốc của dòng họ — <b>đúng một nhãn</b>, ví dụ {@code ho_nguyen_dinh}.
 *
 * <h2>Vì sao luật chữ ở đây trùng khít {@code ck_branch_slug_ltree_safe}</h2>
 * Nhãn này trở thành {@code slug} của chi gốc {@code DONG_HO} và là tiền tố của mọi
 * {@code branch.path} trong họ — tức là phạm vi RBAC của mọi Trưởng chi. Nếu luật ở đây rộng hơn
 * luật của {@code branch} thì hồ sơ lưu được còn chi gốc thì không, và lỗi lộ ra dưới dạng một
 * vi phạm ràng buộc CSDL thay vì một câu nói rõ phải sửa gì. Không nhận chữ hoa, không nhận dấu
 * chấm (dấu chấm là phân cách cấp của {@code ltree}, tức "nhiều nhãn").
 *
 * @param value nhãn đã chuẩn hoá (cắt khoảng trắng hai đầu)
 */
public record RootBranchLabel(String value) {

    private static final Pattern ONE_LABEL = Pattern.compile("^[a-z0-9_]{1,64}$");

    public RootBranchLabel {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Thiếu nhãn gốc ltree của dòng họ (rootBranchPath)");
        }
        value = value.trim();
        if (!ONE_LABEL.matcher(value).matches()) {
            throw new IllegalArgumentException("rootBranchPath phải là MỘT nhãn ltree gồm chữ thường"
                    + " không dấu, chữ số và dấu gạch dưới, tối đa 64 ký tự (ví dụ ho_nguyen_dinh)");
        }
    }

    public static RootBranchLabel of(String raw) {
        return new RootBranchLabel(raw);
    }

    @Override
    public String toString() {
        return value;
    }
}
