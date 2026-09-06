package vn.giapha.kinship.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.config.RedisConfig;
import vn.giapha.kinship.domain.KinshipRule;
import vn.giapha.kinship.domain.KinshipRuleSet;
import vn.giapha.kinship.domain.Region;
import vn.giapha.kinship.domain.RelationCode;
import vn.giapha.kinship.domain.RuleScope;
import vn.giapha.kinship.domain.RuleSetRepository;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;

/**
 * Quản trị bộ quy tắc danh xưng cho <b>Hội đồng Tộc biểu</b> (FR-1.3a).
 *
 * <p><b>Ngữ nghĩa ghi đè trọn vẹn, không phải patch.</b> Danh sách {@code rules} gửi lên thay thế
 * toàn bộ luật hiện có của bộ đó; muốn xoá một luật thì gửi danh sách thiếu luật ấy. Chọn như vậy
 * để câu hỏi "bộ luật đang hiệu lực là gì" luôn có một câu trả lời xác định — với patch từng luật
 * thì trạng thái hiệu lực phụ thuộc lịch sử các lần gọi, và Hội đồng sẽ không bao giờ tự kiểm
 * chứng được.</p>
 *
 * <p><b>Quyền hẹp là cố ý:</b> chỉ {@code COUNCIL} và {@code ADMIN}. Danh xưng sai là chuyện mất
 * mặt với dòng họ, nên Trưởng chi cũng không được tự sửa — họ đề nghị, Hội đồng duyệt.</p>
 *
 * <p><b>Bộ {@code DEFAULT} của hệ thống bất biến.</b> Muốn khác đi thì tạo bộ ghi đè cấp
 * {@code CLAN}/{@code BRANCH}; như vậy luôn còn một mốc chuẩn để đối chiếu khi một chi sửa hỏng.</p>
 */
@Service
public class RuleAdminService {

    private static final Logger log = LoggerFactory.getLogger(RuleAdminService.class);

    /** Chặn chuỗi kế thừa hỏng khi kiểm tra vòng, cùng trần với {@code KinshipRuleSet}. */
    private static final int MAX_CHAIN_HOPS = 32;

    private final RuleSetRepository ruleSets;
    private final CacheManager cacheManager;

    public RuleAdminService(RuleSetRepository ruleSets, CacheManager cacheManager) {
        this.ruleSets = ruleSets;
        this.cacheManager = cacheManager;
    }

    /** Liệt kê bộ luật <b>thô</b> (chưa hợp nhất) cho màn hình quản trị. Tham số null = không lọc. */
    @Transactional(readOnly = true)
    public List<KinshipRuleSet> list(RuleScope scope, Region region, UUID branchId) {
        return ruleSets.findAll(scope, region, branchId);
    }

    @Transactional(readOnly = true)
    public KinshipRuleSet byId(UUID ruleSetId) {
        return ruleSets.byId(ruleSetId)
                .orElseThrow(() -> NotFoundException.of("KinshipRuleSet", ruleSetId));
    }

    /**
     * Ghi đè trọn vẹn một bộ luật.
     *
     * @return bộ luật sau khi ghi, kèm cờ cho biết là tạo mới ({@code 201}) hay cập nhật
     *         ({@code 200})
     */
    @PreAuthorize("hasAnyRole('COUNCIL','ADMIN')")
    @Transactional
    public Result replace(RuleSetRepository.RuleSetCommand command) {
        validateScope(command);
        validateNoDuplicateRelationCode(command.rules());
        rejectSystemDefault(command);
        validateNoCycle(command);

        boolean created = command.id() == null;
        KinshipRuleSet saved;
        try {
            saved = ruleSets.replace(command);
        } catch (OptimisticLockingFailureException ex) {
            throw RuleSetConflictException.optimisticLock(command.id());
        } catch (IllegalStateException ex) {
            // Hợp đồng của RuleSetRepository.replace: expectedVersion lệch thì ném
            // IllegalStateException. Không để nó rơi xuống handler chung (500) — đây là 409.
            throw RuleSetConflictException.optimisticLock(command.id());
        }

        // TODO (W6 - audit): hợp đồng đòi ghi audit_log before/after cho TOÀN BỘ bộ luật mỗi lần
        // ghi đè. Context `audit` chưa công bố service nào (mới chỉ có package-info), nên tạm thời
        // chỉ có dòng log dưới đây. Khi audit có AuditService thì gọi vào đây, kèm `note` của
        // request làm lý do thay đổi. `note` cố ý KHÔNG được ghi vào bộ luật - mô tả bộ luật là
        // trường `description` riêng, xem javadoc KinshipRuleSetUpdateRequest.

        // Bộ luật đổi thì mọi danh xưng đã cache đều đáng ngờ. Vân tay trong khoá cache đã làm cho
        // mục cũ không-thể-với-tới, nhưng vẫn xoá chủ động để không giữ rác 6 tiếng trong Redis và
        // để phòng trường hợp luật bị sửa thẳng dưới CSDL rồi version không nhích.
        evictKinshipCaches();

        log.info("Bo luat danh xung {} ({}) da duoc ghi de: {} luat, version {}",
                saved.code(), saved.scope(), saved.ownRules().size(), saved.version());
        return new Result(saved, created);
    }

    /** Xoá cache danh xưng và cache bộ luật — công khai để tác vụ nhập liệu hàng loạt gọi lại được. */
    public void evictKinshipCaches() {
        clear(RedisConfig.CACHE_KINSHIP);
        clear(RedisConfig.CACHE_KINSHIP_RULES);
    }

    private void clear(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return;
        }
        try {
            cache.clear();
        } catch (RuntimeException ex) {
            // Redis chết không được chặn việc Hội đồng sửa luật — dữ liệu đã nằm trong Postgres.
            log.warn("Khong xoa duoc cache '{}': {}", cacheName, ex.getMessage());
        }
    }

    /** Cấp áp dụng phải đi kèm đúng thứ định danh nó, nếu không "bộ nào áp cho ai" sẽ mơ hồ. */
    private void validateScope(RuleSetRepository.RuleSetCommand command) {
        if (command.scope() == null) {
            throw new DomainException("VALIDATION_FAILED", "Thieu 'scope' cua bo luat");
        }
        if (command.scope() == RuleScope.REGION && command.region() == null) {
            throw new DomainException("VALIDATION_FAILED", "scope = REGION thi bat buoc co 'region'");
        }
        if (command.scope() == RuleScope.BRANCH && command.branchId() == null) {
            throw new DomainException("VALIDATION_FAILED", "scope = BRANCH thi bat buoc co 'branchId'");
        }
    }

    /**
     * {@code relationCode} là <b>khoá ghi đè</b> giữa các cấp bộ luật (xem
     * {@code KinshipRuleSet.effectiveRules()}), nên trùng mã trong cùng một bộ khiến kết quả hợp
     * nhất phụ thuộc thứ tự chèn. CSDL cũng có unique index; chặn sớm ở đây để trả 409 có nghĩa
     * thay vì một lỗi ràng buộc thô.
     */
    private void validateNoDuplicateRelationCode(List<KinshipRule> rules) {
        if (rules == null) {
            return;
        }
        Set<RelationCode> seen = new HashSet<>();
        for (KinshipRule rule : rules) {
            if (!seen.add(rule.relationCode())) {
                throw RuleSetConflictException.duplicateRule(rule.relationCode().value());
            }
        }
    }

    private void rejectSystemDefault(RuleSetRepository.RuleSetCommand command) {
        if (command.scope() == RuleScope.DEFAULT) {
            throw immutable();
        }
        if (command.id() == null) {
            return;
        }
        Optional<KinshipRuleSet> existing = ruleSets.byId(command.id());
        if (existing.isEmpty()) {
            throw NotFoundException.of("KinshipRuleSet", command.id());
        }
        if (existing.get().scope() == RuleScope.DEFAULT) {
            throw immutable();
        }
    }

    private ForbiddenException immutable() {
        return new ForbiddenException("SYSTEM_RULE_SET_IMMUTABLE",
                "Bo luat DEFAULT do he thong so huu, khong sua duoc. "
                        + "Hay tao bo ghi de o cap CLAN hoac BRANCH.");
    }

    /**
     * Leo chuỗi cha từ {@code parentRuleSetId} lên; gặp lại chính bộ đang ghi là vòng. Không có
     * bước này thì {@code KinshipRuleSet.chain()} sẽ ném lỗi ở <b>đường đọc</b> — tức là hỏng cả
     * việc tra danh xưng chứ không chỉ hỏng một lần ghi.
     */
    private void validateNoCycle(RuleSetRepository.RuleSetCommand command) {
        UUID self = command.id();
        UUID parent = command.parentRuleSetId();
        if (parent == null) {
            return;
        }
        if (parent.equals(self)) {
            throw RuleSetConflictException.cycle(parent);
        }
        Set<UUID> visited = new HashSet<>();
        UUID current = parent;
        int hops = 0;
        while (current != null) {
            if (++hops > MAX_CHAIN_HOPS || !visited.add(current)) {
                throw RuleSetConflictException.cycle(current);
            }
            Optional<KinshipRuleSet> set = ruleSets.byId(current);
            if (set.isEmpty()) {
                throw NotFoundException.of("KinshipRuleSet", current);
            }
            KinshipRuleSet parentSet = set.get().parent();
            current = parentSet == null ? null : parentSet.id();
            if (self != null && self.equals(current)) {
                throw RuleSetConflictException.cycle(self);
            }
        }
    }

    /** Danh sách bộ luật kèm cờ tạo mới, để controller chọn giữa {@code 200} và {@code 201}. */
    public record Result(KinshipRuleSet ruleSet, boolean created) {
    }

    /** Tiện ích cho controller: cắt trang trong bộ nhớ — số bộ luật của một dòng họ luôn nhỏ. */
    public static <T> List<T> page(List<T> all, int page, int size) {
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new ArrayList<>(all.subList(from, to));
    }
}
