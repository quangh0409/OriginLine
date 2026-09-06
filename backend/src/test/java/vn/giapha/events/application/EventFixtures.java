package vn.giapha.events.application;

import java.time.LocalDate;
import java.util.UUID;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventSubject;
import vn.giapha.events.domain.EventType;
import vn.giapha.shared.vo.LunarDate;

/** Dựng sẵn các sự kiện thường gặp để bài test nói về nghiệp vụ chứ không về builder. */
final class EventFixtures {

    private EventFixtures() {
    }

    /** Giỗ một cụ: theo âm lịch, lặp hằng năm, gắn với nhân khẩu và một chi. */
    static Event gio(UUID eventId, UUID personId, LunarDate lunar, UUID branchId) {
        return Event.builder(eventId)
                .type(EventType.GIO)
                .personId(personId)
                .title("Gio cu Nguyen Van Duc")
                .lunarDate(lunar)
                .lunarBased(true)
                .recurring(true)
                .targetBranchId(branchId)
                .location("Tu duong ho Nguyen")
                .build();
    }

    /** Giỗ Tổ cấp dòng họ — cả họ nhận nhắc, không gắn chi nào. */
    static Event gioTo(UUID eventId, LunarDate lunar) {
        return Event.builder(eventId)
                .type(EventType.GIO_TO)
                .title("Gio To ho Nguyen")
                .lunarDate(lunar)
                .lunarBased(true)
                .recurring(true)
                .clanLevel(true)
                .build();
    }

    /** Sự kiện theo dương lịch (họp họ thường niên). */
    static Event solar(UUID eventId, LocalDate solar, boolean recurring) {
        return Event.builder(eventId)
                .type(EventType.HOP_HO)
                .title("Hop ho thuong nien")
                .solarDate(solar)
                .lunarBased(false)
                .recurring(recurring)
                .clanLevel(true)
                .build();
    }

    /** Nhân khẩu đã khuất, có {@code death_lunar} — nguồn chân lý của ngày giỗ. */
    static EventSubject deceased(UUID personId, LunarDate deathLunar, EventSubject.BranchSnapshot branch) {
        return new EventSubject(personId, "Nguyen Van Duc", "Nguyen Van Duc (Han-Nom)", 3, false,
                deathLunar, branch);
    }

    /**
     * Nhân khẩu <b>còn sống</b> — dùng cho ca đã đính chính "thật ra cụ còn sống".
     *
     * <p>{@code death_lunar} bị gỡ khỏi hồ sơ nhưng bản ghi {@code event} kiểu GIO vẫn giữ ngày âm
     * của riêng nó; đây chính là hình dạng dữ liệu để lộ ra lỗi nhắc giỗ người đang sống.</p>
     */
    static EventSubject alive(UUID personId, EventSubject.BranchSnapshot branch) {
        return new EventSubject(personId, "Nguyen Van Duc", "Nguyen Van Duc (Han-Nom)", 3, true,
                null, branch);
    }

    static EventSubject.BranchSnapshot branch(UUID id, String path) {
        return new EventSubject.BranchSnapshot(id, "Chi " + path, path, "BAC");
    }
}
