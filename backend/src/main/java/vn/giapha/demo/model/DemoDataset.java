package vn.giapha.demo.model;

import java.util.List;
import java.util.Map;

/**
 * Kết quả của generator: toàn bộ bộ dữ liệu giả trong bộ nhớ, sẵn sàng để writer ghi xuống
 * PostgreSQL + AGE trong một transaction.
 *
 * @param branches      chi/ngành/nhánh, cha luôn đứng trước con (thứ tự INSERT an toàn)
 * @param persons       nhân khẩu, đời nhỏ đứng trước đời lớn
 * @param names         tên đa lớp
 * @param relations     quan hệ (ghi cả bảng lẫn cạnh AGE)
 * @param anchors       khoá nghiệp vụ -> id, để test assert trên id cụ thể (xem DemoFixtures)
 * @param stats         thống kê để log ra và đối chiếu với bảng ca biên bắt buộc
 */
public record DemoDataset(List<DemoBranch> branches,
                          List<DemoPerson> persons,
                          List<DemoName> names,
                          List<DemoRelation> relations,
                          Map<String, java.util.UUID> anchors,
                          Map<String, Integer> stats) {
}
