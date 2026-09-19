package vn.giapha.dataimport.domain.port;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cổng tra <b>khoá bất biến</b> {@code person_external_ref}.
 *
 * <p>Đây là thứ làm cho "sửa file rồi tải lại" <b>không sinh người trùng</b>: tra mã, có thì cập
 * nhật người đã có, không có thì tạo mới. Bước đối soát lặp nhiều lần là hành vi bình thường của
 * quy trình chứ không phải ngoại lệ, nên lối gọi ở đây phải rẻ và phải theo lô.</p>
 *
 * <p><b>Chú ý về phạm vi:</b> mọi phương thức đều nhận branchId vì mã chỉ duy nhất <b>trong một
 * chi</b>. AT-02-001 và GI-02-001 là hai người khác nhau, và bốn chi nhập song song hoàn toàn có
 * thể cùng đánh số kiểu 01, 02, 03.</p>
 */
public interface ExternalRefPort {

    /**
     * Tra một lô mã trong đúng một vòng gọi CSDL.
     *
     * @return chỉ các mã tìm thấy; mã vắng mặt trong bản đồ nghĩa là sẽ tạo mới
     */
    Map<String, UUID> resolve(UUID branchId, Collection<String> externalCodes);

    /**
     * Toàn bộ mã đã biết của chi — dùng cho hai việc: dò dòng biến mất khỏi tệp, và làm nguồn gợi
     * ý mã gần giống khi báo không tìm thấy mã cha.
     */
    Set<String> codesOf(UUID branchId);

    /** Chi này đã có dữ liệu nhập trước đó chưa — điều kiện bật IMP_MASS_CREATE_GUARD. */
    long countByBranch(UUID branchId);

    /**
     * Mã nào đang thuộc về chi nào, tra <b>trên toàn dòng họ</b> chứ không trong một chi.
     *
     * <p>Đây là phép kiểm ranh giới phân quyền: Trưởng chi Ất nộp một tệp chứa mã đã đăng ký cho
     * chi Giáp là đang ghi sang phần dữ liệu của chi khác, dù hoàn toàn vô tình. Không tra toàn cục
     * thì không phát hiện được, vì trong phạm vi chi Ất mã ấy trông như một mã mới tinh.</p>
     *
     * @return chỉ các mã đã có chủ; mã vắng mặt nghĩa là chưa ai dùng
     */
    Map<String, UUID> ownerBranchOf(Collection<String> externalCodes);

    /**
     * Ghi một khoá bất biến mới: {@code (EXCEL_MA, chi, Mã) -> person_id}.
     *
     * <p>Đây là dòng làm cho lần tải lại sau không sinh người trùng, và là dòng làm cho việc gỡ lô
     * biết chính xác phải gỡ ai. Gọi <b>trong cùng transaction</b> với lệnh tạo nhân khẩu: ghi
     * người mà không ghi khoá thì lần tải lại kế tiếp sẽ tạo lại y nguyên 400 người ấy một lần
     * nữa.</p>
     *
     * <p><b>Không sửa dòng này khi người đó chuyển chi.</b> {@code external_code} là toạ độ trong
     * tài liệu gốc (số mấy của sổ chi nào), không phải trạng thái hiện tại của người ấy.</p>
     *
     * @param batchId lô đầu tiên sinh ra người này — {@code first_batch_id}
     */
    void ghi(UUID branchId, String externalCode, UUID personId, UUID batchId);

    /**
     * Xoá các khoá do một lô sinh ra — bước cuối của việc gỡ lô.
     *
     * <p>Phải xoá thật, không xoá mềm: mã phải được <b>trả lại</b> cho chi, nếu không thì tải lại
     * đúng tệp ấy sau khi gỡ sẽ ra 400 dòng UPDATE trỏ tới 400 người vừa bị xoá mềm. Bảng này là
     * bản đồ tra cứu, không phải dữ liệu phả hệ — xoá một dòng ở đây không làm đứt cây.</p>
     *
     * @return số dòng đã xoá
     */
    int xoaTheoLo(UUID batchId);
}
