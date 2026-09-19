package vn.giapha.dataimport.domain.port;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Số liệu nền của <b>màn tiến độ theo chi</b>: mẫu số, số đã có, và ai đang phụ trách.
 *
 * <h2>Ranh giới: chỉ số ĐẾM, không một trường nhân khẩu nào</h2>
 * {@code personsInTree} là một con số {@code count(*)}. Nó không phải dữ liệu cá nhân và không đi
 * qua bộ lọc phân tầng riêng tư được — vì chẳng có gì để lọc. Cổng này <b>không</b> được phép mở
 * rộng thành "danh sách người của chi": dữ liệu nhân khẩu chỉ rời hệ thống qua bộ lọc của
 * {@code genealogy}, không qua một câu SQL của context nhập liệu.
 *
 * <h2>Ngoại lệ duy nhất, và vì sao nó được canh ở tầng trên</h2>
 * {@link SoLieuChi#coordinatorName()} <b>là tên một người còn sống</b>. Nó nằm ở đây vì nó là một
 * thuộc tính của <i>quy trình</i> (ai nhận nhập chi này), nhưng nó phải bị <b>bỏ hẳn khỏi phản
 * hồi</b> với chi nằm ngoài phạm vi người gọi — bỏ hẳn, không null hoá: một trường null vẫn nói
 * cho người đọc biết trường ấy tồn tại và có giá trị. Phép cắt ấy nằm ở tầng api, xem
 * {@code ImportProgressReadModel}.
 */
public interface BranchProgressPort {

    /**
     * @param personsInTree số nhân khẩu chưa xoá mềm đang treo vào chi này
     * @param expectedPersons số người Hội đồng đếm được trên bản phả <b>giấy</b>; {@code null}
     *        nghĩa là <b>chưa ai đếm</b> — một câu trả lời thật, khác hẳn với 0
     * @param coordinatorName tên người đang giữ vai Trưởng chi trên chi này. <b>Người còn sống.</b>
     */
    record SoLieuChi(UUID branchId, long personsInTree, Integer expectedPersons,
                     String coordinatorName) {
    }

    /** Một lượt gọi cho cả dòng họ. Chi không có dòng nào vẫn có mặt, với {@code personsInTree = 0}. */
    Map<UUID, SoLieuChi> theoChi(Collection<UUID> branchIds);
}
