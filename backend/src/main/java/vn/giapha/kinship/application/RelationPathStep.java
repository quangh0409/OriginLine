package vn.giapha.kinship.application;

import java.util.UUID;
import vn.giapha.kinship.domain.DirectLinkType;

/**
 * Một chặng trên đường quan hệ từ ego lên tổ chung rồi xuống alter.
 *
 * <p>Đây là <b>bằng chứng</b> để dòng họ tự kiểm chứng danh xưng, nên không bao giờ được bỏ chặng:
 * người còn sống bị ẩn theo phân tầng chỉ bị thay {@code displayName} bằng nhãn chung
 * ({@link KinshipDisplayPolicy#MASKED_LABEL}), chặng vẫn nằm nguyên — bỏ chặng sẽ làm sai độ dài
 * đường quan hệ mà người dùng đọc được.</p>
 *
 * @param viaRelType loại cạnh đã đi qua để tới chặng này; {@code null} ở chặng {@code SELF} và ở
 *                   các chặng phả hệ mà không phân định được ruột/nuôi (xem
 *                   {@code LcaResult.viaAdoption()} chỉ là cờ tổng cho cả đường đi)
 */
public record RelationPathStep(
        UUID personId,
        String displayName,
        Integer generation,
        PathDirection direction,
        DirectLinkType viaRelType) {

    public RelationPathStep withDisplayName(String value) {
        return new RelationPathStep(personId, value, generation, direction, viaRelType);
    }
}
