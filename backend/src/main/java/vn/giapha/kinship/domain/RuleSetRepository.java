package vn.giapha.kinship.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port đọc/ghi bộ quy tắc danh xưng.
 *
 * <p>Hiện thực là {@code RuleSetJpaRepository}, có nhiệm vụ dựng sẵn chuỗi kế thừa
 * {@code DEFAULT → REGION → CLAN → BRANCH} rồi trả về bộ luật lá; phép hợp nhất thực sự nằm ở
 * {@link KinshipRuleSet#effectiveRules()} — tức là ở domain, test được không cần CSDL.</p>
 */
public interface RuleSetRepository {

    /**
     * Bộ luật hiệu lực cho một chi/ngành, đã nối sẵn chuỗi cha.
     *
     * <p>Cách chọn: bộ {@code BRANCH} của đúng chi đó; không có thì leo lên theo {@code ltree} tìm
     * bộ của chi cha; không có nữa thì bộ {@code CLAN} của dòng họ; rồi bộ {@code REGION} theo vùng
     * của chi; cuối cùng là bộ {@code DEFAULT}.</p>
     *
     * @param branchId chi/ngành của <b>người hỏi</b> (ego). {@code null} thì dùng bộ DEFAULT
     */
    KinshipRuleSet resolveFor(UUID branchId);

    /** Bộ mặc định do hệ thống sở hữu (miền Bắc). Luôn tồn tại sau khi seed. */
    KinshipRuleSet defaultRuleSet();

    /** Một bộ luật cụ thể kèm chuỗi cha — dùng cho màn hình thử nghiệm luật của Hội đồng. */
    Optional<KinshipRuleSet> byId(UUID ruleSetId);

    /** Liệt kê bộ luật thô (chưa hợp nhất) để quản trị. Tham số {@code null} là không lọc. */
    List<KinshipRuleSet> findAll(RuleScope scope, Region region, UUID branchId);

    /**
     * Ghi đè <b>trọn vẹn</b> một bộ luật: danh sách {@code rules} gửi lên thay thế toàn bộ luật
     * hiện có của bộ đó. Ngữ nghĩa replace chứ không phải patch — nhờ vậy "bộ luật đang hiệu lực
     * là gì" luôn xác định được.
     *
     * @throws IllegalStateException khi {@code expectedVersion} không khớp (optimistic lock)
     */
    KinshipRuleSet replace(RuleSetCommand command);

    /**
     * Đổi <b>cha kế thừa</b> (và cột vùng) của một bộ luật, <b>không đụng tới luật của nó</b>.
     *
     * <p>Khác {@link #replace}: replace xoá rồi chèn lại toàn bộ luật và ghi đè cả mô tả. Lối đổi
     * cha này sinh ra cho lúc dòng họ đổi vùng miền — khi Hội đồng đã tự sửa luật ở tầng
     * {@code CLAN} thì những luật ấy là công của họ, và "đổi vùng" không được là cái cớ để xoá
     * chúng đi rồi chèn lại (mất id, mất mô tả, mất dấu thời gian).</p>
     *
     * @return bộ luật sau khi đổi, đã nối lại chuỗi cha
     */
    KinshipRuleSet reparent(UUID ruleSetId, UUID parentRuleSetId, Region region);

    /**
     * Lệnh ghi đè một bộ luật.
     *
     * @param id              {@code null} = tạo mới
     * @param expectedVersion phiên bản lấy từ lần đọc trước, dùng để khoá lạc quan
     */
    record RuleSetCommand(
            UUID id,
            String code,
            String name,
            RuleScope scope,
            Region region,
            UUID branchId,
            UUID parentRuleSetId,
            String description,
            List<KinshipRule> rules,
            Long expectedVersion) {
    }
}
