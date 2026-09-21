package vn.giapha.events.infrastructure.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data cho bảng {@code event}.
 *
 * <p>Chỉ có truy vấn dẫn xuất từ tên phương thức, không có {@code @Query} thủ công: bộ lọc thật của
 * {@code GET /api/v1/events} chạy trên <b>ngày dương của lần xảy ra sắp tới</b> — một giá trị tính
 * ra, không có cột nào để {@code WHERE}. Xem {@code EventQueryService} để biết vì sao việc lọc nằm
 * ở tầng application.</p>
 */
public interface EventJpaRepository extends JpaRepository<EventJpaEntity, UUID> {

    /**
     * Đầu vào của scheduler: sự kiện còn hiệu lực, <b>kể cả sự kiện một lần</b>.
     *
     * <p>Điều kiện {@code is_recurring = TRUE} đã bị bỏ có chủ ý — xem
     * {@code EventRepository.findActivePage}.</p>
     */
    List<EventJpaEntity> findByDeletedFalseOrderByIdAsc(Pageable pageable);

    List<EventJpaEntity> findByDeletedFalseOrderByIdAsc();

    List<EventJpaEntity> findByOrderByIdAsc();
}
