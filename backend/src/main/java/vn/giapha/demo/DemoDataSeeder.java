package vn.giapha.demo;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import vn.giapha.calendar.application.LunarCalendarService;
import vn.giapha.demo.generator.CalendarLunarDateSource;
import vn.giapha.demo.generator.ClanTreeGenerator;
import vn.giapha.demo.generator.DemoSeedConfig;
import vn.giapha.demo.model.DemoDataset;
import vn.giapha.demo.writer.DemoAccountAndEventWriter;
import vn.giapha.demo.writer.DemoDataWriter;
import vn.giapha.demo.writer.DemoWriteResult;

/**
 * Nạp bộ dữ liệu gia phả giả khi ứng dụng khởi động dưới profile {@code demo}.
 *
 * <h2>Vòng đời một lượt nạp</h2>
 * <ol>
 *   <li>{@link DemoEnvironmentGuard} — chặn tuyệt đối nếu đang chạy chung profile môi trường thật;</li>
 *   <li>sinh dữ liệu trong bộ nhớ, ngày âm quy đổi qua {@link LunarCalendarService};</li>
 *   <li>{@link DemoEdgeCaseQuota} — đủ ca biên bắt buộc thì mới ghi;</li>
 *   <li>{@link DemoDataWriter} — ghi bảng và đồ thị AGE trong CÙNG một transaction.</li>
 * </ol>
 *
 * <h2>Chạy sau Flyway, trước khi phục vụ request</h2>
 * <p>{@code ApplicationRunner} chạy sau khi context đã sẵn sàng, nên schema đã được Flyway dựng
 * xong. Ném lỗi ở đây làm ứng dụng dừng hẳn — cố ý: một môi trường demo không có dữ liệu thì không
 * demo được gì, thà biết ngay còn hơn phát hiện lúc mở phả đồ ra trước mặt Hội đồng Tộc biểu.</p>
 */
@Component
@Profile("demo")
class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final DemoDataProperties properties;
    private final LunarCalendarService calendar;
    private final DemoDataWriter writer;
    private final DemoAccountAndEventWriter accountsAndEvents;

    DemoDataSeeder(DemoDataProperties properties, LunarCalendarService calendar,
                   DemoDataWriter writer, DemoAccountAndEventWriter accountsAndEvents,
                   Environment environment) {
        // Chan ngay trong constructor: context khong khoi tao duoc thi khong co duong nao chay tiep.
        DemoEnvironmentGuard.requireNonProduction(environment);
        this.properties = properties;
        this.calendar = calendar;
        this.writer = writer;
        this.accountsAndEvents = accountsAndEvents;
    }

    @Override
    public void run(ApplicationArguments args) {
        long existing = writer.existingPersonCount();
        if (existing > 0) {
            switch (properties.onExistingData()) {
                case SKIP -> {
                    log.info("CSDL da co {} nhan khau — bo qua viec nap du lieu demo "
                            + "(dat giapha.demo.on-existing-data=REPLACE de nap lai)", existing);
                    // Van chay phan bo sung du bo qua gia pha: no idempotent, va mot CSDL nap tu
                    // lan truoc thuong van thieu su kien gio + tai khoan (hai thu duoc them sau).
                    accountsAndEvents.seed();
                    return;
                }
                case FAIL -> throw new IllegalStateException(
                        "CSDL da co %d nhan khau nhung giapha.demo.on-existing-data=FAIL".formatted(existing));
                case REPLACE -> log.warn("CSDL da co {} nhan khau — se xoa sach va nap lai", existing);
            }
        }

        DemoSeedConfig config = properties.toSeedConfig();
        log.info("Sinh du lieu gia: seed={}, ngay moc={}, {} doi, du kien {} nhan khau",
                config.seed(), config.referenceDate(), config.generations(), config.totalPlanned());

        CalendarLunarDateSource lunar = new CalendarLunarDateSource(calendar);
        DemoDataset dataset = new ClanTreeGenerator(config, lunar).generate();

        // Bo du lieu gia la mot bai kiem tra cheo cho W4: ~3.000 moc ngay tu the ky 19 toi nay,
        // du thang nhuan, di qua ca hai lan doi mui gio.
        log.info("Quy doi am lich qua LunarCalendarService: {} moc thanh cong, {} moc khong quy doi duoc "
                + "(truoc nam 1813 — lich co Viet Nam khong tai lap bang cong thuc, de trong la dung)",
                lunar.converted(), lunar.unconvertible());

        DemoEdgeCaseQuota.verify(dataset.stats(), log);

        DemoWriteResult result = existing > 0
                ? writer.replaceAll(dataset, config.referenceDate())
                : writer.write(dataset, config.referenceDate());

        accountsAndEvents.seed();
        logSummary(dataset, result);
    }

    private void logSummary(DemoDataset dataset, DemoWriteResult result) {
        Map<String, Integer> stats = dataset.stats();
        log.info("=== Bo du lieu demo da nap ===");
        log.info("  branch={}, person={}, person_name={}, relationship={}",
                result.branches(), result.persons(), result.names(), result.relations());
        log.info("  do thi giapha_graph: {} dinh Person, {} canh (doi soat khop bang truoc khi commit)",
                result.personNodes(), result.graphEdges());
        log.info("  con song={}, da khuat={}, vi thanh nien={}",
                stats.get("alive"), stats.get("deceased"), stats.get("minors"));
        log.info("  moc neo (person.attributes.demo_anchor) = {} muc", stats.get("anchors"));
        result.auditCounts().forEach((key, value) -> log.info("  [SQL] {} = {}", key, value));
    }
}
