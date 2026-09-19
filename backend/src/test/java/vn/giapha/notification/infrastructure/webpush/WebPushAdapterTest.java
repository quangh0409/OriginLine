package vn.giapha.notification.infrastructure.webpush;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.DeliveryOutcome;
import vn.giapha.notification.domain.DeliveryResult;
import vn.giapha.notification.domain.NotificationCategory;
import vn.giapha.notification.domain.NotificationMessage;
import vn.giapha.notification.domain.PushSubscription;
import vn.giapha.notification.domain.Recipient;

/**
 * Kiểm chứng <b>gửi thật</b>: {@link WebPushAdapter} → ổ cắm mạng → máy chủ đẩy giả → giải mã ngược.
 *
 * <h2>Điều gì được chứng minh ở đây</h2>
 * Một cặp khoá VAPID thật được sinh ra, một "thiết bị" thật (cặp khoá P-256 + auth secret của
 * {@link WebPushDecryptor}) đăng ký, rồi adapter gửi qua HTTP thật tới {@link FakePushService}.
 * Bên nhận <b>giải mã ngược</b> thân bản tin và đọc ra đúng JSON. Không mock nào nằm giữa
 * {@code NotificationMessage} và byte trên dây.
 *
 * <p>Cái duy nhất còn thiếu so với đời thật là bản thân trình duyệt: xem phần cuối javadoc của
 * {@link #dungHopDongVoiServiceWorker()}.</p>
 */
class WebPushAdapterTest {

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final UUID APP_USER = UUID.randomUUID();
    private static final UUID PERSON = UUID.randomUUID();

    private FakePushService mayChuDay;
    private InMemoryPushSubscriptionRepository kho;
    private WebPushProperties cauHinh;
    private WebPushAdapter adapter;
    private ECPublicKey khoaCongKhaiVapid;

    @BeforeEach
    void dungHaTang() throws Exception {
        mayChuDay = FakePushService.khoiDong();
        kho = new InMemoryPushSubscriptionRepository();

        // Cap khoa VAPID that, sinh moi cho moi ca test. Khong khoa nao duoc commit vao repo.
        KeyPair vapid = P256.generateEphemeralKeyPair();
        khoaCongKhaiVapid = (ECPublicKey) vapid.getPublic();
        cauHinh = new WebPushProperties();
        cauHinh.setPublicKey(P256.encodeBase64Url(P256.encodePublicKey(khoaCongKhaiVapid)));
        cauHinh.setPrivateKey(P256.encodeBase64Url(voHuong32Byte((ECPrivateKey) vapid.getPrivate())));
        cauHinh.setSubject("mailto:toc-truong@giapha.vn");
        cauHinh.setTtlSeconds(86_400);
        cauHinh.setJwtValidity(Duration.ofHours(12));

        adapter = new WebPushAdapter(cauHinh, new VapidSigner(), new WebPushCipher(), kho, JSON);
    }

    @AfterEach
    void dongHaTang() {
        mayChuDay.close();
    }

    // ------------------------------------------------------------------------------------
    // Gui that
    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Gui thanh cong")
    class GuiThanhCong {

        @Test
        @DisplayName("Ben nhan GIAI MA NGUOC duoc dung noi dung tieng Viet da gui")
        void benNhanGiaiMaDuocNoiDung() throws Exception {
            WebPushDecryptor thietBi = WebPushDecryptor.thietBiMoi();
            PushSubscription dangKy = dangKyThietBi(thietBi, "thiet-bi-1");
            mayChuDay.traVe(201);

            DeliveryResult ketQua = adapter.send(tinNhacGio("Giỗ cụ Nguyễn Văn Đệ",
                    "Còn 3 ngày nữa tới ngày giỗ", "/events/abc"));

            assertThat(ketQua.outcome()).isEqualTo(DeliveryOutcome.SENT);
            FakePushService.LuotNhan luot = mayChuDay.luotDuyNhat();
            JsonNode payload = JSON.readTree(thietBi.giaiMa(luot.than()));
            assertThat(payload.get("title").asText()).isEqualTo("Giỗ cụ Nguyễn Văn Đệ");
            assertThat(payload.get("body").asText()).isEqualTo("Còn 3 ngày nữa tới ngày giỗ");
            assertThat(payload.get("url").asText()).isEqualTo("/events/abc");
            assertThat(kho.lanDungCuoi()).containsKey(dangKy.id());
            assertThat(kho.endpointDaXoa()).isEmpty();
        }

        @Test
        @DisplayName("Header HTTP dung chuan RFC 8030/8188: Content-Encoding, TTL, Urgency")
        void headerHttpDungChuan() {
            WebPushDecryptor thietBi = throwing(() -> WebPushDecryptor.thietBiMoi());
            dangKyThietBi(thietBi, "thiet-bi-1");
            mayChuDay.traVe(201);

            adapter.send(tinNhacGio("Giỗ", "Còn 1 ngày", "/events/x"));

            FakePushService.LuotNhan luot = mayChuDay.luotDuyNhat();
            assertThat(luot.headerHoac("Content-Encoding", "")).isEqualTo("aes128gcm");
            assertThat(luot.headerHoac("Content-Type", "")).isEqualTo("application/octet-stream");
            assertThat(luot.headerHoac("TTL", "")).isEqualTo("86400");
            assertThat(luot.headerHoac("Urgency", "")).isEqualTo("normal");
            assertThat(luot.duongDan()).isEqualTo("/push/thiet-bi-1");
        }

        @Test
        @DisplayName("JWT VAPID trong header kiem duoc, claim aud dung GOC cua endpoint")
        void jwtVapidHopLe() throws Exception {
            WebPushDecryptor thietBi = WebPushDecryptor.thietBiMoi();
            dangKyThietBi(thietBi, "thiet-bi-1");
            mayChuDay.traVe(201);
            Instant truoc = Instant.now();

            adapter.send(tinNhacGio("Giỗ", "Còn 1 ngày", "/events/x"));

            String authorization = mayChuDay.luotDuyNhat().headerHoac("Authorization", "");
            assertThat(authorization).startsWith("vapid t=").contains(", k=");
            String token = authorization.substring("vapid t=".length(), authorization.indexOf(", k="));
            String k = authorization.substring(authorization.indexOf(", k=") + 4);
            assertThat(k).isEqualTo(cauHinh.getPublicKey());

            String[] phan = token.split("[.]");
            Signature kiem = Signature.getInstance("SHA256withECDSAinP1363Format");
            kiem.initVerify(P256.decodePublicKey(P256.decodeBase64Url(k)));
            kiem.update((phan[0] + "." + phan[1]).getBytes(StandardCharsets.US_ASCII));
            assertThat(kiem.verify(B64.decode(phan[2])))
                    .as("Chu ky VAPID phai kiem duoc bang chinh khoa trong tham so k=")
                    .isTrue();

            JsonNode claims = JSON.readTree(B64.decode(phan[1]));
            // `aud` phai la scheme://host:port, KHONG phai ca URL — nguyen nhan 401 pho bien nhat.
            assertThat(claims.get("aud").asText()).isEqualTo(mayChuDay.audience());
            assertThat(claims.get("sub").asText()).isEqualTo("mailto:toc-truong@giapha.vn");
            assertThat(claims.get("exp").asLong())
                    .isGreaterThan(truoc.getEpochSecond())
                    .isLessThanOrEqualTo(truoc.plus(Duration.ofHours(24)).getEpochSecond());
        }

        @Test
        @DisplayName("Nhieu thiet bi: moi may nhan mot goi rieng, va deu mo duoc")
        void nhieuThietBiDeuNhanDuoc() throws Exception {
            WebPushDecryptor dienThoai = WebPushDecryptor.thietBiMoi();
            WebPushDecryptor mayTinh = WebPushDecryptor.thietBiMoi();
            dangKyThietBi(dienThoai, "dien-thoai");
            dangKyThietBi(mayTinh, "may-tinh");
            mayChuDay.traVe(201);

            DeliveryResult ketQua = adapter.send(tinNhacGio("Giỗ tổ", "Mùng 10 tháng 3", "/events/to"));

            assertThat(ketQua.outcome()).isEqualTo(DeliveryOutcome.SENT);
            assertThat(mayChuDay.daNhan()).hasSize(2);
            // Moi thiet bi chi mo duoc goi cua chinh no — khoa tam thoi va salt khac nhau.
            assertThat(JSON.readTree(dienThoai.giaiMa(goiTai("/push/dien-thoai"))).get("title").asText())
                    .isEqualTo("Giỗ tổ");
            assertThat(JSON.readTree(mayTinh.giaiMa(goiTai("/push/may-tinh"))).get("title").asText())
                    .isEqualTo("Giỗ tổ");
        }

        @Test
        @DisplayName("Mot may chet (410), mot may song (201) -> ket qua chung la SENT")
        void motMayChetMotMaySongVanLaSent() throws Exception {
            WebPushDecryptor song = WebPushDecryptor.thietBiMoi();
            WebPushDecryptor chet = WebPushDecryptor.thietBiMoi();
            dangKyThietBi(song, "con-song");
            PushSubscription daChet = dangKyThietBi(chet, "da-chet");
            mayChuDay.traVe(201);
            mayChuDay.traVeChoEndpoint(daChet.endpoint(), 410);

            DeliveryResult ketQua = adapter.send(tinNhacGio("Giỗ", "Còn 1 ngày", "/events/x"));

            // Danh dau that bai chi vi cai may tinh cu o nha da tat se khien nguoi dung bi gui lai.
            assertThat(ketQua.outcome()).isEqualTo(DeliveryOutcome.SENT);
            assertThat(kho.endpointDaXoa()).containsExactly(daChet.endpoint());
            assertThat(kho.soThietBiConLai()).isEqualTo(1);
        }

        /**
         * Hợp đồng payload giữa backend và service worker.
         *
         * <p>Test này ghim <b>đúng những khoá mà backend phát ra</b>. Nó không thay lời cho một
         * trình duyệt thật: việc service worker có đọc đúng các khoá này hay không nằm ở
         * {@code frontend/}, ngoài phạm vi của bộ test backend.</p>
         */
        @Test
        @DisplayName("Payload chi gom title/body/url/tag/eventId — va KHONG chua du lieu Tang 3")
        void dungHopDongVoiServiceWorker() throws Exception {
            WebPushDecryptor thietBi = WebPushDecryptor.thietBiMoi();
            dangKyThietBi(thietBi, "thiet-bi-1");
            mayChuDay.traVe(201);
            UUID job = UUID.randomUUID();
            UUID suKien = UUID.randomUUID();

            adapter.send(new NotificationMessage(job, suKien, PERSON,
                    new Recipient(PERSON, APP_USER, "vi"), Channel.WEBPUSH,
                    NotificationCategory.REMINDER, "Giỗ cụ tổ", "Còn 7 ngày", "/events/" + suKien,
                    7, LocalDate.of(2026, 3, 27)));

            JsonNode payload = JSON.readTree(thietBi.giaiMa(mayChuDay.luotDuyNhat().than()));
            assertThat(payload.fieldNames()).toIterable()
                    .containsExactlyInAnyOrder("title", "body", "url", "tag", "eventId");
            assertThat(payload.get("tag").asText())
                    .isEqualTo("gio-" + suKien + "-2026-03-27");
            assertThat(payload.get("eventId").asText()).isEqualTo(suKien.toString());
            // BA v2 §10: thong bao day hien tren man hinh khoa va di qua ha tang ben thu ba.
            assertThat(payload.has("phone")).isFalse();
            assertThat(payload.has("email")).isFalse();
            assertThat(payload.has("address")).isFalse();
        }

        /**
         * Ca kiểm quan trọng nhất của {@code tag}, và là ca dễ bị bỏ sót nhất.
         *
         * <p>Khoá duy nhất {@code ux_reminder_job_occurrence} là
         * {@code (event_id, occurrence_year, offset_days)}, nên mỗi mốc 7 / 3 / 1 ngày là một
         * {@code reminder_job} RIÊNG với id riêng. Nếu lấy id job làm tag thì ba lượt nhắc của
         * cùng một đám giỗ mang ba tag khác nhau, hệ điều hành không gộp, và người dùng nhận ba
         * thông báo xếp chồng trên màn hình khoá — đúng thứ mà tag sinh ra để tránh.</p>
         *
         * <p>Ca này khẳng định điều ngược lại: <b>ba job khác nhau, cùng một lần giỗ, cùng một
         * tag</b>.</p>
         */
        @Test
        @DisplayName("Ba moc nhac 7/3/1 cua CUNG mot dam gio phai cho ra CUNG mot tag")
        void baMocNhacCungMotTag() throws Exception {
            WebPushDecryptor thietBi = WebPushDecryptor.thietBiMoi();
            dangKyThietBi(thietBi, "thiet-bi-1");
            UUID suKien = UUID.randomUUID();
            LocalDate ngayGio = LocalDate.of(2026, 3, 27);

            List<String> cacTag = new ArrayList<>();
            for (int truoc : List.of(7, 3, 1)) {
                mayChuDay.traVe(201);
                adapter.send(new NotificationMessage(UUID.randomUUID(), suKien, PERSON,
                        new Recipient(PERSON, APP_USER, "vi"), Channel.WEBPUSH,
                        NotificationCategory.REMINDER, "Giỗ cụ tổ", "Còn " + truoc + " ngày",
                        "/events/" + suKien, truoc, ngayGio));
                JsonNode payload = JSON.readTree(thietBi.giaiMa(mayChuDay.luotCuoi().than()));
                cacTag.add(payload.get("tag").asText());
            }

            assertThat(cacTag)
                    .as("ba lan nhac cung mot dam gio, id job khac nhau, tag phai giong nhau")
                    .containsExactly(cacTag.get(0), cacTag.get(0), cacTag.get(0));
        }

        /**
         * Chiều ngược lại: gộp quá tay thì <b>mất hẳn một lời nhắc</b>. Hai đám giỗ khác nhau —
         * hoặc cùng một cụ nhưng năm sau — phải mang tag khác.
         */
        @Test
        @DisplayName("Hai dam gio khac nhau phai mang tag khac nhau")
        void giokhacNhauThiTagKhacNhau() throws Exception {
            WebPushDecryptor thietBi = WebPushDecryptor.thietBiMoi();
            dangKyThietBi(thietBi, "thiet-bi-1");
            UUID suKien = UUID.randomUUID();

            mayChuDay.traVe(201);
            adapter.send(new NotificationMessage(UUID.randomUUID(), suKien, PERSON,
                    new Recipient(PERSON, APP_USER, "vi"), Channel.WEBPUSH,
                    NotificationCategory.REMINDER, "Giỗ cụ tổ", "Còn 7 ngày", "/e", 7,
                    LocalDate.of(2026, 3, 27)));
            String tag2026 = JSON.readTree(thietBi.giaiMa(mayChuDay.luotCuoi().than()))
                    .get("tag").asText();

            mayChuDay.traVe(201);
            adapter.send(new NotificationMessage(UUID.randomUUID(), suKien, PERSON,
                    new Recipient(PERSON, APP_USER, "vi"), Channel.WEBPUSH,
                    NotificationCategory.REMINDER, "Giỗ cụ tổ", "Còn 7 ngày", "/e", 7,
                    LocalDate.of(2027, 3, 16)));
            String tag2027 = JSON.readTree(thietBi.giaiMa(mayChuDay.luotCuoi().than()))
                    .get("tag").asText();

            assertThat(tag2027)
                    .as("gio nam sau la mot lan gio KHAC, gop vao la mat mot loi nhac")
                    .isNotEqualTo(tag2026);
        }

        @Test
        @DisplayName("Khong co deepLink thi url mac dinh la '/'; body null thanh chuoi rong")
        void giaTriMacDinhCuaPayload() throws Exception {
            WebPushDecryptor thietBi = WebPushDecryptor.thietBiMoi();
            dangKyThietBi(thietBi, "thiet-bi-1");
            mayChuDay.traVe(201);

            adapter.send(new NotificationMessage(null, null, null,
                    new Recipient(PERSON, APP_USER, "vi"), Channel.WEBPUSH,
                    NotificationCategory.SYSTEM, "Thông báo hệ thống", null, null, null, null));

            JsonNode payload = JSON.readTree(thietBi.giaiMa(mayChuDay.luotDuyNhat().than()));
            assertThat(payload.get("url").asText()).isEqualTo("/");
            assertThat(payload.get("body").asText()).isEmpty();
            assertThat(payload.has("tag")).isFalse();
            assertThat(payload.has("eventId")).isFalse();
        }
    }

    // ------------------------------------------------------------------------------------
    // Subscription bi thu hoi — nua sau cua tieu chi ra so 7
    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Subscription bi thu hoi")
    class BiThuHoi {

        @Test
        @DisplayName("HTTP 410 -> XOA khoi kho, khong retry, khong dem la loi")
        void ma410ThiXoa() {
            WebPushDecryptor thietBi = throwing(() -> WebPushDecryptor.thietBiMoi());
            PushSubscription dangKy = dangKyThietBi(thietBi, "da-go-app");
            mayChuDay.traVe(410);

            DeliveryResult ketQua = adapter.send(tinNhacGio("Giỗ", "Còn 1 ngày", "/events/x"));

            assertThat(kho.endpointDaXoa()).containsExactly(dangKy.endpoint());
            assertThat(kho.soThietBiConLai()).isZero();
            assertThat(kho.daGhiNhanLoi())
                    .as("Thu hoi khong phai loi gui — dem no vao failure_count lam nhieu so lieu")
                    .isEmpty();
            assertThat(ketQua.outcome()).isEqualTo(DeliveryOutcome.SKIPPED);
        }

        @Test
        @DisplayName("HTTP 404 -> XOA khoi kho (Firefox tra 404 thay vi 410)")
        void ma404ThiXoa() {
            WebPushDecryptor thietBi = throwing(() -> WebPushDecryptor.thietBiMoi());
            PushSubscription dangKy = dangKyThietBi(thietBi, "khong-ton-tai");
            mayChuDay.traVe(404);

            adapter.send(tinNhacGio("Giỗ", "Còn 1 ngày", "/events/x"));

            assertThat(kho.endpointDaXoa()).containsExactly(dangKy.endpoint());
            assertThat(kho.soThietBiConLai()).isZero();
        }

        @Test
        @DisplayName("Sau khi bi xoa, lan gui KE TIEP khong con goi HTTP nao nua")
        void khongGoiLaiThietBiDaXoa() {
            WebPushDecryptor thietBi = throwing(() -> WebPushDecryptor.thietBiMoi());
            dangKyThietBi(thietBi, "da-go-app");
            mayChuDay.traVe(410);

            adapter.send(tinNhacGio("Giỗ lần 1", "Còn 3 ngày", "/events/x"));
            DeliveryResult lanHai = adapter.send(tinNhacGio("Giỗ lần 2", "Còn 1 ngày", "/events/x"));

            // Day chinh la ly do phai xoa: moi mua gio, moi thiet bi chet ton them mot luot HTTP.
            assertThat(mayChuDay.daNhan()).hasSize(1);
            assertThat(lanHai.outcome()).isEqualTo(DeliveryOutcome.SKIPPED);
            assertThat(lanHai.detail()).contains("chua dang ky thiet bi nao");
        }

        @Test
        @DisplayName("Khoa client hong (p256dh khong giai ma duoc) -> xoa, khong retry")
        void khoaClientHongThiXoa() {
            PushSubscription dangKy = kho.them(APP_USER, mayChuDay.endpoint("khoa-hong"),
                    "khong-phai-base64-!!!", "BTBZMqHH6r4Tts7J_aSIgg");
            mayChuDay.traVe(201);

            DeliveryResult ketQua = adapter.send(tinNhacGio("Giỗ", "Còn 1 ngày", "/events/x"));

            assertThat(mayChuDay.daNhan()).as("Khong duoc goi mang khi khoa da hong").isEmpty();
            assertThat(kho.endpointDaXoa()).containsExactly(dangKy.endpoint());
            assertThat(ketQua.outcome()).isEqualTo(DeliveryOutcome.SKIPPED);
        }
    }

    // ------------------------------------------------------------------------------------
    // Loi tam thoi / vinh vien
    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Phan loai loi")
    class PhanLoaiLoi {

        @Test
        @DisplayName("HTTP 429 -> RETRYABLE, dem loi, KHONG xoa")
        void ma429ThiRetry() {
            WebPushDecryptor thietBi = throwing(() -> WebPushDecryptor.thietBiMoi());
            PushSubscription dangKy = dangKyThietBi(thietBi, "bi-gioi-han");
            mayChuDay.traVe(429);

            DeliveryResult ketQua = adapter.send(tinNhacGio("Giỗ", "Còn 1 ngày", "/events/x"));

            assertThat(ketQua.outcome()).isEqualTo(DeliveryOutcome.RETRYABLE);
            assertThat(kho.endpointDaXoa()).isEmpty();
            assertThat(kho.daGhiNhanLoi()).containsExactly(dangKy.id());
        }

        @Test
        @DisplayName("HTTP 503 -> RETRYABLE, KHONG xoa (may chu day dang hong, thiet bi van song)")
        void ma503ThiRetry() {
            WebPushDecryptor thietBi = throwing(() -> WebPushDecryptor.thietBiMoi());
            dangKyThietBi(thietBi, "may-chu-hong");
            mayChuDay.traVe(503);

            DeliveryResult ketQua = adapter.send(tinNhacGio("Giỗ", "Còn 1 ngày", "/events/x"));

            assertThat(ketQua.outcome()).isEqualTo(DeliveryOutcome.RETRYABLE);
            assertThat(kho.soThietBiConLai()).isEqualTo(1);
        }

        @Test
        @DisplayName("HTTP 401 -> PERMANENT chu KHONG phai SKIPPED: cau hinh VAPID sai phai lo ra")
        void ma401LaLoiVinhVienChuKhongPhaiBoQua() {
            // SKIPPED se duoc ghi vao notification_log nhu mot ket qua binh thuong. Neu khoa VAPID
            // sai, TOAN BO thong bao day se im lang bien mat va khong mot con so nao bao dong —
            // dung kieu hong "chay ma sai" ma tang nay de mac nhat.
            WebPushDecryptor thietBi = throwing(() -> WebPushDecryptor.thietBiMoi());
            dangKyThietBi(thietBi, "vapid-sai");
            mayChuDay.traVe(401, "{\"reason\":\"Unauthorized registration\"}");

            DeliveryResult ketQua = adapter.send(tinNhacGio("Giỗ", "Còn 1 ngày", "/events/x"));

            assertThat(ketQua.outcome()).isEqualTo(DeliveryOutcome.PERMANENT);
            assertThat(ketQua.detail()).contains("401");
            assertThat(kho.endpointDaXoa())
                    .as("401 la loi cua may chu, khong phai cua thiet bi — dung xoa dang ky cua ho")
                    .isEmpty();
        }

        @Test
        @DisplayName("HTTP 400 -> PERMANENT (payload/header sai, retry khong sua duoc)")
        void ma400LaLoiVinhVien() {
            WebPushDecryptor thietBi = throwing(() -> WebPushDecryptor.thietBiMoi());
            dangKyThietBi(thietBi, "payload-sai");
            mayChuDay.traVe(400);

            assertThat(adapter.send(tinNhacGio("Giỗ", "Còn 1 ngày", "/events/x")).outcome())
                    .isEqualTo(DeliveryOutcome.PERMANENT);
        }

        @Test
        @DisplayName("Mot may 503 mot may 401 -> RETRYABLE (uu tien cuu cai con cuu duoc)")
        void tamThoiThangVinhVien() {
            WebPushDecryptor a = throwing(() -> WebPushDecryptor.thietBiMoi());
            WebPushDecryptor b = throwing(() -> WebPushDecryptor.thietBiMoi());
            PushSubscription mot = dangKyThietBi(a, "may-chu-hong");
            dangKyThietBi(b, "vapid-sai");
            mayChuDay.traVe(401);
            mayChuDay.traVeChoEndpoint(mot.endpoint(), 503);

            assertThat(adapter.send(tinNhacGio("Giỗ", "Còn 1 ngày", "/events/x")).outcome())
                    .isEqualTo(DeliveryOutcome.RETRYABLE);
        }

        @Test
        @DisplayName("May chu day khong tra loi (tat may) -> RETRYABLE, khong xoa")
        void matKetNoiThiRetry() {
            WebPushDecryptor thietBi = throwing(() -> WebPushDecryptor.thietBiMoi());
            dangKyThietBi(thietBi, "mat-mang");
            mayChuDay.close();

            DeliveryResult ketQua = adapter.send(tinNhacGio("Giỗ", "Còn 1 ngày", "/events/x"));

            assertThat(ketQua.outcome()).isEqualTo(DeliveryOutcome.RETRYABLE);
            assertThat(kho.endpointDaXoa()).isEmpty();
        }

        @Test
        @DisplayName("Endpoint khong phai URL tuyet doi -> khong duoc nem ngoai le ra ngoai")
        void endpointHongKhongDuocNemNgoaiLe() {
            // Hop dong cua NotificationProvider: KHONG duoc nem cho loi gui thong thuong. Neu nem,
            // NotificationDeliveryService se coi la loi tam thoi va tin se quay 5 vong roi vao DLQ —
            // mot dong du lieu ban lam nghen dung cai hang doi ma nguoi van hanh can nhin.
            kho.them(APP_USER, "khong-phai-url", "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-"
                    + "JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
                    "BTBZMqHH6r4Tts7J_aSIgg");

            DeliveryResult ketQua = adapter.send(tinNhacGio("Giỗ", "Còn 1 ngày", "/events/x"));

            assertThat(ketQua.outcome()).isIn(DeliveryOutcome.SKIPPED, DeliveryOutcome.PERMANENT);
            assertThat(kho.endpointDaXoa()).containsExactly("khong-phai-url");
        }
    }

    // ------------------------------------------------------------------------------------
    // Kenh tu tat
    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Kenh chua cau hinh hoac khong co gi de gui")
    class KenhTuTat {

        @Test
        @DisplayName("Chua co khoa VAPID -> SKIPPED, khong ngoai le, khong goi mang")
        void chuaCauHinhThiBoQua() throws Exception {
            WebPushAdapter chuaCauHinh = new WebPushAdapter(new WebPushProperties(),
                    new VapidSigner(), new WebPushCipher(), kho, JSON);

            DeliveryResult ketQua = chuaCauHinh.send(tinNhacGio("Giỗ", "Còn 1 ngày", "/events/x"));

            assertThat(ketQua.outcome()).isEqualTo(DeliveryOutcome.SKIPPED);
            assertThat(chuaCauHinh.publicKeyBase64Url()).isEmpty();
            assertThat(mayChuDay.daNhan()).isEmpty();
        }

        @Test
        @DisplayName("Da cau hinh -> publicKeyBase64Url tra dung khoa trong bien moi truong")
        void traDungKhoaCongKhai() {
            assertThat(adapter.publicKeyBase64Url())
                    .contains(P256.encodeBase64Url(P256.encodePublicKey(khoaCongKhaiVapid)));
        }

        @Test
        @DisplayName("Nguoi nhan chua co tai khoan -> SKIPPED (khong the co thiet bi nao)")
        void nguoiNhanChuaCoTaiKhoan() {
            DeliveryResult ketQua = adapter.send(new NotificationMessage(UUID.randomUUID(), null,
                    null, new Recipient(PERSON, null, "vi"), Channel.WEBPUSH,
                    NotificationCategory.REMINDER, "Giỗ", "Còn 1 ngày", "/x", 1,
                    LocalDate.of(2026, 3, 27)));

            assertThat(ketQua.outcome()).isEqualTo(DeliveryOutcome.SKIPPED);
            assertThat(mayChuDay.daNhan()).isEmpty();
        }

        @Test
        @DisplayName("Nguoi nhan chua dang ky thiet bi nao -> SKIPPED")
        void chuaDangKyThietBi() {
            DeliveryResult ketQua = adapter.send(tinNhacGio("Giỗ", "Còn 1 ngày", "/x"));

            assertThat(ketQua.outcome()).isEqualTo(DeliveryOutcome.SKIPPED);
            assertThat(mayChuDay.daNhan()).isEmpty();
        }

        @Test
        @DisplayName("Kenh cua adapter la WEBPUSH")
        void kenhLaWebPush() {
            assertThat(adapter.channel()).isEqualTo(Channel.WEBPUSH);
        }
    }

    // ------------------------------------------------------------------------------------
    // Tien ich
    // ------------------------------------------------------------------------------------

    private PushSubscription dangKyThietBi(WebPushDecryptor thietBi, String dinhDanh) {
        return kho.them(APP_USER, mayChuDay.endpoint(dinhDanh), thietBi.p256dh(), thietBi.auth());
    }

    private static NotificationMessage tinNhacGio(String tieuDe, String than, String deepLink) {
        return new NotificationMessage(UUID.randomUUID(), UUID.randomUUID(), PERSON,
                new Recipient(PERSON, APP_USER, "vi"), Channel.WEBPUSH,
                NotificationCategory.REMINDER, tieuDe, than, deepLink, 3,
                LocalDate.of(2026, 3, 27));
    }

    private byte[] goiTai(String duongDan) {
        return mayChuDay.daNhan().stream()
                .filter(luot -> luot.duongDan().equals(duongDan))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Khong co luot POST nao toi " + duongDan))
                .than();
    }

    /** Vô hướng {@code d} của khoá riêng, đệm đủ 32 byte — đúng định dạng khoá riêng VAPID. */
    private static byte[] voHuong32Byte(ECPrivateKey key) {
        byte[] raw = key.getS().toByteArray();
        byte[] out = new byte[32];
        int length = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - length, out, 32 - length, length);
        return out;
    }

    /** Bọc checked exception cho gọn trong các ca không quan tâm tới nó. */
    private static <T> T throwing(java.util.concurrent.Callable<T> callable) {
        try {
            return callable.call();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

}
