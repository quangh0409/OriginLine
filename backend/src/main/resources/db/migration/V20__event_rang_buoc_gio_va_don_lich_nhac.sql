-- =============================================================================
-- V20__event_rang_buoc_gio_va_don_lich_nhac.sql — Ba chỗ mà cơ sở dữ liệu yếu
-- hơn tầng Java tưởng, và một chỉ mục phải đi theo lệnh dọn lịch nhắc
-- Nguồn: BA v2 §5 (person.death_lunar là nguồn chân lý của ngày giỗ) · FR-2.3
--
-- 1. `event_type = 'GIO'` phải theo ÂM LỊCH và phải LẶP HẰNG NĂM.
--    V4 chỉ đòi `person_id` (`ck_event_gio_has_person`). Phép canh
--    `GIO_DATE_FROM_PERSON` ở `EventCommandService` thì tự tắt khi sự kiện theo
--    dương lịch — nên gửi `solarDate` là tháo được ngày giỗ ra khỏi
--    `person.death_lunar`, và lời nhắc bắn theo một ngày dương cố định trôi
--    khoảng 11 ngày mỗi năm khỏi ngày giỗ thật. Lượt ghi gây ra chuyện đó trả
--    200 và không để lại gì bất thường trong nhật ký.
--    Vế thứ hai — `is_recurring` — đóng ca còn lại: một cái giỗ "một lần" qua
--    được phép canh ngày, nhưng lúc đọc `EffectiveLunarDate` thay ngày bằng
--    `death_lunar`, mà năm của nó là NĂM MẤT, nên lần xảy ra duy nhất rơi vào
--    quá khứ và sự kiện lặng lẽ không bao giờ sinh lời nhắc.
--
-- 2. Siết `ck_event_oneoff_lunar_year` (V18) từ `jsonb_exists(...,'year')` thành
--    "năm âm là một SỐ DƯƠNG". `{"year": 0}` qua được ràng buộc cũ, trong khi
--    `Event` (POJO) từ chối `year <= 0` — và constructor ấy cũng chạy trong
--    `EventMapper.toDomain`, tức MỘT dòng như thế làm `GET /api/v1/events` ném
--    lỗi với mọi người và giết luôn bộ sinh lời nhắc ban đêm. Ràng buộc yếu hơn
--    bản sao Java của nó là một quả mìn hẹn giờ, không phải một sự nới lỏng.
--
-- Mọi lệnh sửa dữ liệu dưới đây CHẠY LẠI ĐƯỢC: chúng lọc theo đúng điều kiện vi
-- phạm, nên lượt chạy thứ hai không đụng vào dòng nào.
-- =============================================================================

SET search_path = public, ag_catalog;

-- -----------------------------------------------------------------------------
-- 1a. Vá dữ liệu cũ: giỗ ghi theo dương lịch / không lặp, mà hồ sơ nhân khẩu CÓ
--     ngày mất âm ⇒ lấy lại đúng nguồn chân lý.
--     `year` bị bỏ đi vì giỗ lặp hằng năm (quy ước của V4: year rỗng = lặp lại).
-- -----------------------------------------------------------------------------
UPDATE event e
   SET lunar_date = jsonb_strip_nulls(jsonb_build_object(
                        'month', p.death_lunar -> 'month',
                        'day',   p.death_lunar -> 'day',
                        'leap',  coalesce(p.death_lunar -> 'leap', 'false'::jsonb))),
       is_lunar_based = TRUE,
       is_recurring   = TRUE,
       solar_date     = NULL
  FROM person p
 WHERE p.id = e.person_id
   AND e.event_type = 'GIO'
   AND p.death_lunar IS NOT NULL
   AND (NOT e.is_lunar_based OR NOT e.is_recurring);

-- -----------------------------------------------------------------------------
-- 1b. Còn lại: giỗ vi phạm mà hồ sơ nhân khẩu KHÔNG có ngày mất âm. Không có
--     nguồn nào để lấy ngày, và đoán một ngày giỗ là việc không ai được phép
--     làm. Giữ nguyên dòng — tiêu đề, ngày, nhân khẩu, lời nhắc đã phát — nhưng
--     thôi gọi nó là giỗ: `KHAC` nói đúng những gì hệ thống biết chắc.
--     Hội đồng sửa lại được bằng cách bổ sung `death_lunar` rồi đổi loại.
-- -----------------------------------------------------------------------------
UPDATE event
   SET event_type = 'KHAC',
       description = coalesce(description || E'\n', '')
                     || '[V20] Ban ghi nay tung la GIO nhung khong theo am lich hoac khong lap'
                     || ' hang nam, va ho so nhan khau khong co ngay mat am de lay lai.'
                     || ' Hay bo sung death_lunar tren ho so roi dat lai loai su kien.'
 WHERE event_type = 'GIO'
   AND (NOT is_lunar_based OR NOT is_recurring);

ALTER TABLE event ADD CONSTRAINT ck_event_gio_lunar_recurring CHECK (
    event_type <> 'GIO' OR (is_lunar_based AND is_recurring)
);

COMMENT ON CONSTRAINT ck_event_gio_lunar_recurring ON event IS
    'Gio ca nhan bam vao person.death_lunar: phai theo am lich va lap hang nam.'
    ' Ghi theo duong lich se tach ngay gio khoi ho so va troi ~11 ngay moi nam.';

-- -----------------------------------------------------------------------------
-- 2a. Vá dữ liệu cũ: sự kiện MỘT LẦN theo âm lịch mà `year` không phải số dương.
--     Một dòng như thế KHÔNG nạp được vào `Event`, nên nó không có nghĩa nào cả
--     — coi như lặp hằng năm để nó đọc được, và bỏ hẳn khoá `year` cho khớp quy
--     ước "year rỗng = lặp lại" của V4.
-- -----------------------------------------------------------------------------
UPDATE event
   SET is_recurring = TRUE,
       lunar_date   = lunar_date - 'year'
 WHERE is_lunar_based
   AND NOT is_recurring
   AND (jsonb_typeof(lunar_date -> 'year') <> 'number'
        OR (lunar_date ->> 'year')::numeric <= 0);

ALTER TABLE event DROP CONSTRAINT IF EXISTS ck_event_oneoff_lunar_year;

ALTER TABLE event ADD CONSTRAINT ck_event_oneoff_lunar_year CHECK (
    is_recurring
    OR NOT is_lunar_based
    -- jsonb_typeof kiem TRUOC phep ep kieu: mot `year` kieu chuoi se lam ca cau
    -- lenh INSERT chet bang loi ep kieu thay vi bang loi rang buoc.
    OR (jsonb_typeof(lunar_date -> 'year') = 'number'
        AND (lunar_date ->> 'year')::numeric > 0)
);

COMMENT ON CONSTRAINT ck_event_oneoff_lunar_year ON event IS
    'Su kien mot lan theo am lich phai co nam am la SO DUONG. {"year": 0} qua duoc ban cu'
    ' (V18) trong khi Event (POJO) tu choi no -> dong do lam ca loi doc /api/v1/events nem loi.';

-- -----------------------------------------------------------------------------
-- 3. Chỉ mục cho lệnh dọn lịch nhắc CHƯA TỚI TAY AI
--
-- `ix_reminder_job_event_pending` (V18) chỉ phủ `status = 'PENDING'`, đúng với
-- câu lệnh dọn của thời điểm ấy. Câu lệnh nay xoá cả `CANCELLED` — vì một dòng
-- `CANCELLED` KHÔNG tới tay ai nhưng VẪN chiếm khoá `ux_reminder_job_occurrence`,
-- nên bỏ sót nó là làm lượt sinh kế tiếp lặng lẽ không ghi gì, mãi mãi. Vị từ của
-- chỉ mục phải khớp đúng mệnh đề WHERE, nếu không Postgres không dùng tới nó và
-- mỗi lần sửa một sự kiện là một lượt quét toàn bảng.
--
-- `QUEUED`/`SENT` vẫn nằm ngoài: lời nhắc đã phát đi là chuyện đã rồi.
-- -----------------------------------------------------------------------------
DROP INDEX IF EXISTS ix_reminder_job_event_pending;

CREATE INDEX IF NOT EXISTS ix_reminder_job_event_unsent
    ON reminder_job (event_id) WHERE status IN ('PENDING', 'CANCELLED');

COMMENT ON INDEX ix_reminder_job_event_unsent IS
    'Do lenh don lich nhac chua toi tay ai khi su kien doi ngay hoac bi xoa mem.'
    ' Gom ca CANCELLED: dong ay khong gui cho ai nhung van chiem ux_reminder_job_occurrence.';
