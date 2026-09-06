package vn.giapha.kinship.application;

import vn.giapha.shared.exception.DomainException;

/**
 * Xung đột khi ghi đè bộ quy tắc danh xưng — nhóm {@code 409} của
 * {@code PUT /api/v1/kinship-rules}.
 *
 * <p>Tách riêng khỏi {@link DomainException} thường (vốn được ánh xạ sang {@code 422}) vì hợp đồng
 * đòi ba mã riêng và ba cách xử lý khác nhau ở giao diện: nạp lại dữ liệu, sửa chuỗi kế thừa, hay
 * bỏ bớt luật trùng. {@code KinshipRuleController} tự dịch ngoại lệ này sang Problem Details
 * {@code 409} — không sửa {@code GlobalExceptionHandler} của shared kernel cho một ca riêng của một
 * context.</p>
 */
public class RuleSetConflictException extends DomainException {

    private static final long serialVersionUID = 1L;

    /** {@code expectedVersion} không khớp — có người khác vừa sửa bộ luật này. */
    public static final String OPTIMISTIC_LOCK_CONFLICT = "OPTIMISTIC_LOCK_CONFLICT";
    /** {@code parentRuleSetId} tạo vòng kế thừa. */
    public static final String RULE_SET_CYCLE = "RULE_SET_CYCLE";
    /** Hai luật trùng {@code relationCode} trong cùng một bộ — khoá ghi đè bị nhập nhằng. */
    public static final String DUPLICATE_RULE = "DUPLICATE_RULE";

    public RuleSetConflictException(String code, String message) {
        super(code, message);
    }

    public static RuleSetConflictException optimisticLock(Object ruleSetId) {
        return new RuleSetConflictException(OPTIMISTIC_LOCK_CONFLICT,
                "Bo luat '" + ruleSetId + "' da bi nguoi khac thay doi, hay tai lai va thu lai");
    }

    public static RuleSetConflictException cycle(Object ruleSetId) {
        return new RuleSetConflictException(RULE_SET_CYCLE,
                "Chuoi ke thua bo luat tao thanh vong tai '" + ruleSetId + "'");
    }

    public static RuleSetConflictException duplicateRule(String relationCode) {
        return new RuleSetConflictException(DUPLICATE_RULE,
                "Ma quan he '" + relationCode + "' xuat hien nhieu hon mot lan trong cung mot bo luat");
    }
}
