package vn.giapha.membership.domain;

/**
 * Chức danh dòng tộc của một nhân khẩu, ở dạng <b>dữ liệu thô</b> — chưa thành câu chữ.
 *
 * <h2>Đây KHÔNG phải vai kỹ thuật</h2>
 * {@link RoleCode#BRANCH_HEAD} nói tài khoản đó được <i>thao tác</i> gì trên hệ thống;
 * {@code branch.head_person_id} nói người đó <i>là</i> Trưởng chi trong dòng họ theo huyết thống /
 * đích tôn. Hai dữ kiện độc lập: một người có thể giữ cả hai, chỉ một, hoặc không cái nào. Màn nhận
 * lời mời in chức danh <b>dòng tộc</b>, vì đó là thứ người nhận biết mặt biết tên — không ai trong
 * họ tự giới thiệu mình là "BRANCH_HEAD".
 *
 * @param branchName tên chi/ngành có dấu, ví dụ {@code Chi Giáp}
 * @param branchKind một trong {@code DONG_HO · CHI · NGANH · CANH · NHANH}
 */
public record ClanOffice(String branchName, String branchKind) {
}
