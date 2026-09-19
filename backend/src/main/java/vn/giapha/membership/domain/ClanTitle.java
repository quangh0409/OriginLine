package vn.giapha.membership.domain;

/**
 * Ghép {@link ClanOffice} thành câu chữ để in lên màn nhận lời mời.
 *
 * <h2>Vì sao chuỗi này được ghép ở đây mà không phải ở giao diện</h2>
 * Giao diện <b>không bao giờ</b> tự thêm kính ngữ hay chức danh: cả hai phụ thuộc vào quan hệ và
 * vào dữ liệu dòng họ, mà đó là việc của máy chủ. Ghép ở một chỗ cũng là cách duy nhất để câu chữ
 * không lệch giữa màn mời, thư điện tử và phiếu giấy.
 *
 * <h2>Và vì sao nó KHÔNG nằm ở bộ luật danh xưng</h2>
 * {@code kinship_rule} chứa <b>danh xưng họ hàng</b> — thứ phụ thuộc vùng miền và do Hội đồng sửa
 * được. "Tộc trưởng" / "Trưởng chi" là <b>chức vụ tổ chức</b>, suy thẳng từ
 * {@code branch.head_person_id} và {@code branch.branch_kind}; nó không có biến thể Bắc/Trung/Nam
 * và không cần một bộ luật.
 *
 * <p><b>Câu chữ ở đây là bản đề xuất.</b> Nếu Hội đồng muốn cách gọi khác ("Chi trưởng",
 * "Trưởng tộc", kèm/không kèm tên chi) thì đây là chỗ sửa, và lúc ấy nên cân nhắc đưa hẳn xuống dữ
 * liệu thay vì sửa hằng số.</p>
 */
public final class ClanTitle {

    private ClanTitle() {
    }

    /**
     * {@code null} khi không có chức danh — và {@code null} là câu trả lời <b>đúng</b>, không phải
     * một giá trị thiếu: phần lớn thành viên không giữ chức nào, và giao diện để trống dòng ấy.
     */
    public static String of(ClanOffice office) {
        if (office == null || office.branchName() == null || office.branchName().isBlank()) {
            return null;
        }
        // Goc cua ca dong ho: chuc danh la "Toc truong", khong kem ten — khong ai noi
        // "Truong Dong ho Nguyen".
        if ("DONG_HO".equals(office.branchKind())) {
            return "Tộc trưởng";
        }
        // Ten chi da mang san tu loai ("Chi Giap", "Nganh Truong") nen chi can mot chu "Truong"
        // dang truoc. Ghep "Truong chi" + "Chi Giap" se ra "Truong chi Chi Giap".
        return "Trưởng " + office.branchName().trim();
    }
}
