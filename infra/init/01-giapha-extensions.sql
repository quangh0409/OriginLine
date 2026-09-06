-- ===========================================================================
-- 01-giapha-extensions.sql — chạy MỘT LẦN duy nhất, lúc initdb tạo cluster mới
-- (tức là chỉ khi volume postgres-data còn rỗng).
--
-- PHẠM VI CỦA FILE NÀY = phần bắt buộc ở tầng image. KHÔNG tạo bảng, KHÔNG tạo
-- graph, KHÔNG seed dữ liệu — toàn bộ schema thuộc về Flyway (workstream W1):
--   V1__extensions.sql  · V2__core.sql · ... · V7__graph.sql (create_graph)
-- Vì Flyway V1 cũng CREATE EXTENSION, mọi lệnh ở đây đều IF NOT EXISTS để hai
-- bên chạy chồng nhau mà không vỡ.
--
-- Image apache/age đã tự chạy 00-create-extension-age.sql trước file này và đã
-- khởi động Postgres với shared_preload_libraries=age.
-- ===========================================================================

-- Graph engine: cây phả đồ (giapha_graph) — Cypher chạy trong Postgres.
CREATE EXTENSION IF NOT EXISTS age;

-- Phân cấp chi/ngành/cành/nhánh + phạm vi RBAC theo branch.
CREATE EXTENSION IF NOT EXISTS ltree;

-- Tìm kiếm tên tiếng Việt không dấu (FR-4.4).
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Gợi ý/so khớp mờ cho ô tìm kiếm và dò trùng khi nhập liệu hàng loạt.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- gen_random_uuid() và hàm băm cho khoá chống trùng của consumer.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- BẪY AGE: mọi phiên kết nối phải có ag_catalog trong search_path, nếu không
-- các hàm cypher()/agtype không nhìn thấy được và lỗi báo rất khó hiểu
-- ("function cypher(...) does not exist" hoặc "type agtype does not exist").
--
-- Đặt mặc định ở cấp DATABASE để psql, DBeaver, pgAdmin... đỡ phải nhớ.
-- Backend vẫn phải tự đặt qua spring.datasource.hikari.connection-init-sql,
-- vì Hikari có thể tái dùng connection trước khi cấu hình DB kịp áp dụng và
-- vì cấu hình này không theo được nếu đổi sang datasource khác.
-- ---------------------------------------------------------------------------
DO $$
BEGIN
  EXECUTE format(
    'ALTER DATABASE %I SET search_path = ag_catalog, "$user", public',
    current_database()
  );
END
$$;

-- Áp dụng luôn cho phiên hiện tại (ALTER DATABASE chỉ có hiệu lực ở phiên mới).
LOAD 'age';
SET search_path = ag_catalog, "$user", public;
