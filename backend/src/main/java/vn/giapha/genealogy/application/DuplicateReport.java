package vn.giapha.genealogy.application;

import java.util.List;

/**
 * Kết quả dò trùng cho <b>một</b> dòng đầu vào.
 *
 * <p>Danh sách rỗng nghĩa là "không có gì phải hỏi" — đó là kết quả mong đợi cho tuyệt đại đa số
 * hồ sơ, kể cả những hồ sơ trùng tên với người khác trong họ.</p>
 *
 * @param ref mã tham chiếu của dòng đầu vào, chép lại từ {@link DuplicateProbe#ref()}
 * @param matches các nhân khẩu bị nghi, đã sắp giảm dần theo điểm
 */
@org.springframework.modulith.NamedInterface("do-trung")
public record DuplicateReport(String ref, List<DuplicateMatch> matches) {

    public DuplicateReport {
        matches = matches == null ? List.of() : List.copyOf(matches);
    }

    public boolean coNghiNgo() {
        return !matches.isEmpty();
    }
}
