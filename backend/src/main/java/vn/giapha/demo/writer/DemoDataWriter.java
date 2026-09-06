package vn.giapha.demo.writer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.demo.model.DemoDataset;

/**
 * Ghi bộ dữ liệu giả xuống PostgreSQL + Apache AGE — <b>ranh giới transaction duy nhất</b> của
 * module demo.
 *
 * <h2>Bất biến sống còn: bảng và đồ thị cùng sống hoặc cùng chết</h2>
 * <p>Cạnh AGE là nguồn chân lý của quan hệ; bảng {@code relationship} là bản chiếu để có khoá
 * ngoại, audit và truy vấn SQL thuần (V2 §2.4). Hai bên <b>phải</b> được ghi trong cùng một
 * transaction.</p>
 *
 * <p>Với bộ dữ liệu giả, hậu quả của việc quên nửa kia là một lỗi <b>im lặng</b>: chỉ ghi bảng thì
 * mọi truy vấn SQL đều đẹp, nhưng phả đồ rỗng và LCA trả 0 dòng mà không văng lỗi ở đâu cả — engine
 * danh xưng sẽ "không tìm thấy quan hệ" một cách rất thuyết phục. Vì thế ngoài việc gói chung
 * transaction, phương thức này còn <b>đối soát trước khi commit</b>: số đỉnh phải khớp số nhân
 * khẩu, số cạnh phải khớp số dòng {@code relationship}. Lệch là ném lỗi và cả mẻ dữ liệu bị huỷ,
 * chứ không để lại một nửa cái cây.</p>
 *
 * <p>Không cắt nhỏ thành nhiều transaction để "cho nhẹ": ~1.500 nhân khẩu và ~3.000 quan hệ nằm gọn
 * trong một transaction là chuyện bình thường với PostgreSQL, và tính toàn vẹn ở đây đáng giá hơn
 * nhiều so với vài trăm milli-giây.</p>
 *
 * <h2>Thứ tự ghi (bị ràng buộc bởi khoá ngoại, không được đảo)</h2>
 * <ol>
 *   <li>{@code branch} — nhân khẩu trỏ vào chi/ngành;</li>
 *   <li>{@code person} rồi {@code person_name};</li>
 *   <li>{@code branch.head_person_id} — trỏ ngược sang {@code person} nên phải sau bước 2;</li>
 *   <li>{@code relationship};</li>
 *   <li>đỉnh AGE rồi cạnh AGE — cạnh cần đỉnh đã tồn tại để {@code MATCH} khớp.</li>
 * </ol>
 */
@Component
@Profile("demo")
public class DemoDataWriter {

    private static final Logger log = LoggerFactory.getLogger(DemoDataWriter.class);

    private final DemoRelationalWriter relational;
    private final DemoGraphWriter graph;

    DemoDataWriter(DemoRelationalWriter relational, DemoGraphWriter graph) {
        this.relational = relational;
        this.graph = graph;
    }

    /** Số nhân khẩu đang có trong bảng — chốt chặn "đã có dữ liệu thì không nạp đè". */
    public long existingPersonCount() {
        return relational.personRowCount();
    }

    /** Số đỉnh {@code Person} đang có trong đồ thị. */
    public long existingPersonNodeCount() {
        return graph.personNodeCount();
    }

    /**
     * Xoá sạch phả hệ hiện có rồi ghi lại bộ dữ liệu giả — tất cả trong một transaction.
     *
     * <p>Xoá cứng ở đây <b>không</b> mâu thuẫn với bất biến "chỉ xoá mềm" của {@code genealogy}:
     * đó là luật cho nhân khẩu thật đi qua use case nghiệp vụ. Đây là thao tác dựng lại môi
     * trường demo, chỉ chạy dưới profile {@code demo} và chỉ khi được bật tường minh.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DemoWriteResult replaceAll(DemoDataset dataset, LocalDate referenceDate) {
        log.warn("Xoa sach du lieu pha he hien co de nap lai bo demo — chi hop le o moi truong dev/test");
        relational.deleteAll();
        graph.deleteAll();
        return doWrite(dataset, referenceDate);
    }

    /**
     * Ghi toàn bộ bộ dữ liệu. Mọi câu lệnh dưới đây nằm trong <b>một</b> transaction; ném lỗi ở bất
     * kỳ bước nào cũng cuốn ngược tất cả.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DemoWriteResult write(DemoDataset dataset, LocalDate referenceDate) {
        return doWrite(dataset, referenceDate);
    }

    /**
     * Thân của cả hai lối vào. Tách ra vì gọi chéo trong cùng một bean không đi qua proxy — để
     * {@link #replaceAll} gọi thẳng {@link #write} thì {@code REQUIRES_NEW} thứ hai bị bỏ qua một
     * cách âm thầm, và người đọc sau sẽ tưởng có hai transaction trong khi thực tế chỉ có một.
     */
    private DemoWriteResult doWrite(DemoDataset dataset, LocalDate referenceDate) {
        long started = System.nanoTime();

        relational.writeBranches(dataset.branches());
        relational.writePersons(dataset.persons(), anchorsByPerson(dataset), referenceDate);
        relational.writeNames(dataset.names());
        relational.writeBranchHeads(dataset.branches());
        relational.writeRelations(dataset.relations());

        graph.writePersonNodes(dataset.persons());
        graph.writeEdges(dataset.relations());

        // Doi soat TRUOC KHI COMMIT — day moi la thu bien loi cau hinh thanh loi khong the bo qua.
        long personNodes = graph.personNodeCount();
        long edges = graph.edgeCount();
        long relationRows = relational.relationRowCount();
        reconcile("dinh Person so voi dong person", personNodes, dataset.persons().size());
        reconcile("dong relationship so voi du lieu sinh ra", relationRows, dataset.relations().size());
        reconcile("canh AGE so voi dong relationship", edges, relationRows);

        long millis = (System.nanoTime() - started) / 1_000_000;
        log.info("Nap xong bo du lieu demo trong {} ms: {} nhan khau, {} quan he, {} chi/nganh",
                millis, dataset.persons().size(), dataset.relations().size(), dataset.branches().size());

        return new DemoWriteResult(dataset.branches().size(), dataset.persons().size(),
                dataset.names().size(), dataset.relations().size(), personNodes, edges,
                relational.auditCounts());
    }

    /**
     * Mốc neo được ghi thẳng vào {@code person.attributes.demo_anchor}.
     *
     * <p>Nhờ vậy mọi ca biên bắt buộc của plan §10 đều tra được bằng SQL thuần, không cần chạy lại
     * generator: {@code WHERE attributes -> 'demo_anchor' @> '["DICH_TON_1"]'}. Khi kiểm chứng bộ
     * dữ liệu hoặc khi test cần một id cụ thể, đây là đường ngắn nhất.</p>
     */
    private static Map<UUID, List<String>> anchorsByPerson(DemoDataset dataset) {
        Map<UUID, List<String>> byPerson = new LinkedHashMap<>();
        dataset.anchors().forEach((name, personId) ->
                byPerson.computeIfAbsent(personId, id -> new ArrayList<>()).add(name));
        byPerson.values().forEach(Collections::sort);
        return byPerson;
    }

    private static void reconcile(String what, long actual, long expected) {
        if (actual != expected) {
            throw new IllegalStateException(
                    "Doi soat that bai (%s): mong doi %d, thuc te %d. Huy toan bo transaction."
                            .formatted(what, expected, actual));
        }
    }
}
