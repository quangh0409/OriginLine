package vn.giapha.events.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventSubject;
import vn.giapha.events.domain.port.EventRepository;
import vn.giapha.events.domain.port.EventSubjectPort;

/**
 * Dọn <b>phạm vi người nhận</b> của những sự kiện cũ không nói được mình thuộc về ai.
 *
 * <h2>Vấn đề</h2>
 * {@code ck_event_scope} (V4) chỉ cấm đặt <i>cả hai</i> ({@code is_clan_level} và
 * {@code target_branch_id}); nó <b>không</b> bắt buộc phải có <i>một</i>. Lối ghi thủ công (V18 trở
 * đi) đóng cửa ấy cho bản ghi <b>mới</b> — {@code EventScope} từ chối một lượt ghi bỏ trống cả hai.
 * Nhưng những dòng có trước thì vẫn nằm đó, và khi tới giờ nhắc,
 * {@code NotificationDispatchService.resolveRecipients} mở chúng ra <b>cả dòng họ</b>: với một họ
 * 1.500 người, mỗi dòng như thế là 1.500 thông báo mà không ai yêu cầu.
 *
 * <p>Lựa chọn "nhắc rộng còn hơn nhắc thiếu" ở chỗ gửi là <b>đúng</b> và không đổi: thừa một thông
 * báo thì có người phàn nàn rồi dữ liệu được sửa, thiếu một thông báo thì không ai biết cho tới khi
 * cái giỗ đã qua. Lớp này là nửa còn lại của lựa chọn ấy — <b>đi sửa dữ liệu</b>, để lối gửi không
 * phải đoán mãi.
 *
 * <h2>Vì sao là lệnh quản trị chứ không phải migration</h2>
 * Một migration siết ràng buộc ({@code CHECK (is_clan_level OR target_branch_id IS NOT NULL)}) sẽ
 * chặn luôn cả <b>lượt xoá mềm</b> một dòng cũ: xoá mềm là một câu {@code UPDATE}, và ràng buộc
 * chạy lại trên cả dòng — nên Hội đồng sẽ không gỡ được đúng những bản ghi hỏng mà ràng buộc ấy
 * sinh ra để chỉ mặt. Ngoài ra phép sửa ở đây <b>đọc hồ sơ nhân khẩu</b> để suy ra chi, việc mà SQL
 * trong migration làm được nhưng làm bằng một bản sao thứ hai của luật.
 *
 * <h2>Chạy lại được nhiều lần mà không hỏng</h2>
 * Điều kiện lọc chính là điều kiện vi phạm, nên lượt chạy thứ hai không tìm thấy dòng nào và trả về
 * {@code changed = 0}. {@code dryRun} đếm mà không ghi — bắt buộc phải có trước một lệnh chạm vào
 * hàng nghìn người.
 *
 * <h2>Hai luật suy ra, theo đúng thứ tự</h2>
 * <ol>
 *   <li>Sự kiện <b>gắn nhân khẩu</b> và nhân khẩu ấy có chi → lấy chi của nhân khẩu. Đây là ca của
 *       mọi cái giỗ, tức tuyệt đại đa số.</li>
 *   <li>Còn lại → đánh dấu <b>cấp dòng họ</b> tường minh. Không phải một phỏng đoán mới: đó đúng
 *       bằng hành vi mà lối gửi đang làm hôm nay. Khác biệt là sau lượt chạy này nó nằm
 *       <b>trong dữ liệu</b>, đọc được, sửa được, và có vết trong {@code audit_log} — thay vì nằm
 *       trong một câu {@code if} mà không ai đọc.</li>
 * </ol>
 */
@Service
public class EventScopeBackfillService {

    private static final Logger log = LoggerFactory.getLogger(EventScopeBackfillService.class);

    private static final String AUDIT_ENTITY = "event";

    private final EventRepository events;
    private final EventSubjectPort subjects;
    private final AuditTrailService audit;

    public EventScopeBackfillService(EventRepository events, EventSubjectPort subjects,
                                     AuditTrailService audit) {
        this.events = events;
        this.subjects = subjects;
        this.audit = audit;
    }

    /**
     * @param dryRun đếm mà không ghi
     * @return số dòng đã quét / đã sửa, tách theo hai luật
     */
    @Transactional
    public EventScopeBackfillResult run(boolean dryRun) {
        List<Event> candidates = new ArrayList<>();
        for (Event event : events.search(null, null, null, false)) {
            if (!event.isClanLevel() && event.targetBranchId() == null) {
                candidates.add(event);
            }
        }
        int toBranch = 0;
        int toClan = 0;
        for (Event event : candidates) {
            Optional<UUID> branchId = branchOfSubject(event);
            if (dryRun) {
                if (branchId.isPresent()) {
                    toBranch++;
                } else {
                    toClan++;
                }
                continue;
            }
            if (branchId.isPresent()) {
                apply(event, event.toBuilder().targetBranchId(branchId.get()).build(),
                        "Bo sung chi/nganh cho su kien cu khong co pham vi (theo ho so nhan khau)");
                toBranch++;
            } else {
                apply(event, event.toBuilder().clanLevel(true).targetBranchId(null).build(),
                        "Danh dau tuong minh cap dong ho cho su kien cu khong co pham vi");
                toClan++;
            }
        }
        log.info("Don pham vi su kien cu (dryRun={}): quet {} dong khong co pham vi, gan chi {},"
                + " danh dau cap dong ho {}", dryRun, candidates.size(), toBranch, toClan);
        return new EventScopeBackfillResult(candidates.size(), toBranch, toClan, dryRun);
    }

    private Optional<UUID> branchOfSubject(Event event) {
        if (event.personId() == null) {
            return Optional.empty();
        }
        return subjects.findPerson(event.personId())
                .map(EventSubject::primaryBranch)
                .map(EventSubject.BranchSnapshot::id);
    }

    private void apply(Event before, Event after, String reason) {
        Event saved = events.update(after, before.version());
        audit.record(AUDIT_ENTITY, saved.id().toString(), AuditAction.UPDATE,
                Map.of("isClanLevel", String.valueOf(before.isClanLevel()),
                        "targetBranchId", String.valueOf(before.targetBranchId())),
                Map.of("isClanLevel", String.valueOf(saved.isClanLevel()),
                        "targetBranchId", String.valueOf(saved.targetBranchId())),
                List.of("isClanLevel", "targetBranchId"), reason);
    }
}
