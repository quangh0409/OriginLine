-- =============================================================================
-- V1__extensions.sql — Extension & thiết lập nền
-- Hệ Thống Quản Lý Gia Phả & Cổng Thông Tin Dòng Họ (vn.giapha)
-- Nguồn: TDD v1.0 §5, §6 · BA v2.0 §9
--
-- PostgreSQL 16 + Apache AGE là CSDL DUY NHẤT của hệ thống:
--   age      — đồ thị phả hệ (graph giapha_graph), truy vấn bằng Cypher trong Postgres
--   ltree    — phân cấp chi/ngành/cành/nhánh + phạm vi RBAC
--   unaccent — tra cứu tên tiếng Việt không dấu (kiều bào gõ không dấu)
--   pg_trgm  — tìm gần đúng / typo trên tên
--   pgcrypto — gen_random_uuid(), băm/mã hoá dữ liệu Tầng 3
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS age;
CREATE EXTENSION IF NOT EXISTS ltree;
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- AGE cần nạp thư viện cho mỗi session và cần ag_catalog trong search_path.
-- Mọi migration / đoạn code có chạy Cypher đều phải lặp lại 2 dòng dưới đây.
LOAD 'age';
SET search_path = ag_catalog, "$user", public;

-- Gắn ag_catalog vào search_path mặc định của database để session JDBC của backend
-- không phải tự set. CẢNH BÁO THỨ TỰ: public phải đứng TRƯỚC ag_catalog, nếu không
-- mọi CREATE TABLE không schema-qualified sẽ rơi vào ag_catalog.
-- Nếu user migration không đủ quyền ALTER DATABASE thì bỏ qua — backend vẫn tự
-- SET search_path trong AgeTreeGraphAdapter.
DO $do$
BEGIN
    EXECUTE format(
        'ALTER DATABASE %I SET search_path = "$user", public, ag_catalog',
        current_database()
    );
EXCEPTION WHEN insufficient_privilege THEN
    RAISE NOTICE 'Bo qua ALTER DATABASE SET search_path (thieu quyen). '
                 'Backend phai tu SET search_path = "$user", public, ag_catalog.';
END
$do$;

-- Trả session hiện tại về public để các migration V2..V6 tạo bảng đúng schema.
SET search_path = public, ag_catalog;

-- Cấu hình FTS/unaccent nằm ở V6__search.sql (sau khi có bảng person_name).
-- Tạo graph giapha_graph nằm ở V7__graph.sql.
