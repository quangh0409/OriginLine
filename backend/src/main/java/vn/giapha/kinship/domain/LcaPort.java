package vn.giapha.kinship.domain;

import java.util.List;
import java.util.Optional;
import vn.giapha.shared.vo.PersonId;

/**
 * Port ra đồ thị phả hệ cho context {@code kinship}.
 *
 * <p>Hiện thực chuẩn là {@code AgeLcaAdapter} — Cypher chạy trong Postgres qua Apache AGE. Giữ
 * đồ thị sau port này để {@link KinshipResolver} và {@link RelationFactsFactory} test được bằng
 * đồ thị trong bộ nhớ, không cần Spring context và không cần CSDL.</p>
 *
 * <p><b>Chiều cạnh:</b> cạnh {@code PARENT} đi <b>cha → con</b>. Muốn đi lên tổ tiên phải dùng mũi
 * tên ngược {@code <-[:PARENT*0..]-}. TDD v1.0 viết xuôi chiều và truy vấn trả về 0 dòng với mọi
 * cặp người; bản đúng nằm ở {@code V7__graph.sql} §7.5(c).</p>
 *
 * <p><b>Xoá mềm:</b> người đã xoá mềm vẫn được đi XUYÊN QUA để không đứt đường nối các đời, nhưng
 * KHÔNG được chọn làm LCA.</p>
 *
 * <p>Context {@code genealogy} có {@code TreeGraphPort}/{@code AgeTreeGraphAdapter} riêng của nó —
 * TDD §3 chủ ý cho hai context hai adapter. Không dùng chung file, không import chéo.</p>
 */
public interface LcaPort {

    /**
     * Tổ chung gần nhất của hai người, kèm hai đường đi lên tới nó.
     *
     * <p>Chọn theo tổng số bậc {@code dist_a + dist_b} nhỏ nhất. Khi hoà (rất thường gặp: anh chị
     * em ruột có cả cha lẫn mẹ đều là tổ chung ở khoảng cách 1–1) thì hiện thực phải phá hoà
     * <b>tất định</b> — ưu tiên tổ chung là nam (bên nội), sau đó tới id nhỏ hơn — nếu không, cùng
     * một câu hỏi sẽ cho hai đường quan hệ khác nhau ở hai lần gọi.</p>
     *
     * @return rỗng khi hai người không có tổ chung nào chưa bị xoá mềm
     */
    Optional<LcaResult> findLca(PersonId ego, PersonId alter);

    /**
     * Các cạnh nối trực tiếp giữa hai người theo <b>cả hai chiều</b>
     * ({@code SPOUSE}, {@code PARENT} BIO/ADOPT, {@code HEIR}).
     * {@link DirectLink#reversed()} cho biết cạnh đi từ alter sang ego.
     */
    List<DirectLink> directLinks(PersonId ego, PersonId alter);

    /** Vợ/chồng của một người, kể cả hôn nhân đã chấm dứt (cờ {@link SpouseLink#active()}). */
    List<SpouseLink> spousesOf(PersonId person);
}
