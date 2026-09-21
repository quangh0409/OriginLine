-- =============================================================================
-- V18__event_cau_hinh_thu_cong.sql — Mở lối TẠO / SỬA / XOÁ MỀM sự kiện dòng họ
-- Nguồn: design/07-checklist §3 · BA v2 §5 (lịch việc họ) · FR-2.3
--
-- Cho tới V17, bảng `event` chỉ có người ghi vào nó là máy: giỗ sinh tự động từ
-- `person.death_lunar` và dữ liệu gieo sẵn của bản demo. `EventController` không
-- có một lối ghi nào. Trưởng cành/chi/họ không cấu hình được lễ Tết, chạp mả,
-- họp họ hay khánh thành — tức là mười hai loại sự kiện trong hợp đồng thì mười
-- loại không có cách nào tồn tại.
--
-- Lược đồ V4 + V10 đã đủ cho phần lớn việc ấy (loại, ngày âm, cờ lặp, phạm vi
-- chi/ngành, cờ cấp dòng họ, xoá mềm, `version` cho khoá lạc quan). Migration này
-- chỉ vá ba chỗ mà lối ghi thủ công làm lộ ra.
-- =============================================================================

SET search_path = public, ag_catalog;

-- -----------------------------------------------------------------------------
-- 1. Sự kiện MỘT LẦN theo âm lịch bắt buộc mang năm âm
--
-- `ck_event_date_source` (V4) chỉ đòi `lunar_date` có `day` và `month`, vì khi
-- viết nó thì mọi dòng trong bảng đều là giỗ — mà giỗ thì lặp hằng năm nên năm âm
-- chỉ mang tính lịch sử.
--
-- Lối ghi thủ công phá giả định đó: một lễ khánh thành từ đường là việc xảy ra
-- ĐÚNG MỘT LẦN. "Ngày 12 tháng 2 âm" mà không có năm thì không quy đổi được sang
-- bất kỳ ngày dương nào — và cái sai ấy không lộ ra lúc ghi, nó lộ ra dưới dạng
-- một sự kiện lặng lẽ không bao giờ sinh lời nhắc, hoặc tệ hơn, lặp lại mỗi năm
-- một lần cho tới vô tận.
--
-- Dòng cũ không bị ảnh hưởng: mọi dòng do bản demo và scheduler sinh ra đều
-- `is_recurring = TRUE`.
-- -----------------------------------------------------------------------------
ALTER TABLE event ADD CONSTRAINT ck_event_oneoff_lunar_year CHECK (
    is_recurring
    OR NOT is_lunar_based
    OR jsonb_exists(lunar_date, 'year')
);

COMMENT ON CONSTRAINT ck_event_oneoff_lunar_year ON event IS
    'Su kien mot lan theo am lich phai co nam am, neu khong thi khong quy doi duoc sang ngay duong nao.';

-- -----------------------------------------------------------------------------
-- 2. Tiêu đề rỗng là một thông báo rỗng
--
-- `title VARCHAR(200) NOT NULL` không chặn chuỗi rỗng, và tiêu đề sự kiện đi
-- thẳng vào nội dung thông báo đẩy — thứ hiện trên màn hình khoá điện thoại.
-- Chặn ở CSDL chứ không chỉ ở bean validation: đường ghi của scheduler và của bản
-- demo không đi qua bean validation.
-- -----------------------------------------------------------------------------
ALTER TABLE event ADD CONSTRAINT ck_event_title_not_blank CHECK (btrim(title) <> '');

-- -----------------------------------------------------------------------------
-- 3. Chỉ mục cho việc dọn lịch nhắc CHƯA BẮN của một sự kiện
--
-- Sửa ngày một buổi họp họ từ 15 sang 20 mà để nguyên các `reminder_job` đã sinh
-- thì cả chi vẫn được nhắc theo ngày cũ — không có lỗi nào được ném ra, người ta
-- chỉ đến nhầm ngày. Vì vậy lối sửa (và lối xoá mềm) xoá các job còn `PENDING`
-- của sự kiện ấy để lượt sinh kế tiếp dựng lại theo ngày mới.
--
-- Phải XOÁ chứ không phải chuyển `CANCELLED`: chống trùng nằm ở chỉ mục duy nhất
-- `ux_reminder_job_occurrence (event_id, occurrence_year, offset_days)`, nên một
-- dòng CANCELLED vẫn chiếm khoá và `INSERT ... ON CONFLICT DO NOTHING` của lượt
-- sinh sau sẽ lặng lẽ không ghi gì. Job đã `QUEUED`/`SENT` thì giữ nguyên — lời
-- nhắc đã phát đi là chuyện đã rồi, và nhật ký gửi phải còn để tra.
-- -----------------------------------------------------------------------------
CREATE INDEX ix_reminder_job_event_pending
    ON reminder_job (event_id) WHERE status = 'PENDING';

COMMENT ON INDEX ix_reminder_job_event_pending IS
    'Do lenh don lich nhac chua ban khi su kien doi ngay hoac bi xoa mem.';

COMMENT ON COLUMN event.is_recurring IS
    'TRUE = lap lai hang nam theo ngay am (gio, chap ma, le Tet). FALSE = xay ra dung mot lan,'
    ' va khi do lunar_date phai mang ca nam am (ck_event_oneoff_lunar_year).';
