package vn.giapha.media.application.view;

import java.time.Instant;
import java.util.UUID;

/**
 * Một đơn báo gỡ, ở dạng đưa lên màn duyệt.
 *
 * @param note lời người báo gõ. <b>Chỉ đi ra màn duyệt</b>; không bao giờ vào {@code audit_log},
 *        vì nó là văn bản tự do và có thể chứa dữ liệu Tầng 3 ("ảnh này có số điện thoại của mẹ
 *        tôi"). Xem {@code MediaReportService}
 * @param mediaUrl URL đã ký để người duyệt <b>nhìn thấy thứ mình đang quyết</b>. Không có nó thì
 *        màn duyệt chỉ hiện một UUID và người duyệt bấm gỡ theo cảm tính
 */
public record MediaReportView(UUID id,
                              UUID mediaId,
                              String mediaKind,
                              String mediaUrl,
                              String reason,
                              String note,
                              UUID reportedBy,
                              String status,
                              UUID reviewedBy,
                              Instant reviewedAt,
                              String resolutionNote,
                              Instant createdAt,
                              long version) {
}
