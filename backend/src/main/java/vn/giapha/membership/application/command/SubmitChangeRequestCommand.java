package vn.giapha.membership.application.command;

import java.util.Map;
import java.util.UUID;
import vn.giapha.membership.domain.ChangeRequestType;

/**
 * Đề nghị sửa dữ liệu phả hệ do một thành viên gửi.
 *
 * @param type           loại yêu cầu; quyết định {@code payload} được diễn giải ra sao khi duyệt
 * @param personId       nhân khẩu bị ảnh hưởng; {@code null} chỉ hợp lệ với {@code CREATE_PERSON}
 * @param targetBranchId chi mà yêu cầu nhắm tới. Để trống thì service tự suy từ chi của
 *                       {@code personId} — <b>đây là trường quyết định ai được duyệt</b>
 * @param payload        nội dung đề nghị, dạng tự do theo {@code type}
 * @param reason         lý do người gửi đưa ra
 */
public record SubmitChangeRequestCommand(ChangeRequestType type, UUID personId,
                                         UUID targetBranchId, Map<String, Object> payload,
                                         String reason) {

    public SubmitChangeRequestCommand {
        // Sao chep chap nhan gia tri null: mot de nghi "xoa truong nay" gui len JSON null,
        // va Map.copyOf se bien no thanh mot loi 500 ngay tai bien API.
        payload = payload == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(payload));
    }
}
