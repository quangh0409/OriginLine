package vn.giapha.events.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Thân yêu cầu {@code POST /api/v1/events} — Trưởng cành/chi/họ cấu hình một việc họ.
 *
 * <h2>Ngày âm là mặc định</h2>
 * Việc họ tính theo lịch âm: lễ Tết, chạp mả, giỗ chạp, họp họ đầu xuân. Gửi {@code lunarDate} cho
 * gần như mọi trường hợp; {@code solarDate} dành cho số ít việc vốn được ấn định theo dương lịch.
 * <b>Chỉ gửi một trong hai</b> — lưu cả hai là tạo ra hai ngày không bao giờ đồng bộ lại được.
 *
 * <h2>Phạm vi phải chọn, không được bỏ trống</h2>
 * {@code clanWide = true} (việc của cả họ) <i>hoặc</i> {@code scopeBranchId} (việc của một
 * chi/ngành và mọi nhánh dưới nó). Trường này quyết định <b>ai được nhắc</b>, không phải một nhãn
 * hiển thị: nó chảy thẳng tới phép so {@code ltree} của bộ phân giải người nhận.
 *
 * @param eventType một trong 13 mã của {@code EventType} trong hợp đồng — {@code GIO_HO} và
 *                  {@code GIO_CHI} đã tự ấn định phạm vi, gửi kèm {@code clanWide} mâu thuẫn sẽ bị
 *                  từ chối
 * @param personId  nhân khẩu chủ thể; bắt buộc với {@code GIO_THUONG}
 */
@Schema(name = "CreateEventRequest", description = "Tao su kien dong ho")
public record CreateEventRequest(@NotBlank String eventType,
                                 @NotBlank @Size(max = 200) String title,
                                 @Size(max = 4000) String description,
                                 @Valid LunarDateInput lunarDate,
                                 LocalDate solarDate,
                                 Boolean recurringAnnually,
                                 UUID scopeBranchId,
                                 Boolean clanWide,
                                 UUID personId,
                                 @Size(max = 255) String location) {

    /** Mặc định <b>có lặp</b>: đại đa số việc họ (giỗ, chạp mả, lễ Tết) lặp theo năm âm. */
    public boolean recurring() {
        return recurringAnnually == null || recurringAnnually;
    }

    public boolean clanWideFlag() {
        return Boolean.TRUE.equals(clanWide);
    }
}
