package vn.giapha.media.application.view;

import java.util.UUID;

/**
 * Một tệp đính kèm ở dạng dùng được <b>ngoài</b> context {@code media}.
 *
 * <p>Chỉ gồm kiểu nguyên thuỷ — không một kiểu {@code media.domain} nào đi qua ranh giới, đúng
 * luật cứng của dự án. {@code kind} là chuỗi {@code "IMAGE"}/{@code "VIDEO"} chứ không phải enum
 * {@code MediaKind} vì lý do ấy.</p>
 *
 * @param url URL {@code GET} <b>đã ký, hạn {@code MediaLimits.VIEW_URL_TTL}</b>. Đây là lý do mọi
 *        phản hồi chở kiểu này phải mang {@code Cache-Control: no-store} +
 *        {@code Vary: Authorization} — một URL đã ký nằm trong bộ nhớ đệm dùng chung là một tệp
 *        riêng tư phục vụ cho người sau. {@code PostController} đã đặt sẵn hai header ấy vì
 *        {@code authorDisplayName} cũng cần, và bây giờ chúng còn cần hơn.
 * @param alt chữ thay ảnh. <b>Không bao giờ {@code null} với ảnh</b> — ép từ tầng domain tới tận
 *        {@code ck_media_alt_required}, nên giao diện không phải viết nhánh dự phòng
 * @param durationMs thời lượng video theo mili-giây; {@code null} với ảnh
 */
@org.springframework.modulith.NamedInterface("gan-tep")
public record MediaAssetView(UUID id,
                             String kind,
                             String contentType,
                             long sizeBytes,
                             Integer durationMs,
                             String alt,
                             int position,
                             String url) {
}
