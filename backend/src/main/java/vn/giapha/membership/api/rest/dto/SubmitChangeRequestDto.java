package vn.giapha.membership.api.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

/**
 * Thân yêu cầu {@code POST /api/v1/change-requests}.
 *
 * @param requestType    một trong {@code CREATE_PERSON}, {@code UPDATE_PERSON},
 *                       {@code SOFT_DELETE_PERSON}, {@code ADD_RELATIONSHIP},
 *                       {@code REMOVE_RELATIONSHIP}, {@code ADD_NAME}, {@code MOVE_BRANCH},
 *                       {@code OTHER}
 * @param personId       nhân khẩu bị ảnh hưởng; bỏ trống chỉ hợp lệ với {@code CREATE_PERSON}
 * @param targetBranchId chi nhắm tới; bỏ trống thì backend suy từ chi của {@code personId}
 * @param payload        nội dung đề nghị
 * @param reason         lý do; nên có để người duyệt hiểu bối cảnh
 */
public record SubmitChangeRequestDto(@NotNull String requestType, UUID personId,
                                     UUID targetBranchId, Map<String, Object> payload,
                                     @Size(max = 2000) String reason) {
}
