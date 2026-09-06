package vn.giapha.demo.writer;

import java.util.Map;

/**
 * Kết quả một lượt nạp dữ liệu giả — số liệu <b>đọc lại từ CSDL</b>, không phải số liệu tự khai.
 *
 * <p>{@code personNodes} và {@code graphEdges} được đếm bằng Cypher ngay trước khi commit, nên
 * chúng là bằng chứng chứ không phải lời hứa: nếu đồ thị không được ghi thì hai con số này bằng 0
 * và {@code DemoDataWriter} đã ném lỗi từ trước khi trả về đây.</p>
 *
 * @param branches     số chi/ngành/nhánh
 * @param persons      số nhân khẩu
 * @param names        số bản ghi tên đa lớp
 * @param relations    số dòng {@code relationship}
 * @param personNodes  số đỉnh {@code Person} đếm được trong {@code giapha_graph}
 * @param graphEdges   số cạnh đếm được trong {@code giapha_graph}
 * @param auditCounts  thống kê ca biên đọc bằng SQL, để đối chiếu với plan §10
 */
public record DemoWriteResult(int branches,
                              int persons,
                              int names,
                              int relations,
                              long personNodes,
                              long graphEdges,
                              Map<String, Long> auditCounts) {
}
