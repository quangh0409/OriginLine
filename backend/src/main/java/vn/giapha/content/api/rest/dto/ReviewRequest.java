package vn.giapha.content.api.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Thân {@code POST .../{id}/review} — dùng chung cho bài viết và vinh danh.
 *
 * <p>Một DTO cho cả hai vì hai lệnh có <b>đúng</b> cùng hình dạng, và hai record giống hệt nhau chỉ
 * là hai chỗ để lệch nhau. Ý nghĩa của {@code approve = false} thì khác: bài viết <i>trả về bản
 * nháp</i> để sửa rồi gửi lại, vinh danh <i>bị từ chối hẳn</i>. Khác biệt ấy nằm ở domain, không
 * nằm ở hình dạng thân yêu cầu.</p>
 *
 * @param note lý do. <b>Bắt buộc khi {@code approve = false}</b>, nhưng phép kiểm ấy ở tầng domain
 *        chứ không phải ở một annotation: bean validation không diễn đạt được "bắt buộc khi trường
 *        kia bằng false" mà không viết một validator riêng, và luật ấy phải đúng cho cả lối vào
 *        không đi qua HTTP
 */
public record ReviewRequest(
        @NotNull(message = "Phai noi ro duyet hay tra lai")
        Boolean approve,

        @Size(max = 2000, message = "Ly do toi da 2000 ky tu")
        String note) {
}
