-- =============================================================================
-- V11 — NỬA SAU CỦA ĐƯỜNG ỐNG NHẬP LIỆU: GHI VÀO PHẢ, VÀ GỠ LẠI ĐƯỢC
-- Nguồn: plan/02-nhap-lieu/index.html — §8 (N6 ghi vào phả, phương án A),
--        §13.2 mục 4 (rút lại cả lô, có điều kiện).
--
-- V9 dựng KHU VỰC CHỜ và ghi rõ: "không một bảng nào được ghi vào person /
-- relationship / đồ thị AGE". Ranh giới ghi nằm ở đúng một bước và bước đó là đây.
-- Tệp này KHÔNG mở thêm một đường ghi nào vào ba chỗ ấy — nó chỉ thêm **sổ cái**
-- ghi lại lô nào đã viết ra những gì, để câu hỏi "gỡ lại được không, và gỡ những ai"
-- trả lời bằng một truy vấn có chỉ mục thay vì một buổi chiều đoán mò.
-- =============================================================================

-- =============================================================================
-- 11.1 import_batch — bốn cột cho việc ghi và việc gỡ
-- =============================================================================
ALTER TABLE import_batch
    ADD COLUMN committed_by     UUID        REFERENCES app_user (id),
    ADD COLUMN rolled_back_at   TIMESTAMPTZ,
    ADD COLUMN rolled_back_by   UUID        REFERENCES app_user (id),
    ADD COLUMN rollback_reason  TEXT;

-- TRẠNG THÁI KHÔNG ĐỔI SAU KHI GỠ, VÀ ĐÂY LÀ QUYẾT ĐỊNH CÓ CHỦ Ý.
--   Lô vẫn mang status = 'COMMITTED' vì nó ĐÃ từng được ghi — đó là sự thật lịch sử, và
--   `committed_at` là mốc mà mọi phép kiểm "ai đã động vào sau khi ghi" dựa vào. Đổi trạng thái
--   về VALIDATED sẽ xoá mất sự thật ấy và làm lô trông như chưa từng vào phả, trong khi audit_log
--   vẫn đầy dấu vết của nó. "Đã gỡ" là một cột riêng, không phải một trạng thái.
ALTER TABLE import_batch
    ADD CONSTRAINT ck_import_batch_rollback CHECK (
        rolled_back_at IS NULL OR (status = 'COMMITTED' AND committed_at IS NOT NULL));

COMMENT ON COLUMN import_batch.rolled_back_at IS
    'Lo da duoc rut lai. Status VAN la COMMITTED: no da tung vao pha that, va committed_at la moc so sanh cua moi phep kiem "ai da dong vao sau do".';

-- Chặn bấm-hai-lần vẫn giữ nguyên ý đồ của V9, nhưng phải nhả ra sau khi gỡ: một lô đã rút lại
-- thì đúng cái tệp ấy PHẢI tải lên lại được — nếu không, người vừa gỡ vì nhập nhầm chi sẽ không
-- nhập lại nổi cho chi đúng mà không đi sửa tay một ô trong tệp.
DROP INDEX ux_import_batch_committed_file;
CREATE UNIQUE INDEX ux_import_batch_committed_file
    ON import_batch (branch_id, file_sha256)
    WHERE status = 'COMMITTED' AND rolled_back_at IS NULL;

-- =============================================================================
-- 11.2 import_commit_entry — SỔ CÁI CỦA MỘT LẦN GHI
-- =============================================================================
-- VÌ SAO PHẢI CÓ BẢNG NÀY, TRONG KHI ĐÃ CÓ person_external_ref.first_batch_id:
--   Cột ấy trả lời "lô X sinh ra những NGƯỜI nào". Nó không trả lời được "lô X nối những CẠNH
--   nào" — mà cạnh mới là thứ nguy hiểm khi gỡ. Một lô hay nối con vào một người cha ĐÃ CÓ SẴN
--   trong phả từ trước; cạnh ấy do lô sinh ra nên phải gỡ, còn người cha thì tuyệt đối không được
--   đụng tới. Không ghi lại thì lúc gỡ chỉ còn cách suy đoán từ thời điểm tạo — và suy đoán sai ở
--   đây nghĩa là cắt nhầm một cành của dòng họ.
--
--   Cột person_version_at_commit là điều kiện THẬT của việc gỡ (xem §13.2 và cách N7 canh hoàn
--   tác gộp): gỡ một lô mà ai đó đã kịp bổ sung ảnh, sửa ngày giỗ hay thêm một lớp tên thì sẽ âm
--   thầm nuốt mất công của họ. Một nút gỡ làm mất dữ liệu còn tệ hơn không có nút gỡ, vì nó tạo
--   cảm giác an toàn giả.
CREATE TABLE import_commit_entry (
    id                       UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id                 UUID        NOT NULL REFERENCES import_batch (id) ON DELETE CASCADE,
    entry_kind               VARCHAR(8)  NOT NULL,
    row_no                   INT,
    external_code            VARCHAR(64),

    -- entry_kind = 'PERSON'
    person_id                UUID        REFERENCES person (id),
    -- TRUE = lô này TẠO RA người ấy; FALSE = người đã có từ trước, lô chỉ cập nhật hồ sơ.
    -- Gỡ lô chỉ được phép xoá mềm nhóm TRUE. Nhóm FALSE tồn tại trước lô và sống tiếp sau khi gỡ.
    person_created           BOOLEAN     NOT NULL DEFAULT FALSE,
    person_version_at_commit BIGINT,

    -- entry_kind = 'EDGE'
    edge_from                UUID        REFERENCES person (id),
    edge_to                  UUID        REFERENCES person (id),
    edge_type                VARCHAR(20),
    -- CO Y KHONG dat khoa ngoai sang relationship — doc truoc khi "sua cho day du":
    --   Dong relationship duoc ghi qua ORM va chi thuc su cham dia luc flush, con so cai nay ghi
    --   bang JDBC theo lo o cuoi cung transaction. Mot khoa ngoai o day bien thu tu flush cua ORM
    --   thanh mot rang buoc cua luoc do: lo 400 nguoi se chet voi mot loi khoa ngoai kho hieu, o
    --   dung buoc cuoi, sau khi da chay xong phan viec nang nhat. Cot nay la DAU VET de truy lai;
    --   viec go lo khong dua vao no ma dua vao (edge_from, edge_to, edge_type).
    relationship_id          UUID,

    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_commit_entry_kind CHECK (entry_kind IN ('PERSON','EDGE')),
    CONSTRAINT ck_commit_entry_person CHECK (
        entry_kind <> 'PERSON' OR person_id IS NOT NULL),
    CONSTRAINT ck_commit_entry_edge CHECK (
        entry_kind <> 'EDGE'
        OR (edge_from IS NOT NULL AND edge_to IS NOT NULL AND edge_type IS NOT NULL)),
    CONSTRAINT ck_commit_entry_edge_type CHECK (
        edge_type IS NULL OR edge_type IN ('PARENT_BIO','PARENT_ADOPT','SPOUSE','HEIR'))
);

CREATE INDEX ix_commit_entry_batch  ON import_commit_entry (batch_id, entry_kind);
CREATE INDEX ix_commit_entry_person ON import_commit_entry (person_id) WHERE person_id IS NOT NULL;
-- Một người chỉ được ghi đúng một dòng sổ cái trong một lô. Hai dòng nghĩa là lô đã xử lý cùng
-- một người hai lần, và khi gỡ ta sẽ xoá mềm hai lần rồi nhận một ngoại lệ giữa chừng.
CREATE UNIQUE INDEX ux_commit_entry_person
    ON import_commit_entry (batch_id, person_id)
    WHERE entry_kind = 'PERSON';

COMMENT ON TABLE import_commit_entry IS
    'So cai cua mot lan ghi: lo nao da tao ai va noi canh nao. Khong co bang nay thi "go lo" phai doan, va doan sai la cat nham mot canh cua dong ho.';
COMMENT ON COLUMN import_commit_entry.person_version_at_commit IS
    'person.version ngay sau khi ghi. Lech nghia la co nguoi da sua ho so ke tu do -> TU CHOI go lo, vi khoi phuc se nuot mat sua doi cua ho.';

-- =============================================================================
-- 11.3 Ghi chú: phép kiểm "đã có dữ liệu phụ thuộc mới" KHÔNG cần chỉ mục mới
-- =============================================================================
-- Gỡ lô phải trả lời được, rẻ và chắc chắn, ba câu: có ai treo thêm con vào người của lô chưa,
-- có ai nhận hồ sơ chưa, có sự kiện giỗ nào đã lên lịch chưa. Cả ba đã có đường đi sẵn:
--   ix_relationship_from / ix_relationship_to  (V2)  — cạnh mới chạm người của lô
--   ux_app_user_person                          (V5)  — tài khoản đã nhận hồ sơ
--   ix_change_request_person                    (V5)  — yêu cầu đính chính đang treo
--   ix_event_person                             (V4)  — giỗ đã lên lịch
-- Thêm chỉ mục ở đây chỉ tạo ra bản sao của những chỉ mục ấy. Ghi ra để lần sau không ai
-- "bổ sung cho đủ bộ".
