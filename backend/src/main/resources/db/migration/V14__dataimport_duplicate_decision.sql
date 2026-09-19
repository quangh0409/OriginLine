-- =============================================================================
-- V14 — QUYẾT ĐỊNH CHO CẶP NGHI TRÙNG
--
-- LỖ HỔNG ĐANG BỊT:
--   Bộ dò trùng sinh cảnh báo IMP_SUSPECT_DUPLICATE, cổng duyệt chặn khi còn cặp
--   chưa quyết, nhưng KHÔNG CÓ CHỖ NÀO GHI QUYẾT ĐỊNH XUỐNG. Hệ quả dây chuyền:
--   mọi cặp vĩnh viễn PENDING ⇒ undecidedDuplicateCount == suspectDuplicateCount
--   ⇒ ImportCommitGate chặn mãi. Đường ống vì thế chỉ chạy được trên tệp KHÔNG
--   có ai nghi trùng — mà một cuốn gia phả thật, chép lại từ nhiều cuốn sổ, gần
--   như luôn có. Bộ dò trùng tồn tại chính vì điều đó.
--
-- BA QUYẾT ĐỊNH, VÀ CHỈ BA:
--   MERGED   — đây là cùng một người
--   DISTINCT — hai người khác nhau, cả hai cùng vào phả
--   DEFERRED — chưa chắc, để lại sau
--
-- DEFERRED VẪN CHẶN CỔNG DUYỆT, VÀ ĐÓ LÀ TOÀN BỘ GIÁ TRỊ CỦA NÓ.
--   Nếu "hoãn" mở khoá nút duyệt thì nó thành nút "cho tôi qua", và cả cơ chế
--   dò trùng thành trang trí: ai cũng bấm hoãn ba mươi cặp rồi ghi. Nó tồn tại
--   để người đối chiếu KHÔNG phải chọn bừa giữa hai đáp án khi chưa chắc — ép
--   chọn nhị phân lúc chưa chắc thì cái bấm đại trở thành sự thật trong phả.
-- =============================================================================

-- =============================================================================
-- 14.1 import_duplicate_pair — MỘT CẶP, VÀ QUYẾT ĐỊNH CỦA NGƯỜI VỀ NÓ
-- =============================================================================
-- VÌ SAO LÀ MỘT BẢNG RIÊNG CHỨ KHÔNG PHẢI MỘT CỘT TRÊN import_issue:
--   Một dòng cảnh báo có thể mang NHIỀU ứng viên (dòng 12 nghi trùng với ba hồ
--   sơ), và người đối chiếu phải quyết TỪNG CẶP. Treo quyết định lên import_issue
--   thì ba câu trả lời khác nhau phải chen vào một cột. Nặng hơn: import_issue bị
--   XOÁ SẠCH RỒI GHI LẠI mỗi lần kiểm (xem V9 §9.6) — quyết định gắn vào đó sẽ
--   bốc hơi ở lần kiểm lại kế tiếp, đúng thứ lỗi im lặng nguy hiểm nhất.
--
-- VÂN TAY CỦA TẬP CẶP — ÁP DỤNG TIỀN LỆ ĐÚNG CỦA V13, NHƯNG Ở ĐỘ PHÂN GIẢI KHÁC:
--   V13 gắn xác nhận cảnh báo vào vân tay của CẢ TẬP cảnh báo: tập đổi ⇒ xác nhận
--   cũ hết hiệu lực. Nguyên tắc ấy đúng ở đây, nhưng độ phân giải "cả tập" thì SAI:
--   bắt quyết lại 40 cặp vì lần kiểm sau tìm thêm cặp thứ 41 là cách huấn luyện
--   người ta bấm bừa — đúng cái hại mà V13 muốn tránh.
--
--   Vì vậy vân tay ở đây là `pair_key`, vân tay của TỪNG CẶP, và nó mang đúng
--   DANH TÍNH của cặp: (mã dòng trong tệp, bên kia là ai). Kiểm lại thì:
--     · cặp có pair_key y nguyên  -> quyết định cũ GIỮ NGUYÊN (ON CONFLICT DO UPDATE
--       chỉ làm mới score/signals, không đụng status/decided_*);
--     · cặp mới                   -> chèn mới với status PENDING, và lô lập tức
--       bị chặn lại cho tới khi có người quyết;
--     · cặp biến mất              -> xoá, vì câu hỏi không còn nữa.
--
--   `score` CỐ Ý KHÔNG nằm trong pair_key. Quyết định là một khẳng định về DANH
--   TÍNH ("đây là cùng một người"), và danh tính không đổi khi điểm nhích từ 86
--   xuống 84 vì ai đó vừa bổ sung năm sinh cho hồ sơ bên kia. Nhét điểm vào vân
--   tay thì mọi lần kiểm lại đều huỷ một nắm quyết định mà không giải thích được.
-- =============================================================================
CREATE TABLE import_duplicate_pair (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id           UUID         NOT NULL REFERENCES import_batch (id) ON DELETE CASCADE,

    -- Danh tính của cặp. Dựng ở Java: "<ma dong>::TREE::<person_id>" hoặc
    -- "<ma dong>::FILE::<ma dong kia>". Là chuỗi đọc được chứ không phải mã băm,
    -- vì thứ duy nhất người phải làm với nó lúc 2 giờ sáng là ĐỌC nó trong psql.
    pair_key           VARCHAR(200) NOT NULL,

    -- Bên "đang nhập": một dòng của tệp. row_no để hiển thị, incoming_code là khoá
    -- thật — số dòng đổi khi người ta chèn một dòng vào giữa tệp, mã thì không.
    row_no             INT          NOT NULL,
    incoming_code      VARCHAR(64)  NOT NULL,

    -- Bên bị nghi: HOẶC một nhân khẩu đã có trong phả, HOẶC một dòng khác của
    -- chính tệp này. Hai ca khác nhau HOÀN TOÀN về nghĩa của chữ "gộp" — xem
    -- DuplicateMergePlan — nên chúng được phân biệt tường minh chứ không suy ra
    -- từ việc cột nào đang NULL.
    counterpart_kind   VARCHAR(4)   NOT NULL,
    existing_person_id UUID         REFERENCES person (id),
    existing_code      VARCHAR(64),

    score              INT          NOT NULL,
    -- CHỈ NHÃN TÍN HIỆU ("trùng ngày giỗ", "cùng chi"), KHÔNG BAO GIỜ GIÁ TRỊ TRƯỜNG.
    -- Đường nhập liệu không phát một trường nhân khẩu nào của người trong phả; bốn bề
    -- mặt cùng loại đã bị bịt trong dự án này và cột này không được mở bề mặt thứ năm.
    signals            TEXT,

    status             VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    -- AI quyết, LÚC NÀO. Đây là thao tác có hệ quả VĨNH VIỄN lên phả: "gộp" làm
    -- một dòng biến mất hoặc làm một hồ sơ đã có bị viết đè. Một dấu thời gian vô
    -- danh là câu vô dụng đúng vào lúc cần nó nhất.
    decided_by         UUID         REFERENCES app_user (id),
    decided_at         TIMESTAMPTZ,
    decision_note      TEXT,

    first_seen_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_dup_pair_kind CHECK (counterpart_kind IN ('TREE','FILE')),
    CONSTRAINT ck_dup_pair_status CHECK (status IN ('PENDING','MERGED','DISTINCT','DEFERRED')),
    -- Đúng một bên được điền, và bên nào thì do counterpart_kind nói. Không có ca
    -- "cả hai NULL" (cặp không có bên kia) lẫn "cả hai điền" (cặp có hai bên kia).
    CONSTRAINT ck_dup_pair_side CHECK (
        (counterpart_kind = 'TREE' AND existing_person_id IS NOT NULL AND existing_code IS NULL)
     OR (counterpart_kind = 'FILE' AND existing_code IS NOT NULL AND existing_person_id IS NULL)),
    -- PENDING thì chưa ai quyết; mọi trạng thái khác BẮT BUỘC có chủ và có mốc.
    -- DEFERRED nằm ở vế thứ hai là cố ý: "hoãn" là một quyết định được ghi lại,
    -- nó chỉ không phải một quyết định MỞ KHOÁ.
    CONSTRAINT ck_dup_pair_decided CHECK (
        (status = 'PENDING' AND decided_at IS NULL AND decided_by IS NULL)
     OR (status <> 'PENDING' AND decided_at IS NOT NULL AND decided_by IS NOT NULL)),
    CONSTRAINT ck_dup_pair_score CHECK (score BETWEEN 0 AND 100),
    -- Một dòng không bao giờ nghi trùng với chính nó.
    CONSTRAINT ck_dup_pair_not_self CHECK (existing_code IS NULL OR existing_code <> incoming_code)
);

-- Vân tay: một cặp xuất hiện đúng một lần trong một lô, và đây là đích của
-- ON CONFLICT khi kiểm lại. Không có ràng buộc này thì mỗi lần kiểm lại sinh thêm
-- một bản sao của cùng một câu hỏi, và cổng duyệt đếm ra một con số tăng dần.
CREATE UNIQUE INDEX ux_import_duplicate_pair ON import_duplicate_pair (batch_id, pair_key);

-- Câu hỏi cổng duyệt hỏi mỗi lần bấm ghi: "còn cặp nào CHƯA QUYẾT không".
-- DEFERRED nằm trong chỉ mục cùng PENDING vì nó vẫn chặn — xem đầu tệp.
CREATE INDEX ix_import_duplicate_pair_undecided ON import_duplicate_pair (batch_id)
    WHERE status IN ('PENDING','DEFERRED');
CREATE INDEX ix_import_duplicate_pair_batch ON import_duplicate_pair (batch_id, row_no);
CREATE INDEX ix_import_duplicate_pair_person ON import_duplicate_pair (existing_person_id)
    WHERE existing_person_id IS NOT NULL;

COMMENT ON TABLE import_duplicate_pair IS
    'Mot cap nghi trung va quyet dinh cua NGUOI ve no. May chi nghi ngo; khong nguong diem nao tu gop. DEFERRED van chan cong duyet.';
COMMENT ON COLUMN import_duplicate_pair.pair_key IS
    'Van tay DANH TINH cua cap: (ma dong, ben kia la ai). Kiem lai sinh cap MOI thi cap cu giu nguyen quyet dinh; score CO Y khong nam trong day.';
COMMENT ON COLUMN import_duplicate_pair.signals IS
    'CHI nhan tin hieu, KHONG BAO GIO gia tri truong. Duong nhap lieu khong phat mot truong nhan khau nao cua nguoi trong pha.';
COMMENT ON COLUMN import_duplicate_pair.status IS
    'PENDING | MERGED | DISTINCT | DEFERRED. Chua quyet = PENDING hoac DEFERRED, va ca hai deu chan cong duyet.';
