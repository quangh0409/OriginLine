package vn.giapha.media.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.media.domain.MediaReport;
import vn.giapha.media.domain.ReportStatus;

/** Cổng lưu trữ của {@link MediaReport}. */
public interface MediaReportRepository {

    MediaReport save(MediaReport report);

    Optional<MediaReport> findById(UUID id);

    /** Đơn đang mở của đúng người này cho đúng tệp này — chặn bấm nhiều lần. */
    Optional<MediaReport> findOpenBy(UUID mediaId, UUID reportedBy);

    /** Hàng đợi của người duyệt, mới nhất trước. Lọc phạm vi chạy ở tầng ứng dụng. */
    List<MediaReport> findByStatus(ReportStatus status, int limit);
}
