-- =============================================================================
-- V12 — DỌN NỢ DỮ LIỆU: CẢNH BÁO NHẬP LIỆU CŨ MANG DỮ LIỆU NGƯỜI TRONG PHẢ
-- =============================================================================
-- Hai luật của bộ kiểm từng soạn câu thông báo BẰNG TRƯỜNG thay vì bằng khoá, và
-- câu ấy được ghi thẳng vào import_issue.message / import_issue.context:
--
--   IMP_SUSPECT_DUPLICATE  "... có thể trùng với Nguyễn Thị Lựu (đã có trong phả)
--                          — 78 điểm", cộng context.nghiNgo[].ten / .doi / .giaiThich
--                          ("trùng năm sinh 1975, cùng đời thứ 5").
--   IMP_TABOO_COLLISION    context.bacTrenTen / .bacTrenDoi, và context.tenHuy —
--                          cái cuối này là BẪY: nó KHÔNG phải ô Tên huý người nhập
--                          vừa gõ mà là person_name.full_name ĐỌC TỪ PHẢ, vì phép dò
--                          khớp cả ở mức tên chính ("Cẩn" khớp "Nguyễn Văn Cẩn").
--
-- Bộ dò quét TOÀN DÒNG HỌ, nên người bị nhắc tới có thể đang CÒN SỐNG ở một chi khác
-- mà người nhập liệu của chi này không có quyền biết là tồn tại. Sau V8, năm sinh
-- nằm trong nhóm trường kín nhất.
--
-- Gốc rễ đã vá ở SuspectDuplicateRule / TabooCollisionRule / DuplicateScorer.hint().
-- Tệp này lo phần bản vá mã nguồn KHÔNG với tới được: những dòng ĐÃ NẰM TRONG CSDL.
--
-- VÌ SAO VẪN LÀM DÙ GẦN NHƯ CHẮC CHẮN LÀ NO-OP
--   Đường ống nhập liệu chưa phát hành: cơ sở dữ liệu phát triển trên máy này còn ở
--   V7, tức V9 (tệp tạo bảng import_issue) chưa từng chạy ở đâu ngoài container test
--   dùng-một-lần. Rất có thể lệnh dưới đây cập nhật đúng 0 dòng.
--   Nhưng thứ tự triển khai không nằm trong tay bản vá này. Chỉ cần MỘT môi trường
--   (máy của đồng nghiệp, một bản demo, một staging) đã chạy V9+V11 và đã kiểm một lô
--   là những dòng ấy còn nguyên. Và cảnh báo của một lô ĐÃ COMMITTED thì không bao giờ
--   được sinh lại — kiểm lại lô không cứu được chúng. Một lệnh UPDATE có điều kiện,
--   chạy trong vài micro-giây trên bảng rỗng, là cái giá rẻ hơn hẳn một lỗ rò không có
--   đường thu hồi.
--
-- VÌ SAO KHÔNG SOẠN LẠI CÂU THÔNG BÁO ĐẦY ĐỦ Ở ĐÂY
--   Làm vậy là chép văn bản cảnh báo sang một BẢN THỨ BA (bộ kiểm, tầng api, và SQL),
--   và ba bản thì chắc chắn lệch nhau. Câu thay thế dưới đây cố ý ngắn, nói thật rằng
--   nó đã bị soạn lại, và chỉ ra việc cần làm: kiểm lại lô để có cảnh báo đầy đủ.
-- =============================================================================

UPDATE import_issue
   SET message = 'Cảnh báo nghi trùng này đã được soạn lại vì lý do riêng tư:'
                 || ' bản cũ có chứa dữ liệu của một hồ sơ trong phả.'
                 || ' Hãy chạy lại bước kiểm (validate) trên lô để có thông báo đầy đủ.',
       -- Vế "đã có trong phả" (personId IS NOT NULL) rút về đúng khoá + điểm.
       -- Vế "dòng khác trong chính tệp này" (personId NULL) giữ nguyên: toàn bộ nội
       -- dung của nó là thứ chính người nhập vừa gõ, giấu đi chỉ làm họ không đối
       -- chiếu được.
       context = jsonb_set(
           context,
           '{nghiNgo}',
           COALESCE((
               SELECT jsonb_agg(
                          CASE WHEN ung ? 'personId'
                               THEN jsonb_strip_nulls(jsonb_build_object(
                                        'personId', ung -> 'personId',
                                        'diem',     ung -> 'diem',
                                        'nguon',    to_jsonb('TREE'::text),
                                        'tinHieu',  ung -> 'tinHieu'))
                               ELSE ung
                          END
                          ORDER BY ord)
                 FROM jsonb_array_elements(context -> 'nghiNgo') WITH ORDINALITY AS t(ung, ord)
           ), '[]'::jsonb))
 WHERE code = 'IMP_SUSPECT_DUPLICATE'
   AND jsonb_typeof(context -> 'nghiNgo') = 'array'
   -- Chỉ đụng vào dòng THẬT SỰ còn mang trường dữ liệu của người trong phả. Dòng do
   -- mã nguồn sau bản vá ghi ra không khớp điều kiện này, nên chạy lại tệp là vô hại.
   AND EXISTS (
       SELECT 1
         FROM jsonb_array_elements(context -> 'nghiNgo') AS ung
        WHERE ung ? 'personId'
          AND (ung ? 'ten' OR ung ? 'doi' OR ung ? 'giaiThich'));

-- -----------------------------------------------------------------------------
-- Kỵ húy: cắt tên và đời của bậc trên, chỉ giữ khoá.
-- tenHuy được LẤY LẠI từ khu vực chờ (import_person_row.taboo_name) — ô người nhập
-- tự gõ — thay vì giữ giá trị cũ đọc từ phả. Lô nào đã bị xoá khu vực chờ thì không
-- có nguồn an toàn để lấy lại, và khi ấy thà mất một tiện nghi còn hơn giữ một cái tên.
-- -----------------------------------------------------------------------------
UPDATE import_issue i
   SET message = 'Cảnh báo kỵ húy này đã được soạn lại vì lý do riêng tư:'
                 || ' bản cũ có chứa tên của một bậc trên trong phả.'
                 || ' Hãy chạy lại bước kiểm (validate) trên lô để có thông báo đầy đủ.',
       context = jsonb_strip_nulls(jsonb_build_object(
           'tenHuy',    to_jsonb(r.taboo_name),
           'bacTrenId', i.context -> 'bacTrenId'))
  FROM import_person_row r
 WHERE i.code = 'IMP_TABOO_COLLISION'
   AND r.batch_id = i.batch_id
   AND r.row_no = i.row_no
   AND (i.context ? 'bacTrenTen' OR i.context ? 'bacTrenDoi');

-- Dòng kỵ húy cũ không tra được về khu vực chờ (lô đã bị dọn): cắt trắng, giữ mỗi khoá.
UPDATE import_issue
   SET message = 'Cảnh báo kỵ húy này đã được soạn lại vì lý do riêng tư:'
                 || ' bản cũ có chứa tên của một bậc trên trong phả.'
                 || ' Hãy chạy lại bước kiểm (validate) trên lô để có thông báo đầy đủ.',
       context = jsonb_strip_nulls(jsonb_build_object('bacTrenId', context -> 'bacTrenId'))
 WHERE code = 'IMP_TABOO_COLLISION'
   AND (context ? 'bacTrenTen' OR context ? 'bacTrenDoi');
