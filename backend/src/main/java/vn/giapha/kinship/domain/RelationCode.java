package vn.giapha.kinship.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import vn.giapha.shared.domain.ValueObject;

/**
 * Mã quan hệ ổn định của một luật danh xưng ({@code kinship_rule.relation_code}) — ví dụ
 * {@code BAC_TRAI_NOI}, {@code CHU_RUOT}, {@code THIM}, {@code CHAU_HO}.
 *
 * <p><b>Mã không phải danh xưng.</b> Danh xưng ("bác", "thím") là dữ liệu ở cột {@code title} và
 * dòng họ sửa được; mã chỉ là <i>khoá ghi đè</i>: bộ luật con ghi đè bộ cha khi trùng mã. Vì thế mã
 * là tập mở — Hội đồng Tộc biểu thêm mã mới được, và lớp này cố tình <b>không</b> phải enum.</p>
 *
 * <p>Các hằng số dưới đây chỉ là những mã hay được nhắc tới trong test và tài liệu; chúng không
 * giới hạn tập mã hợp lệ.</p>
 */
public record RelationCode(String value) implements ValueObject {

    private static final Pattern VALID = Pattern.compile("^[A-Z0-9_]{1,48}$");

    // ---- Trực hệ ----
    public static final RelationCode CHA = of("CHA");
    public static final RelationCode ME = of("ME");
    public static final RelationCode ONG_NOI = of("ONG_NOI");
    public static final RelationCode BA_NGOAI = of("BA_NGOAI");
    public static final RelationCode CON_TRAI = of("CON_TRAI");
    public static final RelationCode CHAU_NOI = of("CHAU_NOI");
    public static final RelationCode CHAU_NGOAI = of("CHAU_NGOAI");

    // ---- Cùng đời ----
    public static final RelationCode ANH_RUOT = of("ANH_RUOT");
    public static final RelationCode EM_GAI_RUOT = of("EM_GAI_RUOT");
    public static final RelationCode ANH_HO = of("ANH_HO");
    public static final RelationCode ANH_EM_CHUA_RO_VAI = of("ANH_EM_CHUA_RO_VAI");

    // ---- Bàng hệ đời trên ----
    public static final RelationCode BAC_TRAI_NOI = of("BAC_TRAI_NOI");
    public static final RelationCode CHU_RUOT = of("CHU_RUOT");
    public static final RelationCode CO_RUOT = of("CO_RUOT");
    public static final RelationCode CAU_RUOT = of("CAU_RUOT");
    public static final RelationCode DI_RUOT = of("DI_RUOT");
    public static final RelationCode BAC_HO_NOI = of("BAC_HO_NOI");
    public static final RelationCode BAC_CHU_NOI_CHUA_RO = of("BAC_CHU_NOI_CHUA_RO");

    // ---- Hôn nhân ----
    public static final RelationCode VO = of("VO");
    public static final RelationCode CHONG = of("CHONG");
    public static final RelationCode THIM = of("THIM");
    public static final RelationCode MO = of("MO");
    public static final RelationCode CON_DAU = of("CON_DAU");
    public static final RelationCode BO_CHONG = of("BO_CHONG");

    // ---- Nuôi / thừa tự ----
    public static final RelationCode BO_NUOI = of("BO_NUOI");
    public static final RelationCode CON_NUOI = of("CON_NUOI");
    public static final RelationCode DICH_TON = of("DICH_TON");
    public static final RelationCode CON_THUA_TU = of("CON_THUA_TU");
    public static final RelationCode CON_KE_TU = of("CON_KE_TU");

    // ---- Luật vét ----
    public static final RelationCode HO_HANG_NOI = of("HO_HANG_NOI");
    public static final RelationCode HO_HANG_XA = of("HO_HANG_XA");
    public static final RelationCode KHONG_XAC_DINH = of("KHONG_XAC_DINH");

    public RelationCode {
        Objects.requireNonNull(value, "relation_code khong duoc null");
        value = value.trim().toUpperCase(Locale.ROOT);
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "relation_code chi nhan [A-Z0-9_] toi da 48 ky tu: " + value);
        }
    }

    public static RelationCode of(String value) {
        return new RelationCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
