package vn.giapha.genealogy.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Liên hệ - <b>toàn khối là dữ liệu Tầng 3</b> theo Nghị định 13/2023.
 *
 * <p>Chỉ mở cho chính chủ, {@code ADMIN}, và người được chủ thể opt-in. Khi không đủ quyền thì cả
 * khối vắng mặt, không phải một object rỗng: object rỗng vẫn nói rằng "có chỗ cho dữ liệu này".</p>
 *
 * <p>Không bao giờ ghi các giá trị này vào {@code audit_log}, log ứng dụng, hay
 * {@code rejectedValue} của thông điệp lỗi.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContactInfoDto(String phone, String email, String zaloId) {
}
