-- =============================================================================
-- V22 — Chuyển các lời nhắc giỗ ĐÃ nằm trong hộp thư sang chữ CÓ DẤU
--
-- Bối cảnh: tới 24/09/2026 `ReminderMessageFactory` dựng lời nhắc KHÔNG DẤU
-- ("Con 7 ngay toi ...", "Ngay 16/09/2026 duong lich (ngay 6 thang 8 am lich).")
-- theo thói quen của dòng log. Bản vá đã sửa factory, nhưng mọi dòng
-- `notification_inbox` sinh TRƯỚC bản vá vẫn nằm đó và vẫn là thứ đầu tiên
-- người lớn tuổi đọc trong trung tâm thông báo — cạnh những dòng mới có dấu.
--
-- Nguyên tắc của migration này:
--   1. CHỈ đụng phần do MÁY sinh — tiền tố đếm ngược của `title`, và các cụm cố
--      định của `body`. KHÔNG đụng phần do NGƯỜI viết: tên sự kiện (`event.title`)
--      và địa điểm (`event.location`) được chép nguyên văn vào lời nhắc, và
--      "sửa dấu" chữ của người khác là bịa dữ liệu. Nếu một dòng họ đặt tên giỗ
--      không dấu thì nó ở lại không dấu.
--   2. Mọi mẫu đều NEO (`^...` hoặc `...$`) vào đúng vị trí factory đặt nó, để một
--      địa điểm tình cờ chứa chữ "Dia diem:" hay "am lich" ở giữa không bị sửa.
--   3. Chỉ các dòng KHỚP MẪU CŨ bị UPDATE (mệnh đề WHERE), nên dòng đã có dấu
--      không đổi — kể cả `updated_at` (trigger chỉ chạm dòng bị UPDATE).
--      Chạy lại lần hai cũng không đổi gì: mẫu cũ không còn khớp.
--   4. Chuỗi đích phải khớp TỪNG KÝ TỰ với `ReminderMessageFactory` hiện tại
--      (countdownVi · body · lunarVi · shiftNote). Sửa factory thì sửa luôn phép
--      so ở `NotificationInboxCoDauMigrationIT`, nếu không hai bản sẽ lệch nhau.
--
-- Không đụng bản tiếng Anh: `InAppProvider` ghi đúng một ngôn ngữ theo người
-- nhận, và bản tiếng Anh vốn không có vấn đề dấu.
-- Không đụng `typeLabel` ("gio To", "le mung tho"…): nó chỉ được dùng khi
-- `event.title` trống, điều mà `ck_event_title_not_blank` (V18) đã làm cho
-- không thể — và nếu có thì cũng không phân biệt được với chữ người gõ.
-- =============================================================================

SET search_path = public, ag_catalog;

-- -----------------------------------------------------------------------------
-- 22.1 title — tiền tố đếm ngược (countdownVi)
--   "Con N ngay toi "  -> "Còn N ngày tới "
--   "Ngay mai la "     -> "Ngày mai là "
--   "Hom nay la "      -> "Hôm nay là "
-- -----------------------------------------------------------------------------
UPDATE notification_inbox
   SET title = regexp_replace(
                 regexp_replace(
                   regexp_replace(title, '^Con (\d+) ngay toi ', 'Còn \1 ngày tới '),
                   '^Ngay mai la ', 'Ngày mai là '),
                 '^Hom nay la ', 'Hôm nay là ')
 WHERE category = 'REMINDER'
   AND title ~ '^(Con \d+ ngay toi|Ngay mai la|Hom nay la) ';

-- -----------------------------------------------------------------------------
-- 22.2 body — câu ngày song lịch, nhãn địa điểm, và câu giải thích lệch ngày
--
-- Hình dạng cũ (mọi phần sau câu đầu là tuỳ chọn):
--   Ngay DD/MM/YYYY duong lich[ (ngay D thang M[ nhuan] am lich)].
--   [ Dia diem: <chữ người viết>.]
--   [ Nam nay khong co ngay D thang M[ nhuan] nen gio duoc tinh vao
--     ngay D thang M[ nhuan] am lich.]
--
-- Thứ tự các bước có chủ ý: bước (a) đổi đầu câu thành "Ngày … dương lịch", và
-- (b)/(c)/(e) NEO vào chính cái đầu câu đã đổi ấy — nên không bước nào khớp
-- được vào giữa phần địa điểm do người viết. Câu lệch ngày (d) neo ở cuối thân.
-- `[^.]*` ở (c) an toàn vì câu đầu không có dấu chấm nào ngoài dấu kết câu:
-- ngày viết bằng "/" và âm lịch bằng số.
-- " nhuan" được đổi ở các bước riêng (e, 22.3), vì regexp_replace của Postgres
-- không thay có điều kiện theo một nhóm bắt tuỳ chọn.
-- -----------------------------------------------------------------------------
UPDATE notification_inbox
   SET body =
       -- (e) " nhuan" -> " nhuận" trong ngoặc âm lịch của câu đầu
       regexp_replace(
         -- (d) câu giải thích lệch ngày, neo ở CUỐI thân; " nhuan" chép nguyên, đổi ở 22.3
         regexp_replace(
           -- (c) nhãn địa điểm, neo ngay sau câu đầu
           regexp_replace(
             -- (b) ngoặc âm lịch của câu đầu
             regexp_replace(
               -- (a) đầu câu
               regexp_replace(body,
                 '^Ngay (\d{2}/\d{2}/\d{4}) duong lich',
                 'Ngày \1 dương lịch'),
               '^(Ngày \d{2}/\d{2}/\d{4} dương lịch) \(ngay (\d{1,2}) thang (\d{1,2})( nhuan)? am lich\)',
               '\1 (ngày \2 tháng \3\4 âm lịch)'),
             '^(Ngày [^.]*\.) Dia diem: ',
             '\1 Địa điểm: '),
           ' Nam nay khong co ngay (\d{1,2}) thang (\d{1,2})( nhuan)? nen gio duoc tinh vao ngay (\d{1,2}) thang (\d{1,2})( nhuan)? am lich\.$',
           ' Năm nay không có ngày \1 tháng \2\3 nên giỗ được tính vào ngày \4 tháng \5\6 âm lịch.'),
         '^(Ngày \d{2}/\d{2}/\d{4} dương lịch \(ngày \d{1,2} tháng \d{1,2}) nhuan( âm lịch\))',
         '\1 nhuận\2')
 WHERE category = 'REMINDER'
   AND body ~ '^Ngay \d{2}/\d{2}/\d{4} duong lich';

-- -----------------------------------------------------------------------------
-- 22.3 " nhuan" của câu lệch ngày — mỗi lượt neo vào đúng một vế của câu ĐÃ có
-- dấu từ bước (d). Câu có dấu mà còn "nhuan" chỉ có thể do chính migration này
-- sinh ra (factory hiện tại luôn viết "nhuận"), nên hai lượt này không chạm được
-- chữ người viết hay một dòng mới.
-- -----------------------------------------------------------------------------
UPDATE notification_inbox
   SET body = regexp_replace(body,
         '( Năm nay không có ngày \d{1,2} tháng \d{1,2}) nhuan( nên giỗ được tính vào ngày \d{1,2} tháng \d{1,2}(?: nhuan| nhuận)? âm lịch\.)$',
         '\1 nhuận\2')
 WHERE category = 'REMINDER'
   AND body ~ ' Năm nay không có ngày \d{1,2} tháng \d{1,2} nhuan nên giỗ được tính vào ngày \d{1,2} tháng \d{1,2}(?: nhuan| nhuận)? âm lịch\.$';

UPDATE notification_inbox
   SET body = regexp_replace(body,
         '( nên giỗ được tính vào ngày \d{1,2} tháng \d{1,2}) nhuan( âm lịch\.)$',
         '\1 nhuận\2')
 WHERE category = 'REMINDER'
   AND body ~ ' nên giỗ được tính vào ngày \d{1,2} tháng \d{1,2} nhuan âm lịch\.$';
