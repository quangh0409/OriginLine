-- =============================================================================
-- V10__event_type_tang_do_phan_giai.sql — Mở rộng ck_event_type
-- Nguồn: BA v2 §5 (lịch việc họ) · contracts/openapi.yaml #/components/schemas/EventType
--
-- BA ĐIỀU NÀY SỬA:
--
-- 1) 'TIEU_TUONG' (giỗ đầu, tròn 1 năm) và 'DAI_TUONG' (giỗ hết, tròn 2 năm) có
--    trong hợp đồng và trong messages/vi.json của giao diện, nhưng KHÔNG có trong
--    ràng buộc CHECK — nên không dòng nào trong bảng mang được hai giá trị ấy.
--    Hệ quả nhìn thấy được: giao diện hiện hai bộ lọc vĩnh viễn không khớp gì.
--    Đây là hai mốc tang lễ có thật và quan trọng trong tập quán Việt, chúng phải
--    ghi được chứ không phải bị gộp vào 'GIO'.
--
-- 2) 'MUNG_THO' tách khỏi 'SINH_NHAT'. Trước đây cả hai chung một giá trị CSDL
--    'SINH_NHAT' và tầng API quy đổi thành 'MUNG_THO', nên một cái sinh nhật và
--    một lễ mừng thọ là cùng một thứ trên dây. Hai việc này khác nhau cả về đối
--    tượng (mừng thọ là bậc cao niên, theo mốc 60/70/80/90 tuổi) lẫn về nghi lễ,
--    và màn "sinh nhật & lời chúc" cần phân biệt được chúng.
--
-- KHÔNG chuyển đổi dữ liệu cũ: mọi dòng 'SINH_NHAT' đang có vẫn là sinh nhật.
-- Việc nâng một số dòng lên 'MUNG_THO' là quyết định nghiệp vụ của Hội đồng Tộc
-- biểu, không phải suy đoán của migration — đoán sai thì cả họ nhận lời chúc thọ
-- gửi cho một đứa trẻ.
--
-- Ràng buộc CHECK trong PostgreSQL không sửa tại chỗ được: phải bỏ rồi tạo lại.
-- =============================================================================

SET search_path = public, ag_catalog;

ALTER TABLE event DROP CONSTRAINT IF EXISTS ck_event_type;

ALTER TABLE event ADD CONSTRAINT ck_event_type CHECK (event_type IN (
    'GIO',            -- giỗ cá nhân (nguồn: person.death_lunar)
    'GIO_TO',         -- giỗ Tổ / tế Tổ toàn họ
    'TE_LE',          -- tế lễ, lễ tại từ đường
    'TIEU_TUONG',     -- giỗ đầu — tròn 1 năm ngày mất
    'DAI_TUONG',      -- giỗ hết — tròn 2 năm ngày mất
    'TAO_MO',         -- chạp mộ / tảo mộ
    'KHANH_THANH',    -- khánh thành từ đường, tu bổ
    'HOP_HO',         -- họp họ
    'SINH_NHAT',      -- sinh nhật người còn sống
    'MUNG_THO',       -- mừng thọ bậc cao niên (60/70/80/90)
    'CUOI_HOI',
    'KHAC'
));

COMMENT ON CONSTRAINT ck_event_type ON event IS
    'Tap ma loai su kien. Khop mot-mot voi enum vn.giapha.events.domain.EventType.';
