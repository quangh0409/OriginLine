package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataIntegrityViolationException;
import vn.giapha.events.api.rest.EventTypeApiMapper;
import vn.giapha.events.domain.EventType;

/**
 * {@code ck_event_type} phải khớp <b>một-một</b> với enum {@code EventType} (V10).
 *
 * <h2>Vì sao cần một ca test chạm tới ràng buộc CHECK thật</h2>
 * Một giá trị enum không có trong ràng buộc CHECK là thứ <b>không có test đơn vị nào bắt được</b>:
 * mã Java biên dịch sạch, bộ lọc của giao diện hiện ra bình thường, chỉ có điều không dòng nào trong
 * cơ sở dữ liệu mang được giá trị ấy — nên bộ lọc vĩnh viễn trả về rỗng và không ai biết tại sao.
 * Đó chính xác là chuyện đã xảy ra với {@code TIEU_TUONG} và {@code DAI_TUONG}: có trong hợp đồng,
 * có trong {@code messages/vi.json}, không có trong bảng.
 *
 * <p>Ca test theo tham số bên dưới sẽ đỏ ngay khi ai đó thêm hằng số vào {@code EventType} mà quên
 * migration — đúng cái bẫy vừa được gỡ.</p>
 */
@DisplayName("ck_event_type khớp một-một với EventType (V10)")
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class EventTypeConstraintIT extends AbstractIntegrationTest {

    private void insertEvent(String eventType) {
        jdbc.update("INSERT INTO event (id, event_type, title, lunar_date, is_lunar_based,"
                        + " person_id, is_clan_level)"
                        + " VALUES (?, ?, ?, CAST(? AS jsonb), TRUE, NULL, TRUE)",
                UUID.randomUUID(), eventType, "Su kien thu " + eventType,
                "{\"day\": 10, \"month\": 5, \"leapMonth\": false}");
    }

    @ParameterizedTest
    @EnumSource(EventType.class)
    @DisplayName("mọi giá trị của enum đều ghi được vào bảng event")
    void moiGiaTriEnumDeuGhiDuoc(EventType type) {
        if (type == EventType.GIO) {
            // ck_event_gio_has_person doi person_id, va gio ca nhan thi dieu do la dung.
            return;
        }
        assertThatCode(() -> insertEvent(type.name()))
                .as("EventType.%s khong co trong ck_event_type -> bo loc tuong ung se vinh vien rong",
                        type.name())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("giỗ đầu / giỗ hết / mừng thọ ghi được và đọc lại đúng mã hợp đồng")
    void baGiaTriMoiCuaV10() {
        insertEvent(EventType.TIEU_TUONG.name());
        insertEvent(EventType.DAI_TUONG.name());
        insertEvent(EventType.MUNG_THO.name());

        assertThat(jdbc.queryForList(
                "SELECT event_type FROM event ORDER BY event_type", String.class))
                .containsExactly("DAI_TUONG", "MUNG_THO", "TIEU_TUONG");
        assertThat(EventTypeApiMapper.toApi(EventType.TIEU_TUONG, true)).isEqualTo("TIEU_TUONG");
        assertThat(EventTypeApiMapper.toApi(EventType.MUNG_THO, false)).isEqualTo("MUNG_THO");
    }

    @Test
    @DisplayName("giá trị ngoài tập vẫn bị ràng buộc từ chối — CHECK không bị nới lỏng thành vô nghĩa")
    void giaTriLaVanBiTuChoi() {
        assertThatThrownBy(() -> insertEvent("LE_HOI_KHONG_CO_THAT"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
