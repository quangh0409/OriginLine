package vn.giapha.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.NotificationCategory;
import vn.giapha.notification.domain.NotificationMessage;
import vn.giapha.notification.domain.Recipient;

/**
 * Hợp đồng nối dây của một tin trên RabbitMQ.
 *
 * <p>Tin nằm trong queue <b>lâu hơn một lần triển khai</b>: khi hàng nghìn tin đang chờ ở
 * {@code notify.dlq}, một thay đổi trong domain không được phép làm chúng không đọc lại được. Bài
 * test này giữ envelope phẳng, dùng kiểu nguyên thuỷ, và enum đi dưới dạng <b>chuỗi</b>.</p>
 */
class NotificationEnvelopeTest {

    /**
     * Dựng lại <b>đúng cấu hình chạy thật</b>: {@code RabbitConfig} nhận bean {@code ObjectMapper}
     * do Spring Boot auto-config tạo, và Boot tắt sẵn {@code WRITE_DATES_AS_TIMESTAMPS}.
     *
     * <p>Phải tắt tay ở đây vì {@code Jackson2ObjectMapperBuilder} trần <b>không</b> tắt nó — chính
     * Boot mới tắt. Nếu dùng mapper mặc định thì {@code dueSolarDate} ra mảng {@code [2026,10,20]}:
     * vẫn đọc ngược được, nhưng người vận hành soi {@code notify.dlq} sẽ thấy một payload khác hẳn
     * thứ tài liệu mô tả.</p>
     */
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    /** Mapper <b>chưa</b> tắt WRITE_DATES_AS_TIMESTAMPS - hình dạng của một cấu hình khác. */
    private final ObjectMapper mapperKhacCauHinh = Jackson2ObjectMapperBuilder.json().build();

    private NotificationMessage tinNhac() {
        return new NotificationMessage(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new Recipient(UUID.randomUUID(), UUID.randomUUID(), "en"), Channel.WEBPUSH,
                NotificationCategory.REMINDER, "3 days until the rite for Duc",
                "On 20/10/2026", "/events/abc", 3, LocalDate.of(2026, 10, 20));
    }

    @Test
    @DisplayName("Doi qua JSON roi ve lai: khong mat truong nao")
    void doiQuaJsonKhongMatTruong() throws Exception {
        NotificationMessage goc = tinNhac();

        String json = mapper.writeValueAsString(NotificationEnvelope.from(goc));
        NotificationMessage khoiPhuc = mapper.readValue(json, NotificationEnvelope.class).toMessage();

        assertThat(khoiPhuc).isEqualTo(goc);
    }

    @Test
    @DisplayName("Enum di duoi dang CHUOI: them mot hang so vao giua enum khong lam hong tin dang cho")
    void enumDiDuoiDangChuoi() throws Exception {
        String json = mapper.writeValueAsString(NotificationEnvelope.from(tinNhac()));

        assertThat(json).contains("\"channel\":\"WEBPUSH\"").contains("\"category\":\"REMINDER\"");
        // Số thứ tự của enum tuyệt đối không được xuất hiện - đó là thứ đổi khi ai đó chèn hằng số.
        assertThat(json).doesNotContain("\"channel\":1");
    }

    @Test
    @DisplayName("Ngay gio di duoi dang ISO, khong phai mang so - nguoi van hanh soi DLQ doc duoc")
    void ngayGioDiDuoiDangIso() throws Exception {
        String json = mapper.writeValueAsString(NotificationEnvelope.from(tinNhac()));

        assertThat(json).contains("\"dueSolarDate\":\"2026-10-20\"");
        assertThat(json).doesNotContain("[2026,10,20]");
    }

    @Test
    @DisplayName("Tin ghi boi cau hinh ngay-thang KHAC van doc lai duoc - queue song lau hon mot lan trien khai")
    void docLaiDuocTinGhiBoiCauHinhKhac() throws Exception {
        NotificationMessage goc = tinNhac();

        // Kịch bản thật: hàng nghìn tin đang nằm ở notify.dlq từ trước khi ai đó chỉnh cấu hình
        // Jackson. Đổi cấu hình mà không đọc lại được chúng nghĩa là mất luôn cả đống tin ấy.
        String jsonCuoiCungCuaCauHinhCu = mapperKhacCauHinh.writeValueAsString(
                NotificationEnvelope.from(goc));

        assertThat(mapper.readValue(jsonCuoiCungCuaCauHinhCu, NotificationEnvelope.class).toMessage())
                .isEqualTo(goc);
    }

    @Test
    @DisplayName("Locale cua nguoi nhan di kem tin: consumer khong phai tra lai bang app_user")
    void localeDiKemTin() {
        NotificationEnvelope envelope = NotificationEnvelope.from(tinNhac());

        assertThat(envelope.recipientLocale()).isEqualTo("en");
        assertThat(envelope.toMessage().recipient().prefersEnglish()).isTrue();
    }

    @Test
    @DisplayName("Truong thieu trong JSON cu khong lam no consumer")
    void truongThieuKhongLamNoConsumer() throws Exception {
        // Một tin do phiên bản trước đẩy vào, chưa có deepLink/offsetDays.
        String jsonCu = """
                {"reminderJobId":"%s","recipientPersonId":"%s","channel":"INAPP",
                 "category":"REMINDER","title":"Gio cu Duc"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        NotificationMessage message = mapper.readValue(jsonCu, NotificationEnvelope.class).toMessage();

        assertThat(message.title()).isEqualTo("Gio cu Duc");
        assertThat(message.deepLink()).isNull();
        assertThat(message.offsetDays()).isNull();
        assertThat(message.category()).isEqualTo(NotificationCategory.REMINDER);
    }
}
